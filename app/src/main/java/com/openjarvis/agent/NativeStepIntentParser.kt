package com.openjarvis.agent

import org.json.JSONObject

/**
 * Strict decoder for the dedicated native-intent router.
 * A null result means the model explicitly chose "none" or returned invalid data.
 */
internal object NativeStepIntentParser {
    fun parse(raw: String): StepIntent? = runCatching {
        val json = JSONObject(raw.trim())
        require(json.keys().asSequence().toSet() == setOf("skill", "args"))
        val skill = json.getString("skill").lowercase()
        val args = json.getJSONObject("args")
        when (skill) {
            "none" -> {
                require(args.length() == 0)
                null
            }
            "alarm" -> {
                requireKeys(args, "hour", "minute")
                val hour = integer(args, "hour")
                val minute = integer(args, "minute")
                require(hour in 0..23 && minute in 0..59)
                StepIntent.Alarm(hour, minute)
            }
            "timer" -> {
                requireKeys(args, "seconds")
                val seconds = integer(args, "seconds")
                require(seconds in 1..86400)
                StepIntent.Timer(seconds)
            }
            "torch" -> {
                requireKeys(args, "on")
                StepIntent.Torch(boolean(args, "on"))
            }
            "volume" -> {
                requireKeys(args, "up")
                StepIntent.Volume(boolean(args, "up"))
            }
            "navigate" -> {
                requireKeys(args, "place")
                val place = args.getString("place").trim()
                require(place.isNotEmpty() && place.length <= 120)
                StepIntent.Navigate(place)
            }
            "app_launch" -> {
                requireKeys(args, "label")
                val label = args.getString("label").trim()
                require(label.isNotEmpty() && label.length <= 60)
                StepIntent.AppLaunch(label)
            }
            "message" -> {
                require(args.keys().asSequence().all { it in setOf("app", "contact", "text") })
                val app = optionalString(args, "app")?.trim()?.takeIf { it.isNotEmpty() }
                val contact = optionalString(args, "contact")?.trim()?.takeIf { it.isNotEmpty() }
                val text = optionalString(args, "text")?.trim()?.takeIf { it.isNotEmpty() }
                require(app == null || app in setOf("whatsapp", "sms"))
                require(contact != null || text != null)
                require(contact == null || contact.length <= 100)
                require(text == null || text.length <= 1000)
                StepIntent.Message(app, contact, text)
            }
            else -> null
        }
    }.getOrNull()

    private fun requireKeys(args: JSONObject, vararg keys: String) {
        require(args.keys().asSequence().toSet() == keys.toSet())
    }

    private fun integer(args: JSONObject, key: String): Int {
        val value = args.get(key)
        require(value is Number)
        val asDouble = value.toDouble()
        require(asDouble.isFinite() && asDouble % 1.0 == 0.0)
        require(asDouble >= Int.MIN_VALUE && asDouble <= Int.MAX_VALUE)
        return asDouble.toInt()
    }

    private fun boolean(args: JSONObject, key: String): Boolean {
        val value = args.get(key)
        require(value is Boolean)
        return value
    }

    private fun optionalString(args: JSONObject, key: String): String? {
        if (!args.has(key) || args.isNull(key)) return null
        return args.getString(key)
    }
}
