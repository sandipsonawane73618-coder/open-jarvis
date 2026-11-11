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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for an external OmniParser-compatible visual grounding endpoint.
 * Screen parsing models remain on the configured service, not inside this APK.
 */
class OmniParserClient(
    private val endpoint: String,
    private val apiKey: String = "",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
) {
    data class UiElement(
        val id: String,
        val label: String?,
        val contentDescription: String?,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val confidence: Float
    )

    suspend fun parse(screenshot: Bitmap): Result<List<UiElement>> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("image", encodeJpeg(screenshot))
                .put("image_mime_type", "image/jpeg")
                .put("response_format", "jarvis_omniparser_elements_v1")
            val builder = Request.Builder().url(normalizeEndpoint(endpoint))
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("OmniParser service HTTP ${response.code}: ${body.take(300)}")
                parseElements(JSONObject(body).optJSONArray("elements") ?: JSONArray())
            }
        }
    }

    private fun parseElements(array: JSONArray): List<UiElement> {
        require(array.length() <= 300) { "OmniParser returned too many UI elements" }
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val box = item.optJSONObject("box") ?: JSONObject()
            val x = box.optDouble("x", Double.NaN).toFloat()
            val y = box.optDouble("y", Double.NaN).toFloat()
            val width = box.optDouble("width", Double.NaN).toFloat()
            val height = box.optDouble("height", Double.NaN).toFloat()
            require(listOf(x, y, width, height).all { it.isFinite() } && x >= 0f && y >= 0f &&
                width > 0f && height > 0f && x + width <= 1.001f && y + height <= 1.001f) {
                "OmniParser element has invalid normalized bounds"
            }
            UiElement(
                item.optString("id", index.toString()),
                item.optString("label").takeIf { it.isNotBlank() },
                item.optString("content_description").takeIf { it.isNotBlank() },
                x, y, width, height,
                item.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)
            )
        }
    }

    private fun encodeJpeg(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        fun normalizeEndpoint(value: String): String {
            val v = value.trim()
            require(v.startsWith("http://") || v.startsWith("https://")) { "OmniParser URL must use http:// or https://" }
            return v
        }
    }
}
