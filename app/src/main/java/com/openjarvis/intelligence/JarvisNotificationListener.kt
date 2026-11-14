package com.openjarvis.intelligence

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.openjarvis.graphify.GraphifyRepository
import com.openjarvis.accessibility.JarvisAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JarvisNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var graphifyRepo: GraphifyRepository? = null
    private val notificationsLock = Any()
    private val notifications = mutableListOf<JarvisNotification>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        // Seed the in-memory view with notifications already active when access is enabled.
        runCatching { activeNotifications?.forEach(::upsertNotification) }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: JarvisNotificationListener? = null
            private set

        private val PRIVACY_PROTECTED_APPS = setOf(
            // India: bank, UPI, wallet and payment apps.
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "com.sbi.lotusintouch",
            "com.sbi.upi",
            "com.csam.icici.bank.imobile",
            "com.icicibank.pockets",
            "com.snapwork.hdfc",
            "com.enstage.wibmo.hdfc",
            "com.axis.mobile",
            "com.upi.axispay",
            "com.bankofbaroda.upi",
            "com.fss.pnbpsp",
            "com.fss.unbipsp",
            "com.idfcfirstbank.optimus",
            "com.fss.idfcpsp",
            "com.YesBank",
            "in.irisbyyes.app",
            "com.lcode.ucoupi",
            "com.euronet.iobupi",
            "com.lcode.csbupi",
            "com.fss.jnkpsp",
            "com.mycompany.kvb",
            "com.mobikwik_new",
            "com.freecharge.android",
            "com.dreamplug.androidapp",
            "com.hdfcbank.payzapp",
            "com.samsung.android.spay",
            "com.samsung.android.spaymini",
            "in.amazon.mShop.android.shopping",

            // Legacy package aliases retained for backward-compatible privacy protection.
            "com.chase",
            "com.usbank",
            "com.citi",
            "com.barclays",
            "com.hsbc",
            "com.db",
            "com.americanexpress",
            "com.discover",
            "com.paypal",
            "com.squareup",
            "com.stripe",

            // International banking and payment apps.
            "com.bankofamerica.cashpromobile",
            "com.chase.sig.android",
            "com.wf.wellsfargomobile",
            "com.usbank.mobilebanking",
            "com.citi.citimobile",
            "com.barclaycardus",
            "com.hsbc.hsbcukmobilebanking",
            "com.dbs.sg.dbsmbanking",
            "com.americanexpress.android.acctsvcs.us",
            "com.discoverfinancial.mobile",
            "com.paypal.android.p2pmobile",
            "com.squareup.cash",
            "com.razorpay"
        ).map { it.lowercase() }.toSet()

        // Package-name heuristics are intentionally conservative: false positives
        // are preferable to collecting notification text from a financial app.
        private val FINANCIAL_PACKAGE_MARKERS = setOf(
            "bank", "banking", "upi", "paisa", "wallet", "finance", "payment",
            "paytm", "phonepe", "payzapp", "mobikwik", "freecharge", "razorpay",
            "paypal", "cashapp", "bhim", "npci", "cred", "credit", "debit",
            "gpay", "card", "kotak", "idbi", "indusind", "federal", "rbl",
            "pnb", "sbi", "hdfc", "icici", "axis", "yesbank", "bankofbaroda",
            "bankofindia", "canara", "unionbank", "indianbank", "bandhan",
            "dbs", "hsbc", "barclays", "wellsfargo", "chase", "citi",
            "americanexpress", "discover"
        )

        private val messagingApps = listOf(
            "com.whatsapp",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.instagram.android",
            "com.facebook.orca",
            "org.telegram.messenger",
            "com.slack",
            "com.discord"
        )

        fun shouldProcessNotification(packageName: String): Boolean {
            val normalized = packageName.trim().lowercase()
            if (normalized.isEmpty()) return false
            return normalized !in PRIVACY_PROTECTED_APPS &&
                FINANCIAL_PACKAGE_MARKERS.none { marker -> normalized.contains(marker) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!shouldProcessNotification(sbn.packageName)) {
            return
        }

        upsertNotification(sbn)
    }

    private fun upsertNotification(sbn: StatusBarNotification) {
        val parsed = parseNotification(sbn)
        synchronized(notificationsLock) {
            // Notification IDs are scoped to a package; replace an updated active item.
            notifications.removeAll { it.id == parsed.id && it.packageName == parsed.packageName }
            notifications.add(parsed)
            val newestFirst = notifications.sortedByDescending { it.timestamp }
                .take(NotificationBufferPolicy.MAX_ITEMS)
            notifications.clear()
            notifications.addAll(newestFirst)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Notification IDs are only unique within an app; include packageName to avoid
        // deleting another app's notification when IDs happen to collide.
        synchronized(notificationsLock) {
            notifications.removeAll { it.id == sbn.id && it.packageName == sbn.packageName }
        }
    }

    private fun parseNotification(sbn: StatusBarNotification): JarvisNotification {
        val extras = sbn.notification.extras
        val title = NotificationPrivacyPolicy.redact(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "")
        val body = NotificationPrivacyPolicy.redact(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "")

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val isMessaging = sbn.packageName in messagingApps

        val sender = if (isMessaging) {
            NotificationPrivacyPolicy.redact(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
        } else null

        return JarvisNotification(
            id = sbn.id,
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = title,
            body = body,
            timestamp = sbn.postTime,
            isMessaging = isMessaging,
            sender = sender
        )
    }

    /**
     * Legacy entry point intentionally refuses to send. Replies must be routed through
     * AgentCore's exact-recipient/message preview and explicit spoken "haan" approval.
     */
    @Deprecated("Use the AgentCore send-safety preview and exact-payload approval flow")
    suspend fun replyToNotification(sender: String, packageName: String, reply: String): Boolean = false

    fun getUnread(packageName: String? = null, maxItems: Int = 5): List<JarvisNotification> = synchronized(notificationsLock) {
        val safeLimit = maxItems.coerceIn(1, NotificationBufferPolicy.MAX_ITEMS)
        (if (packageName != null) notifications.filter { it.packageName == packageName } else notifications)
            .sortedByDescending { it.timestamp }
            .take(safeLimit)
            .map { item ->
                item.copy(
                    title = NotificationPrivacyPolicy.redact(item.title),
                    body = NotificationPrivacyPolicy.redact(item.body),
                    sender = item.sender?.let(NotificationPrivacyPolicy::redact)
                )
            }
    }

    fun notificationAccessEnabled(): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        return enabled.split(':').any {
            it.equals(android.content.ComponentName(this, JarvisNotificationListener::class.java).flattenToString(), ignoreCase = true)
        }
    }

    fun getSummary(): String {
        val snapshot = synchronized(notificationsLock) { notifications.toList() }
        val grouped = snapshot.groupBy { it.packageName }

        return grouped.entries.sortedByDescending { it.value.size }.take(5).joinToString(", ") { (pkg, msgs) ->
            val app = msgs.first().appLabel
            val count = msgs.size
            val lastSender = msgs.lastOrNull()?.sender
            if (lastSender != null) "$count $app from $lastSender"
            else "$count $app"
        }
    }

    fun clearAll(packageName: String? = null) = synchronized(notificationsLock) {
        if (packageName != null) notifications.removeAll { it.packageName == packageName }
        else notifications.clear()
    }

    data class JarvisNotification(
        val id: Int,
        val packageName: String,
        val appLabel: String,
        val title: String,
        val body: String,
        val timestamp: Long,
        val isMessaging: Boolean,
        val sender: String?
    )
}
