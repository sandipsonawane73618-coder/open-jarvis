package com.openjarvis.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.openjarvis.voice.WakeWordConfig
import com.openjarvis.ui.OverlayService
import com.openjarvis.voice.voicute.WakeWordEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

class WakeLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WakeLabScreen(onBack = { finish() }) } }
    }
}

private data class WakeClip(val file: File, val label: String, val createdAt: Long)
private data class WakeReplay(
    val clipName: String,
    val label: String,
    val modeAScores: Map<String, Float>,
    val modeBScores: Map<String, Float>,
    val modeAHit: Boolean,
    val modeBHit: Boolean,
    val durationMs: Long,
    val sampleCount: Int,
    val rms: Double,
    val peak: Int,
    val allZeroSamples: Boolean,
    val liveListenerPaused: Boolean?,
    val modeARawOutput: String,
    val modeBRawOutput: String
)
private data class WakeSelfTest(val sampleCount: Int, val rms: Double, val peak: Int, val allZeroSamples: Boolean, val rawOutput: String, val rawBestScore: Float?, val status: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WakeLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { File(context.filesDir, "wake_lab").apply { mkdirs() } }
    val prefs = remember { context.getSharedPreferences("wake_lab", 0) }
    var optedIn by remember { mutableStateOf(prefs.getBoolean("recording_opt_in", false)) }
    var label by remember { mutableStateOf("Jarvis") }
    var clips by remember { mutableStateOf(loadClips(root)) }
    var recording by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Local-only • Voicute • 16 kHz mono PCM") }
    var results by remember { mutableStateOf<List<WakeReplay>>(emptyList()) }
    var selfTest by remember { mutableStateOf<WakeSelfTest?>(null) }
    var busy by remember { mutableStateOf(false) }
    val threshold = remember {
        val p = context.getSharedPreferences(WakeWordConfig.SENSITIVITY_PREFS, 0)
        WakeWordConfig.Sensitivity.fromPreference(
            p.getString(WakeWordConfig.SENSITIVITY_KEY, WakeWordConfig.Sensitivity.HIGH.name)
        ).threshold
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Microphone permission granted" else "Microphone permission denied"
    }

    fun refresh() { clips = loadClips(root) }
    fun recordClip() {
        if (!optedIn) { status = "Enable local recording opt-in first"; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return
        }
        if (recording || busy) return
        recording = true
        val listenerWasPaused = OverlayService.pauseWakeWordForWakeLab()
        status = "Recording 4 seconds… live listener paused=${listenerWasPaused}"
        scope.launch {
            var saved: File? = null
            var failure: String? = null
            try {
                saved = withContext(Dispatchers.IO) {
                    val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, max(AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 8192))
                    try {
                        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
                        val pcm = ShortArray(64000)
                        recorder.startRecording()
                        var offset = 0
                        while (offset < pcm.size) {
                            val n = recorder.read(pcm, offset, pcm.size - offset)
                            check(n > 0) { "AudioRecord read failed: $n" }
                            offset += n
                        }
                        recorder.stop()
                        val stamp = System.currentTimeMillis()
                        val safe = label.replace(Regex("[^A-Za-z0-9_-]"), "_")
                        val wav = File(root, "wake_${stamp}_${safe}.wav")
                        writeWav(wav, pcm)
                        File(root, wav.nameWithoutExtension + ".label").writeText(label)
                        File(root, wav.nameWithoutExtension + ".listener_paused").writeText(listenerWasPaused.toString())
                        wav
                    } finally { runCatching { recorder.release() } }
                }
            } catch (error: Exception) { failure = error.message ?: error.javaClass.simpleName }
            finally {
                recording = false
                OverlayService.resumeWakeWordAfterWakeLab()
            }
            if (saved != null) { refresh(); status = "Saved locally: ${saved.name} • live listener paused=${listenerWasPaused}" }
            else status = "Recording failed: ${failure ?: "unknown"}"
        }
    }

    fun replayAll() {
        if (clips.isEmpty() || busy) return
        busy = true
        status = "Replaying clips in Mode A and Mode B…"
        scope.launch {
            try {
                val replayData = withContext(Dispatchers.IO) {
                    val model = WakeWordEngine(context)
                    try {
                        check(model.isLoaded) { model.errorMessage ?: "Voicute model failed to load" }
                        val tone = ShortArray(model.audioSamplesNeeded)
                        for (i in 0 until minOf(16000, tone.size)) tone[i] = (9000.0 * kotlin.math.sin(2.0 * Math.PI * 440.0 * i / 16000.0)).toInt().toShort()
                        val testStats = pcmStats(tone)
                        val testResult = model.processForWakeLab(tone, false)
                        val test = WakeSelfTest(tone.size, testStats.rms, testStats.peak, testStats.allZero,
                            testResult?.rawOutputSummary ?: "NO_DETECTION_RESULT", testResult?.rawBestScore,
                            if (testResult == null) "NO_RESULT_OR_INFERENCE_ERROR" else if (testResult.rawBestScore != 0f || testResult.rawOutputSummary.split(",").any { it.substringAfter("=", "").toFloatOrNull()?.let { v -> v != 0f } == true }) "NON_ZERO_RAW_OUTPUT" else "RAW_OUTPUT_ZERO")
                        Pair(clips.map { clip -> replayClip(model, clip, threshold) }, test)
                    } finally { model.close() }
                }
                results = replayData.first
                selfTest = replayData.second
                status = "Replay complete • threshold=${"%.3f".format(Locale.US, threshold)}"
            } catch (error: Exception) {
                status = "Replay failed: ${error.message ?: error.javaClass.simpleName}"
            } finally { busy = false }
        }
    }

    val positive = results.filter { it.label != "negative" }
    val negative = results.filter { it.label == "negative" }
    val aHits = positive.count { it.modeAHit }
    val bHits = positive.count { it.modeBHit }
    val aFalse = negative.count { !it.modeAScores.values.all { score -> score < threshold } }
    val bFalse = negative.count { !it.modeBScores.values.all { score -> score < threshold } }
    val report = buildString {
        appendLine("JARVIS WAKE LAB — VOICUTE ONLY")
        appendLine("Sample rate: 16000 Hz mono PCM16")
        appendLine("Threshold: $threshold")
        appendLine("Clip counts: total=${results.size}; positive=${positive.size}; negative=${negative.size}")
        appendLine("Mode A: raw int16-scale (same preprocessing as live)")
        appendLine("Mode B: short / 32768f (experimental only; never used by live listener)")
        appendLine("Aggregation: maximum score per phrase across overlapping 1.01s windows, 100ms hop")
        appendLine("Mode A hit rate: ${if (positive.isEmpty()) "N/A" else "$aHits/${positive.size} = ${100.0*aHits/positive.size}%"}")
        appendLine("Mode B hit rate: ${if (positive.isEmpty()) "N/A" else "$bHits/${positive.size} = ${100.0*bHits/positive.size}%"}")
        appendLine("Mode A false-trigger rate: ${if (negative.isEmpty()) "N/A (no negative clips)" else "$aFalse/${negative.size} = ${100.0*aFalse/negative.size}%"}")
        appendLine("Mode B false-trigger rate: ${if (negative.isEmpty()) "N/A (no negative clips)" else "$bFalse/${negative.size} = ${100.0*bFalse/negative.size}%"}")
        selfTest?.let { t ->
            appendLine("SELF-TEST deterministic 440Hz tone: samples=${t.sampleCount}; durationMs=${t.sampleCount*1000L/16000L}; RMS=${t.rms}; peak=${t.peak}; all-zero samples? ${if (t.allZeroSamples) "haan" else "nahi"}")
            appendLine("Raw ONNX output before threshold/gate: ${t.rawOutput}")
            appendLine("Raw best score=${t.rawBestScore}; status=${t.status}")
        } ?: appendLine("SELF-TEST: not run")
        results.forEach { r ->
            appendLine()
            appendLine("Clip: ${r.clipName} label=${r.label}")
            appendLine("durationMs=${r.durationMs}; sampleCount=${r.sampleCount}; RMS(raw int16 scale)=${r.rms}; peak=${r.peak}; all-zero samples? ${if (r.allZeroSamples) "haan" else "nahi"}")
            appendLine("live listener paused during recording: ${when (r.liveListenerPaused) { true -> "haan"; false -> "nahi"; null -> "unknown (clip predates pause tracking)" }}")
            appendLine("Mode A raw ONNX output: ${r.modeARawOutput}")
            appendLine("Mode B raw ONNX output: ${r.modeBRawOutput}")
            appendLine("A: ${r.modeAScores} result=${if (r.label == "negative") if (r.modeAHit) "FALSE TRIGGER" else "NO TRIGGER" else if (r.modeAHit) "HIT" else "MISS"}")
            appendLine("B: ${r.modeBScores} result=${if (r.label == "negative") if (r.modeBHit) "FALSE TRIGGER" else "NO TRIGGER" else if (r.modeBHit) "HIT" else "MISS"}")
        }
        appendLine()
        appendLine("Privacy: clips stay in app-private local storage. No audio is included in diagnostic reports.")
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(report) }
                ?: error("Cannot write export")
        }.onFailure { status = "Export failed: ${it.message}" }
            .onSuccess { status = "Wake Lab TXT exported" }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Wake Lab • Voicute") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Back") }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text(status, style = MaterialTheme.typography.bodySmall) }
            item {
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Local recording opt-in", style = MaterialTheme.typography.titleMedium)
                        Text("Recording is off by default. Audio stays only in this app's private storage.")
                        Row {
                            Switch(checked = optedIn, onCheckedChange = {
                                optedIn = it
                                prefs.edit().putBoolean("recording_opt_in", it).apply()
                                if (!it) status = "Recording opt-in disabled"
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(if (optedIn) "Enabled" else "Disabled")
                        }
                        Text("Expected phrase / label")
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(value = label, onValueChange = {}, readOnly = true,
                                modifier = Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("Jarvis", "Hey Jarvis", "Hi Jarvis", "negative").forEach { item ->
                                    DropdownMenuItem(text = { Text(item) }, onClick = { label = item; expanded = false })
                                }
                            }
                        }
                        Button(onClick = { recordClip() }, enabled = optedIn && !recording && !busy) {
                            Text(if (recording) "Recording…" else "Record 4-second clip")
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { replayAll() }, enabled = clips.isNotEmpty() && !busy) { Text("Replay A + B") }
                    OutlinedButton(onClick = { exportLauncher.launch("Wake_Lab_${System.currentTimeMillis()}.txt") }, enabled = results.isNotEmpty()) { Text("Export TXT") }
                }
            }
            item { Text("Saved local clips (${clips.size})", style = MaterialTheme.typography.titleMedium) }
            items(clips, key = { it.file.name }) { clip ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(clip.file.name)
                        Text("Label: ${clip.label}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                OutlinedButton(onClick = {
                    root.listFiles()?.forEach { it.delete() }
                    results = emptyList()
                    refresh()
                    status = "All Wake Lab clips deleted"
                }, enabled = !recording && !busy && clips.isNotEmpty()) { Text("Delete all clips") }
            }
            item { Text("Replay results", style = MaterialTheme.typography.titleMedium) }
            items(results, key = { it.clipName }) { result ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text("${result.clipName} • ${result.label}")
                        Text("Mode A: ${result.modeAScores} • ${if (result.label == "negative") if (result.modeAHit) "FALSE TRIGGER" else "NO TRIGGER" else if (result.modeAHit) "HIT" else "MISS"}")
                        Text("Mode B: ${result.modeBScores} • ${if (result.label == "negative") if (result.modeBHit) "FALSE TRIGGER" else "NO TRIGGER" else if (result.modeBHit) "HIT" else "MISS"}")
                    }
                }
            }
        }
    }
}

private data class PcmStats(val rms: Double, val peak: Int, val allZero: Boolean)
private fun pcmStats(pcm: ShortArray): PcmStats {
    if (pcm.isEmpty()) return PcmStats(0.0, 0, true)
    var sum = 0.0
    var peak = 0
    var allZero = true
    pcm.forEach { sample ->
        val v = sample.toInt()
        if (v != 0) allZero = false
        peak = max(peak, kotlin.math.abs(v))
        sum += v.toDouble() * v.toDouble()
    }
    return PcmStats(kotlin.math.sqrt(sum / pcm.size), peak, allZero)
}
private fun loadClips(root: File): List<WakeClip> = root.listFiles()
    ?.filter { it.extension.equals("wav", true) }
    ?.sortedByDescending { it.lastModified() }
    ?.map { wav ->
        val labelFile = File(root, wav.nameWithoutExtension + ".label")
        WakeClip(wav, labelFile.takeIf { it.exists() }?.readText()?.trim() ?: "negative", wav.lastModified())
    }.orEmpty()

private fun writeWav(file: File, pcm: ShortArray) {
    FileOutputStream(file).use { out ->
        val dataBytes = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII)); header.putInt(36 + dataBytes)
        header.put("WAVE".toByteArray(Charsets.US_ASCII)); header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16); header.putShort(1); header.putShort(1); header.putInt(16000)
        header.putInt(32000); header.putShort(2); header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII)); header.putInt(dataBytes)
        out.write(header.array())
        val bytes = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { bytes.putShort(it) }
        out.write(bytes.array())
    }
}

private fun readWav(file: File): ShortArray {
    val bytes = file.readBytes()
    require(bytes.size >= 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Invalid WAV: ${file.name}" }
    val pcm = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
    return ShortArray((bytes.size - 44) / 2) { pcm.short }
}

private fun replayClip(model: WakeWordEngine, clip: WakeClip, threshold: Float): WakeReplay {
    val pcm = readWav(clip.file)
    val stats = pcmStats(pcm)
    val paused = File(clip.file.parentFile, clip.file.nameWithoutExtension + ".listener_paused").takeIf { it.exists() }?.readText()?.trim()?.toBooleanStrictOrNull()
    val windowSize = model.audioSamplesNeeded
    require(windowSize > 0) { "Voicute window size unavailable" }
    val keys = listOf("jarvis", "hey jarvis", "hi jarvis")
    val a = keys.associateWith { 0f }.toMutableMap()
    val b = keys.associateWith { 0f }.toMutableMap()
    val hop = 1600
    var rawA = "NO_RESULT"
    var rawB = "NO_RESULT"
    var start = 0
    while (start < pcm.size) {
        val window = ShortArray(windowSize)
        val count = minOf(windowSize, pcm.size - start)
        System.arraycopy(pcm, start, window, 0, count)
        val resultA = model.processForWakeLab(window, false)
        val resultB = model.processForWakeLab(window, true)
        if (resultA != null) rawA = resultA.rawOutputSummary
        if (resultB != null) rawB = resultB.rawOutputSummary
        parseScores(resultA?.scoreSummary).forEach { (k, v) -> if (a.containsKey(k)) a[k] = max(a[k] ?: 0f, v) }
        parseScores(resultB?.scoreSummary).forEach { (k, v) -> if (b.containsKey(k)) b[k] = max(b[k] ?: 0f, v) }
        if (start + windowSize >= pcm.size) break
        start += hop
    }
    val expected = clip.label.lowercase(Locale.US)
    val hitA = if (expected == "negative") a.values.any { it >= threshold } else (a[expected] ?: 0f) >= threshold
    val hitB = if (expected == "negative") b.values.any { it >= threshold } else (b[expected] ?: 0f) >= threshold
    return WakeReplay(clip.file.name, expected, a, b, hitA, hitB, pcm.size*1000L/16000L, pcm.size, stats.rms, stats.peak, stats.allZero, paused, rawA, rawB)
}

private fun parseScores(summary: String?): Map<String, Float> = summary.orEmpty().split(",")
    .mapNotNull { token ->
        val parts = token.trim().split("=", limit = 2)
        if (parts.size != 2) null else parts[1].toFloatOrNull()?.let { parts[0].trim().lowercase(Locale.US) to it }
    }.toMap()
