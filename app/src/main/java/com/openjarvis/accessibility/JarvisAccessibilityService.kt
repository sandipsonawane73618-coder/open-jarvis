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
        
        // Start from the XML-declared service info. Replacing it with a fresh
        // AccessibilityServiceInfo can discard manifest capabilities such as
        // CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT, leaving the service connected
        // but unable to read the target app's accessibility tree.
        val info = serviceInfo
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
        info.notificationTimeout = 100
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
        val numberedTarget = Regex("^#?(\\d{1,2})$").matchEntire(text.trim())
        if (numberedTarget != null) {
            val index = numberedTarget.groupValues[1].toIntOrNull() ?: return false
            return tapVisibleInteractiveElement(index)
        }
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
                val matches = node.isVisibleToUser && matchesNodeTarget(node, needle)
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
    fun typeText(text: String, targetHint: String? = null): Boolean {
        if (text.isEmpty()) return false
        val roots = interactionRoots()
        var target: AccessibilityNodeInfo? = null
        for (index in roots.indices) {
            val found = findEditableInRoot(roots[index], targetHint)
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

    private fun findEditableInRoot(root: AccessibilityNodeInfo, targetHint: String? = null): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val hasTarget = !targetHint.isNullOrBlank()
        try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused?.isEditable == true && focused.isVisibleToUser && focused.isEnabled &&
                (!hasTarget || matchesNodeTarget(focused, targetHint!!))) {
                return focused
            }
            focused?.recycle()
            queue.add(AccessibilityNodeInfo.obtain(root))
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.isVisibleToUser && node.isEnabled && node.isEditable &&
                    (!hasTarget || matchesNodeTarget(node, targetHint!!))) {
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

    /** Submit the currently focused search editor using its IME action. */
    fun submitImeSearch(): Boolean {
        val roots = interactionRoots()
        for (index in roots.indices) {
            val editor = findEditableInRoot(roots[index])
            if (editor != null) {
                val submitted = android.os.Build.VERSION.SDK_INT >= 30 &&
                    editor.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER)
                editor.recycle()
                for (remaining in index + 1 until roots.size) roots[remaining].recycle()
                return submitted
            }
        }
        return false
    }

    /**
     * Match UI controls by visible text, accessibility description, hint, tooltip,
     * or Android resource id. Icon-only controls (for example WhatsApp Search)
     * often have no visible text but do expose a content description or view id.
     */
    private fun matchesNodeTarget(node: AccessibilityNodeInfo, target: String): Boolean {
        val ignored = setOf("icon", "button", "field", "box", "control", "menu", "the", "a")
        val query = target.lowercase().replace(Regex("[^a-z0-9]+"), " ")
            .split(Regex("\\s+")).filter { it.isNotBlank() && it !in ignored }
        if (query.isEmpty()) return false
        val candidates = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.hintText?.toString(),
            node.tooltipText?.toString(),
            node.viewIdResourceName
        ).map { it.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim() }
            .filter { it.isNotBlank() }
        return candidates.any { candidate ->
            val compact = candidate.replace(" ", "")
            val queryCompact = query.joinToString("")
            compact.contains(queryCompact) ||
                query.any { token -> candidate.split(Regex("\\s+")).any { it.contains(token) } ||
                    compact.contains(token) }
        }
    }

    /** Read visible text from the target app and other interactive windows.
     * The floating assistant can own rootInActiveWindow, so inspect every window.
     */
    fun extractVisibleText(maxCharacters: Int = 12000): String {
        val roots = interactionRoots()
        val output = linkedSetOf<String>()
        for (root in roots) {
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                queue.add(AccessibilityNodeInfo.obtain(root))
                try {
                    while (queue.isNotEmpty() && output.sumOf { it.length } < maxCharacters) {
                        val node = queue.removeFirst()
                        if (node.isVisibleToUser) {
                            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(output::add)
                            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(output::add)
                        }
                        for (index in 0 until node.childCount) {
                            node.getChild(index)?.let(queue::add)
                        }
                        node.recycle()
                    }
                } finally {
                    while (queue.isNotEmpty()) queue.removeFirst().recycle()
                    root.recycle()
                }
                if (output.sumOf { it.length } >= maxCharacters) {
                    for (remaining in roots.dropWhile { it !== root }.drop(1)) remaining.recycle()
                    break
                }
        }
        return output.joinToString(" ").take(maxCharacters)
    }

    /**
     * Compact live UI map for the planner. Includes icon descriptions and Android
     * resource ids so the model does not assume every control has a text label.
     */
    fun describeVisibleInteractiveElements(maxItems: Int = 60): String {
        val roots = interactionRoots()
        val lines = mutableListOf<String>()
        for (root in roots) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(AccessibilityNodeInfo.obtain(root))
            try {
                while (queue.isNotEmpty() && lines.size < maxItems) {
                    val node = queue.removeFirst()
                    if (node.isVisibleToUser && (node.isClickable || node.isEditable || node.isLongClickable)) {
                        val label = listOfNotNull(
                            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { "text=$it" },
                            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { "desc=$it" },
                            node.hintText?.toString()?.takeIf { it.isNotBlank() }?.let { "hint=$it" },
                            node.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { "id=$it" },
                            node.className?.toString()?.let { "class=$it" }
                        ).joinToString(" ")
                        if (label.isNotBlank()) {
                            val kind = when {
                                node.isEditable -> "editable"
                                node.isClickable -> "clickable"
                                else -> "long-clickable"
                            }
                            val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
                            lines.add("[#${lines.size + 1} $kind] $label bounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
                        }
                    }
                    for (index in 0 until node.childCount) {
                        node.getChild(index)?.let(queue::add)
                    }
                    node.recycle()
                }
            } finally {
                root.recycle()
                while (queue.isNotEmpty()) queue.removeFirst().recycle()
            }
            if (lines.size >= maxItems) break
        }
        return lines.joinToString("\n")
    }

    /** Android-use-style indexed lookup over the same visible control order shown to the planner. */
    private fun tapVisibleInteractiveElement(targetIndex: Int): Boolean {
        if (targetIndex !in 1..60) return false
        val roots = interactionRoots()
        var currentIndex = 0
        for (rootIndex in roots.indices) {
            val root = roots[rootIndex]
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(AccessibilityNodeInfo.obtain(root))
            try {
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    if (node.isVisibleToUser && (node.isClickable || node.isEditable || node.isLongClickable)) {
                        currentIndex++
                        if (currentIndex == targetIndex) {
                            var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
                            for (depth in 0..8) {
                                val current = candidate ?: break
                                if (current.isVisibleToUser && current.isEnabled && current.isClickable) {
                                    val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    current.recycle()
                                    candidate = null
                                    while (queue.isNotEmpty()) queue.removeFirst().recycle()
                                    for (remaining in rootIndex + 1 until roots.size) roots[remaining].recycle()
                                    return clicked
                                }
                                val parent = current.parent
                                current.recycle()
                                candidate = parent
                            }
                            candidate?.recycle()
                            while (queue.isNotEmpty()) queue.removeFirst().recycle()
                            for (remaining in rootIndex + 1 until roots.size) roots[remaining].recycle()
                            return false
                        }
                    }
                    for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
                    node.recycle()
                }
            } finally {
                root.recycle()
                while (queue.isNotEmpty()) queue.removeFirst().recycle()
            }
        }
        return false
    }

    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun pressNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

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

    /** Returns package names currently represented by visible accessibility windows. */
    fun activePackageNames(): Set<String> {
        val packages = linkedSetOf<String>()

        runCatching {
            rootInActiveWindow?.let { root ->
                try {
                    root.packageName?.toString()?.takeIf { it.isNotBlank() }?.let(packages::add)
                } finally {
                    root.recycle()
                }
            }
        }

        runCatching {
            windows.forEach { window ->
                val windowRoot = window.root
                try {
                    windowRoot?.packageName?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(packages::add)
                } finally {
                    windowRoot?.recycle()
                }
            }
        }

        // Include only a fresh window-state event; stale foreground history must
        // never scope a new action to an app that is no longer visible.
        val eventAgeMs = System.currentTimeMillis() - lastWindowStateAtMs
        if (eventAgeMs in 0..5000) {
            lastWindowStatePackage?.takeIf { it.isNotBlank() }?.let(packages::add)
        }
        return packages
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