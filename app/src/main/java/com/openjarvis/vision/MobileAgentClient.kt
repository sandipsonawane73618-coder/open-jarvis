package com.openjarvis.vision

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional adapter for a separately hosted MobileAgent-compatible service.
 * The Python/model runtime is not bundled in the Android APK.
 */
class MobileAgentClient(
    private val endpoint: String,
    private val apiKey: String = "",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
) {
    data class Step(val action: String, val target: String? = null, val text: String? = null, val rationale: String? = null)

    suspend fun nextStep(task: String, screenshot: Bitmap): Result<Step> = withContext(Dispatchers.IO) {
        runCatching {
            require(task.isNotBlank()) { "Task description is required" }
            val payload = JSONObject()
                .put("task", task)
                .put("image", encodeJpeg(screenshot))
                .put("image_mime_type", "image/jpeg")
                .put("response_format", "jarvis_mobile_agent_step_v1")
            val builder = Request.Builder().url(normalizeEndpoint(endpoint))
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("MobileAgent service HTTP ${response.code}: ${body.take(300)}")
                val json = JSONObject(body)
                val action = json.optString("action").lowercase()
                require(action in ALLOWED_ACTIONS) { "Unsupported MobileAgent action: $action" }
                Step(action, json.optString("target").takeIf { it.isNotBlank() },
                    json.optString("text").takeIf { it.isNotBlank() },
                    json.optString("rationale").takeIf { it.isNotBlank() })
            }
        }
    }

    private fun encodeJpeg(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        val ALLOWED_ACTIONS = setOf("tap", "type", "scroll", "back", "home", "wait", "done")
        fun normalizeEndpoint(value: String): String {
            val v = value.trim()
            require(v.startsWith("http://") || v.startsWith("https://")) { "MobileAgent URL must use http:// or https://" }
            return v
        }
    }
}
