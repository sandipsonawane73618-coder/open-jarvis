package com.openjarvis.skills

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.openjarvis.agent.ContactCommandParser
import com.openjarvis.agent.AppTargetResolver
import com.openjarvis.agent.InstalledAppTarget
import com.openjarvis.agent.SearchCommandParser

/** Android effects live here; SkillRegistry remains JVM-testable and side-effect free. */
class AndroidSkillExecutor(private val context: Context) : SkillRegistry.SkillExecutor {
    override suspend fun execute(request: SkillRegistry.Request): Result<Unit> = runCatching {
        val args = request.args
        when (request.kind) {
            SkillRegistry.Kind.OPEN_APP -> {
                val intent = context.packageManager.getLaunchIntentForPackage(resolvePackage(args.getValue("app")))
                    ?: throw IllegalArgumentException("App not installed: ${args["app"]}")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent)
            }
            SkillRegistry.Kind.SETTINGS -> {
                val action = when (args.getValue("screen")) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                    "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                    "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                launch(Intent(action))
            }
            SkillRegistry.Kind.CALL -> {
                val parsed = ContactCommandParser.parse(args["text"].orEmpty())
                val name = parsed?.name ?: args["contact"]
                val uri = if (name != null) Uri.parse("tel:").buildUpon().appendPath(name).build()
                    else Uri.parse("tel:${args["number"] ?: ""}")
                launch(Intent(Intent.ACTION_DIAL, uri))
            }
            SkillRegistry.Kind.SEARCH -> {
                val raw = args["text"].orEmpty()
                val query = SearchCommandParser.parseYouTubeQuery(raw)
                    ?: SearchCommandParser.parseWebQuery(raw) ?: raw
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
            }
            SkillRegistry.Kind.WHATSAPP_MESSAGE -> {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, args.getValue("text"))
                    setPackage("com.whatsapp")
                }; launch(i)
            }
            SkillRegistry.Kind.SMS_MESSAGE -> launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", args.getValue("text")))
            SkillRegistry.Kind.ALARM -> launch(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, args.getValue("hour").toInt()).putExtra(AlarmClock.EXTRA_MINUTES, args.getValue("minute").toInt()).putExtra(AlarmClock.EXTRA_SKIP_UI, true).putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis alarm"))
            SkillRegistry.Kind.TIMER -> launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, args.getValue("seconds").toInt()).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            SkillRegistry.Kind.TORCH -> {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                    ?: throw IllegalStateException("No torch available")
                manager.setTorchMode(cameraId, args.getValue("enabled").toBoolean())
            }
            SkillRegistry.Kind.VOLUME -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val up = args.getValue("direction") == "up"
                val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val atLimit = if (up) before >= maximum else before <= 0
                val adjustment = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjustment, AudioManager.FLAG_SHOW_UI)
                val after = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (!atLimit && after == before) {
                    throw IllegalStateException("Android media volume did not change; action was not verified")
                }
            }
            SkillRegistry.Kind.TIME, SkillRegistry.Kind.DATE -> Unit
            SkillRegistry.Kind.MAPS -> {
                val destination = args.getValue("destination")
                val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination)))
                    .setPackage("com.google.android.apps.maps")
                if (mapsIntent.resolveActivity(context.packageManager) != null) launch(mapsIntent)
                else launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination))))
            }
        }
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            throw IllegalStateException("No Android app can handle this action: ${intent.action ?: "unknown"}")
        }
        try {
            context.startActivity(intent)
        } catch (error: android.content.ActivityNotFoundException) {
            throw IllegalStateException("No Android app can handle this action: ${intent.action ?: "unknown"}", error)
        }
    }

    private fun resolvePackage(label: String): String {
        val normalized = label.trim().lowercase(java.util.Locale.ROOT)
        if (normalized in setOf("youtube", "you tube", "yt", "यूट्यूब")) {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val installed = context.packageManager.queryIntentActivities(launcherIntent, 0)
                .mapNotNull { info ->
                    val activity = info.activityInfo ?: return@mapNotNull null
                    val appLabel = runCatching { info.loadLabel(context.packageManager).toString().trim() }.getOrNull()
                    if (appLabel.isNullOrBlank()) null else InstalledAppTarget(appLabel, activity.packageName)
                }
                .distinctBy { it.packageName }
            val candidates = AppTargetResolver.youtubeCandidates(installed)
            val mods = candidates.filter { it.packageName != "com.google.android.youtube" }
            if (mods.size > 1) {
                throw IllegalStateException("Multiple YouTube variants are installed: ${mods.joinToString(", ") { it.label }}. Which one should I open?")
            }
            mods.singleOrNull()?.let { return it.packageName }
            if (context.packageManager.getLaunchIntentForPackage("com.google.android.youtube") != null) {
                return "com.google.android.youtube"
            }
        }
        if (normalized in setOf("calendar", "google calendar")) {
            val calendarPackages = listOf(
                "com.google.android.calendar",
                "com.coloros.calendar",
                "com.android.calendar"
            )
            calendarPackages.firstOrNull { packageName ->
                context.packageManager.getLaunchIntentForPackage(packageName) != null
            }?.let { return it }
            throw IllegalStateException("No Calendar app is installed on this device.")
        }
        val known = when (normalized) {
            "youtube" -> "com.google.android.youtube"
            "whatsapp" -> "com.whatsapp"
            "chrome" -> "com.android.chrome"
            "instagram", "insta" -> "com.instagram.android"
            "maps", "google maps" -> "com.google.android.apps.maps"
            "telegram" -> "org.telegram.messenger"
            "facebook" -> "com.facebook.katana"
            else -> null
        }
        if (known != null && context.packageManager.getLaunchIntentForPackage(known) != null) return known

        val installedMatch = runCatching {
            context.packageManager.getInstalledApplications(0).firstOrNull { app ->
                context.packageManager.getLaunchIntentForPackage(app.packageName) != null &&
                    context.packageManager.getApplicationLabel(app).toString().trim()
                        .equals(label.trim(), ignoreCase = true)
            }?.packageName
        }.getOrNull()
        return installedMatch ?: known ?: label.trim()
    }
}
