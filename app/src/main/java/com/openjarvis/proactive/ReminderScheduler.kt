package com.openjarvis.proactive

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Schedules a local, on-device reminder. WorkManager persists the request across
 * process death and device restarts; no network or cloud service is used.
 */
object ReminderScheduler {
    private const val INPUT_MESSAGE = "reminder_message"
    private const val TAG = "jarvis-reminder"

    fun schedule(context: Context, message: String, delaySeconds: Long): UUID {
        val cleanMessage = message.trim()
        require(cleanMessage.length in 2..240) { "Reminder text must be 2 to 240 characters" }
        require(delaySeconds in 10L..604_800L) { "Reminder delay must be between 10 seconds and 7 days" }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            throw IllegalStateException("Allow Jarvis notifications before scheduling reminders")
        }

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(Data.Builder().putString(INPUT_MESSAGE, cleanMessage).build())
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("jarvis-reminder-${request.id}", ExistingWorkPolicy.KEEP, request)
        return request.id
    }
}

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val message = inputData.getString("reminder_message")
            ?.trim()
            ?.takeIf { it.length in 2..240 }
            ?: return Result.failure()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure()
        }

        return try {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Jarvis Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "Scheduled reminders created by Jarvis" }
                )
            }

            val launchIntent = applicationContext.packageManager
                .getLaunchIntentForPackage(applicationContext.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    applicationContext,
                    NOTIFICATION_ID,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(com.openjarvis.R.drawable.ic_notification)
                .setContentTitle("Jarvis Reminder")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (_: SecurityException) {
            Result.failure()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_reminders"
        private const val NOTIFICATION_ID = 7401
    }
}
