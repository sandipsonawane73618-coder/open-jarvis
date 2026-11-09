package com.openjarvis.voice

import android.content.Context
import com.openjarvis.voice.voicute.WakeWordEngine as VoicuteWakeWordEngine
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Loads and validates the bundled, offline Voicute multi-keyword model.
 * Model bytes are bundled by scripts/prepare-voicute.sh; runtime inference never
 * downloads models or sends audio off-device.
 */
class VoicuteModelLoader(context: Context) {
    private val appContext = context.applicationContext

    data class LoadedModel(
        val engine: VoicuteWakeWordEngine,
        val keywords: List<String>,
        val melFrames: Int
    )

    fun load(): LoadedModel {
        val metadataBytes = appContext.assets.open(MODEL_INFO_ASSET).use { it.readBytes() }
        val metadata = JSONObject(String(metadataBytes, StandardCharsets.UTF_8))
        val modelType = metadata.optString("model_type")
        require(modelType == "multi_keyword") {
            "Voicute model must use multi_keyword format; found '$modelType'"
        }

        val keywordArray = metadata.optJSONArray("keywords")
            ?: throw IllegalStateException("Voicute model_info.json has no keywords array")
        val keywords = (0 until keywordArray.length()).map { keywordArray.getString(it) }
        validateDemoKeywords(keywords)

        val modelFile = metadata.optString("model_file")
        require(modelFile.isNotBlank()) { "Voicute model_file is missing" }
        appContext.assets.open(modelFile).use { /* verify bundled model is readable */ }
        appContext.assets.open(MEL_MODEL_ASSET).use { /* verify mel model is readable */ }

        val engine = VoicuteWakeWordEngine(appContext)
        if (!engine.isLoaded) {
            val detail = engine.errorMessage ?: "unknown ONNX model loading error"
            engine.close()
            throw IllegalStateException("Voicute ONNX model failed to load: $detail")
        }
        val melFrames = metadata.optInt("mel_time", 98)
        require(melFrames > 0) {
            engine.close()
            "Voicute mel_time must be positive"
        }
        return LoadedModel(engine, keywords, melFrames)
    }

    companion object {
        const val MODEL_INFO_ASSET = "model_info.json"
        const val MEL_MODEL_ASSET = "melspectrogram.onnx"
        const val MODEL_ENGINE_NAME = "Voicute ONNX KWS"
        const val DEMO_DISPLAY_PHRASES = "Jarvis / Hey Jarvis / Hi Jarvis"
        private val REQUIRED_DEMO_KEYWORDS = setOf("jarvis", "hey jarvis", "hi jarvis")

        internal fun validateDemoKeywords(keywords: List<String>) {
            val normalized = keywords.map { it.trim().lowercase() }.toSet()
            require(normalized == REQUIRED_DEMO_KEYWORDS) {
                "Bundled Voicute demo must contain exactly Jarvis, Hey Jarvis and Hi Jarvis; found: ${keywords.joinToString()}"
            }
        }
    }
}
