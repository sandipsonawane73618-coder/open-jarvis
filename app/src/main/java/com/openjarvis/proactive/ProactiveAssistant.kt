package com.openjarvis.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Opt-in, local-only proactive notifications. No network or LLM is started in background.
 */
object ProactiveAssistant {
    private const val CHANNEL_ID = "jarvis_proactive"
    private const val DAILY_BRIEFING = "jarvis_daily_briefing"

    fun scheduleReminder(context: Context, title: String, message: String, delayMinutes: Long) {
        require(title.isNotBlank() && message.isNotBlank())
        require(delayMinutes >= 1)
        val data = Data.Builder()
            .putString(ProactiveNotificationWorker.KEY_TITLE, title.take(80))
            .putString(ProactiveNotificationWorker.KEY_MESSAGE, message.take(240))
            .build()
        val request = OneTimeWorkRequestBuilder<ProactiveNotificationWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "jarvis_reminder_${request.id}", ExistingWorkPolicy.KEEP, request
        )
    }

    /** Daily local briefing; the worker posts a concise reminder to review today's agenda. */
    fun isDailyBriefingEnabled(context: Context): Boolean =
        context.getSharedPreferences("jarvis_proactive_settings", Context.MODE_PRIVATE)
            .getBoolean("daily_briefing_enabled", false)

    fun enableDailyBriefing(context: Context, enabled: Boolean) {
        context.getSharedPreferences("jarvis_proactive_settings", Context.MODE_PRIVATE)
            .edit().putBoolean("daily_briefing_enabled", enabled).apply()
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(DAILY_BRIEFING)
            return
        }
        val request = PeriodicWorkRequestBuilder<DailyBriefingWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(24, TimeUnit.HOURS)
            .build()
        manager.enqueueUniquePeriodicWork(
            DAILY_BRIEFING, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    internal fun post(context: Context, title: String, message: String, id: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "JARVIS Proactive Assistant", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.openjarvis.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}

class ProactiveNotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val message = inputData.getString(KEY_MESSAGE) ?: return Result.failure()
        ProactiveAssistant.post(applicationContext, title, message, id.hashCode())
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
    }
}

class DailyBriefingWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        ProactiveAssistant.post(
            applicationContext,
            "JARVIS Daily Briefing",
            "Good morning. Review your reminders and today's priorities.",
            7301
        )
        return Result.success()
    }
}

/** Battery alerts are event-driven; no background polling or permanent service. */
class ProactiveBatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_LOW -> ProactiveAssistant.post(
                context, "JARVIS Battery Alert", "Battery is low. Consider enabling battery saver.", 7302
            )
            Intent.ACTION_POWER_CONNECTED -> ProactiveAssistant.post(
                context, "JARVIS Power", "Charging started.", 7303
            )
            Intent.ACTION_POWER_DISCONNECTED -> ProactiveAssistant.post(
                context, "JARVIS Power", "Charger disconnected.", 7304
            )
        }
    }
}
