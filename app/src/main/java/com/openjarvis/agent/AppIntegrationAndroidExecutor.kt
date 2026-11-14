package com.openjarvis.agent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import android.net.Uri
import java.time.ZoneId
import java.util.Date

/**
 * Android 16-compatible intent execution for app integrations.
 * All outbound communication remains a user-reviewed draft.
 */
internal object AppIntegrationAndroidExecutor {
    fun instagramSearch(context: Context, query: String): String {
        val plan = AppIntegrationExecutionPolicy.instagramSearch(query)
            ?: throw IllegalArgumentException("Instagram search text is empty.")
        val uri = Uri.parse(plan.uri)
        val appIntent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(plan.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (appIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(appIntent)
                return "Instagram search opened for ${query.trim()}. Review results in Instagram."
            }
        } catch (error: ActivityNotFoundException) {
            Log.i("JarvisAppIntegration", "Instagram app did not handle search URI; trying browser", error)
        }
        val browserIntent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (browserIntent.resolveActivity(context.packageManager) == null) {
            throw IllegalStateException("Instagram is not available and no browser can open its search.")
        }
        context.startActivity(browserIntent)
        return "Instagram search opened in browser for ${query.trim()}."
    }

    fun telegramDraft(context: Context, request: AppIntegrationCommandParser.TelegramMessageRequest): String {
        val plan = AppIntegrationExecutionPolicy.telegramMessage(request.message)
            ?: throw IllegalArgumentException("Telegram message text is empty.")
        val installed = runCatching {
            context.packageManager.getPackageInfo("org.telegram.messenger", 0)
        }.isSuccess
        if (!installed) throw IllegalStateException("Telegram is not installed on this device.")
        val shareIntent = Intent(plan.action)
            .setType(plan.mimeType)
            .putExtra(Intent.EXTRA_TEXT, plan.text)
            .setPackage(plan.packageName)
        if (shareIntent.resolveActivity(context.packageManager) == null) {
            throw IllegalStateException("Telegram cannot open a text draft on this device.")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Choose Telegram chat")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Telegram draft prepared for ${request.contactName}. Choose the correct chat and tap Send yourself; Jarvis did NOT send it."
    }

    fun calendarEvent(context: Context, request: AppIntegrationCommandParser.CalendarEventRequest): String {
        val plan = AppIntegrationExecutionPolicy.calendarEvent(
            request.title, request.start, request.durationMinutes
        ) ?: throw IllegalArgumentException("Calendar event details are invalid.")
        val zone = ZoneId.systemDefault()
        val beginMillis = plan.start!!.atZone(zone).toInstant().toEpochMilli()
        val endMillis = beginMillis + plan.durationMinutes!! * 60_000L
        val insertIntent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, plan.title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (insertIntent.resolveActivity(context.packageManager) == null) {
            throw IllegalStateException("No Calendar app is available to create this event.")
        }
        context.startActivity(insertIntent)
        return "Calendar event draft opened: ${request.title}. Review details and tap Save yourself."
    }
}
