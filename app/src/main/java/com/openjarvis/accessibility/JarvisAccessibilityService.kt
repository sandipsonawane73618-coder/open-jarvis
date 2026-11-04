package com.openjarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or 
                   AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (!packageName.isNullOrBlank()) {
                lastWindowStatePackage = packageName
                lastWindowStateAtMs = System.currentTimeMillis()
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun openAppByPackage(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openAppByLabel(label: String): Boolean {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val apps = pm.queryIntentActivities(intent, 0)
        val normalizedLabel = label.lowercase()
        
        for (app in apps) {
            val appLabel = app.loadLabel(pm).toString().lowercase()
            if (appLabel.contains(normalizedLabel) || normalizedLabel.contains(appLabel)) {
                openAppByPackage(app.activityInfo.packageName)
                return true
            }
        }
        return false
    }

    /**
     * Search every interactive window, not only rootInActiveWindow. The floating
     * Jarvis overlay can own the active root while WhatsApp is visible underneath.
     */
    fun tapByText(text: String): Boolean {
        if (text.isBlank()) return false
        val roots = interactionRoots()
        for (index in roots.indices) {
            if (tapInRoot(roots[index], text.trim())) {
                for (remaining in index + 1 until roots.size) roots[remaining].recycle()
                return true
            }
        }
        return false
    }

    private fun interactionRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        runCatching {
            windows.forEach { window -> window.root?.let(roots::add) }
        }
        runCatching { rootInActiveWindow?.let(roots::add) }
        // Search the actual target app before the Jarvis overlay window.
        return roots.sortedBy { it.packageName?.toString() == packageName }
    }

    private fun tapInRoot(root: AccessibilityNodeInfo, needle: String): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root))
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val matches = node.isVisibleToUser && (
                    node.text?.toString()?.contains(needle, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.contains(needle, ignoreCase = true) == true
                )
                if (matches) {
                    var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
                    for (depth in 0..8) {
                        val current = candidate ?: break
                        if (current.isVisibleToUser && current.isEnabled && current.isClickable) {
                            val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            current.recycle()
                            candidate = null
                            if (clicked) {
                                node.recycle()
                                while (queue.isNotEmpty()) queue.removeFirst().recycle()
                                return true
                            }
                            break
                        }
                        val parent = current.parent
                        current.recycle()
                        candidate = parent
                    }
                    candidate?.recycle()
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::add)
                }
                node.recycle()
            }
            return false
        } finally {
            root.recycle()
            while (queue.isNotEmpty()) queue.removeFirst().recycle()
        }
    }

    /**
     * Resolve the editor across interactive windows too; the Jarvis overlay may
     * be active while the target app owns the editable search field.
     */
    fun typeText(text: String): Boolean {
        if (text.isEmpty()) return false
        val roots = interactionRoots()
        var target: AccessibilityNodeInfo? = null
        for (index in roots.indices) {
            val found = findEditableInRoot(roots[index])
            if (found != null) {
                target = found
                for (remaining in index + 1 until roots.size) roots[remaining].recycle()
                break
            }
        }
        val editor = target ?: return false
        if (!editor.isFocused) editor.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val changed = editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        editor.recycle()
        return changed
    }

    private fun findEditableInRoot(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused?.isEditable == true && focused.isVisibleToUser && focused.isEnabled) {
                return focused
            }
            focused?.recycle()
            queue.add(AccessibilityNodeInfo.obtain(root))
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.isVisibleToUser && node.isEnabled && node.isEditable) {
                    val result = AccessibilityNodeInfo.obtain(node)
                    node.recycle()
                    return result
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::add)
                }
                node.recycle()
            }
            return null
        } finally {
            root.recycle()
            while (queue.isNotEmpty()) queue.removeFirst().recycle()
        }
    }

    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /** Scroll the first target-app scrollable container, not the floating overlay. */
    fun scrollScreen(direction: String): Boolean {
        val roots = interactionRoots()
        val forward = direction.equals("down", ignoreCase = true) ||
            direction.equals("forward", ignoreCase = true)
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        for (index in roots.indices) {
            if (scrollInRoot(roots[index], action)) {
                for (remaining in index + 1 until roots.size) roots[remaining].recycle()
                return true
            }
        }
        return false
    }

    private fun scrollInRoot(root: AccessibilityNodeInfo, action: Int): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root))
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.isVisibleToUser && node.isEnabled && node.isScrollable) {
                    val success = node.performAction(action)
                    node.recycle()
                    while (queue.isNotEmpty()) queue.removeFirst().recycle()
                    return success
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::add)
                }
                node.recycle()
            }
            return false
        } finally {
            root.recycle()
            while (queue.isNotEmpty()) queue.removeFirst().recycle()
        }
    }

    fun isPackageActive(packageName: String): Boolean {
        // The floating JARVIS overlay can be the active root while the target app
        // is already visible underneath it. Inspect all interactive windows too.
        val root = rootInActiveWindow
        val rootPackage = root?.packageName?.toString()
        root?.recycle()
        if (rootPackage == packageName) return true

        val visibleInWindow = runCatching {
            windows.any { window ->
                val windowRoot = window.root
                try {
                    windowRoot?.packageName?.toString() == packageName
                } finally {
                    windowRoot?.recycle()
                }
            }
        }.getOrDefault(false)
        if (visibleInWindow) return true

        // Accessibility window-state events are the final fallback. Keep the
        // freshness bound so an old foreground event cannot verify a new launch.
        val eventPackage = lastWindowStatePackage
        val eventAgeMs = System.currentTimeMillis() - lastWindowStateAtMs
        return eventPackage == packageName && eventAgeMs in 0..5000
    }

    fun containsVisibleText(expected: String): Boolean {
        if (expected.isBlank()) return false
        val roots = interactionRoots()
        for (index in roots.indices) {
            if (containsTextInRoot(roots[index], expected)) {
                for (remaining in index + 1 until roots.size) roots[remaining].recycle()
                return true
            }
        }
        return false
    }

    private fun containsTextInRoot(root: AccessibilityNodeInfo, expected: String): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root))
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val matches = node.isVisibleToUser && (
                    node.text?.toString()?.contains(expected, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.contains(expected, ignoreCase = true) == true
                )
                if (matches) {
                    node.recycle()
                    while (queue.isNotEmpty()) queue.removeFirst().recycle()
                    return true
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(queue::add)
                }
                node.recycle()
            }
            return false
        } finally {
            root.recycle()
            while (queue.isNotEmpty()) queue.removeFirst().recycle()
        }
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val normalizedText = text.lowercase()
        
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            
            val nodeText = node.text?.toString()?.lowercase()
            val contentDesc = node.contentDescription?.toString()?.lowercase()
            
            if (nodeText?.contains(normalizedText) == true || contentDesc?.contains(normalizedText) == true) {
                if (node.isClickable && node.isVisibleToUser) {
                    return node
                }
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    queue.add(child)
                }
            }
            node.recycle()
        }
        
        return null
    }

    @Volatile
    private var lastWindowStatePackage: String? = null

    @Volatile
    private var lastWindowStateAtMs: Long = 0L

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set
    }
}