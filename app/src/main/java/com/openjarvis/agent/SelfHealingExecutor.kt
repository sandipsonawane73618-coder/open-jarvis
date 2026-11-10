package com.openjarvis.agent

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import com.openjarvis.accessibility.JarvisAccessibilityService

/**
 * Deterministic recovery planner. It classifies failures, verifies local dependencies
 * with bounded read-only probes, records privacy-safe recurrence counters, and never
 * replays UI actions or silently changes the configured model/provider.
 */
class SelfHealingExecutor(context: Context) {
    private val appContext = context.applicationContext
    private val patternStore = SelfHealingPatternStore(appContext)
    private val ollamaHealthCheck = LocalOllamaHealthCheck()

    /** Consult privacy-safe local history before permitting another read-only retry. */
    fun shouldRetry(action: Action, reason: String): Boolean {
        val decision = SelfHealingRecoveryPolicy.classify(action, reason)
        val pattern = patternStore.patterns().firstOrNull { it.area == decision.area.name }
        val consecutive = pattern?.consecutiveFailures ?: 0
        val learnedDecision = SelfHealingRecoveryPolicy.applyLearning(decision, consecutive)
        val allowed = SelfHealingSafetyPolicy.shouldRetry(action, reason) &&
            SelfHealingSafetyPolicy.shouldRetryAfterLearning(action, reason, consecutive) &&
            learnedDecision.mayRetryAutomatically
        if (!allowed && SelfHealingSafetyPolicy.shouldRetry(action, reason)) {
            Log.w("JarvisSelfHealing", "retry_paused area=${decision.area} recovery=${learnedDecision.recovery} consecutiveUnresolved=$consecutive")
        }
        return allowed
    }

    suspend fun recordVerifiedReadOnlyRecovery(action: Action, originalFailure: String) {
        val decision = SelfHealingRecoveryPolicy.classify(action, originalFailure)
        if (!decision.mayRetryAutomatically) return

        // A successful UI observation does not prove that Ollama recovered. Verify each
        // failure area with evidence from that same subsystem before updating learned state.
        val verified = when (decision.area) {
            SelfHealingRecoveryPolicy.FailureArea.OLLAMA_CONNECTIVITY -> {
                val result = ollamaHealthCheck.verify()
                Log.i("JarvisSelfHealing", "post_retry_ollama_probe state=${result.state}")
                result.state == LocalOllamaHealthPolicy.State.READY
            }
            else -> SelfHealingVerificationPolicy.confirmsReadOnlyRecovery(action, decision.area)
        }
        val outcome = patternStore.recordOutcome(decision.area.name, verified)
        Log.i("JarvisSelfHealing", "recovery_outcome area=${decision.area} verified=$verified unresolved=${outcome?.unresolvedFailures ?: -1}")
    }

    suspend fun failureDisposition(action: Action, reason: String): String {
        val decision = SelfHealingRecoveryPolicy.classify(action, reason)
        var pattern = patternStore.record(decision.area.name)
        val verification = when (decision.area) {
            SelfHealingRecoveryPolicy.FailureArea.OLLAMA_CONNECTIVITY,
            SelfHealingRecoveryPolicy.FailureArea.MODEL_UNAVAILABLE -> {
                val result = ollamaHealthCheck.verify()
                Log.i("JarvisSelfHealing", "local_ollama_probe state=${result.state}")
                if (decision.area == SelfHealingRecoveryPolicy.FailureArea.OLLAMA_CONNECTIVITY) {
                    // A ready endpoint plus the configured model verifies connectivity recovery.
                    val dependencyVerified = result.state == LocalOllamaHealthPolicy.State.READY
                    val outcome = patternStore.recordOutcome(decision.area.name, dependencyVerified)
                    Log.i("JarvisSelfHealing", "recovery_outcome area=${decision.area} verified=$dependencyVerified unresolved=${outcome?.unresolvedFailures ?: -1}")
                } else {
                    // Listing the model is diagnostic evidence, not proof that the failed
                    // model request recovered. Keep MODEL_UNAVAILABLE unresolved.
                    Log.i("JarvisSelfHealing", "model_availability_observed state=${result.state}; failure remains unresolved")
                }
                " Local dependency check: ${result.detail} The failed task was not replayed; retry it only after reviewing this result."
            }
            SelfHealingRecoveryPolicy.FailureArea.ACCESSIBILITY,
            SelfHealingRecoveryPolicy.FailureArea.OVERLAY,
            SelfHealingRecoveryPolicy.FailureArea.SPEECH,
            SelfHealingRecoveryPolicy.FailureArea.APP_LAUNCH -> {
                val result = verifyAndroidConfiguration(action, decision.area)
                Log.i("JarvisSelfHealing", "local_android_probe area=${decision.area} result=$result")
                " Local read-only check: $result"
            }
            else -> " Verification required: ${SelfHealingVerificationPolicy.instruction(decision.area)}"
        }
        // Re-read after the probe: a verified dependency recovery must clear the
        // consecutive-unresolved streak before recurrence guidance is generated.
        pattern = patternStore.patterns().firstOrNull { it.area == decision.area.name } ?: pattern
        val learnedDecision = SelfHealingRecoveryPolicy.applyLearning(
            decision,
            pattern.consecutiveFailures
        )
        val recurrence = if (learnedDecision.recovery == SelfHealingRecoveryPolicy.RecoveryKind.ESCALATE) {
            " This failure category has ${pattern.consecutiveFailures} consecutive unresolved occurrences; automatic retry is paused until user review."
        } else ""
        Log.w("JarvisSelfHealing", "area=${decision.area} recovery=${learnedDecision.recovery} autoRetry=${learnedDecision.mayRetryAutomatically} consecutiveUnresolved=${pattern.consecutiveFailures} unresolved=${pattern.unresolvedFailures}")
        return "${learnedDecision.explanation}${verification}${recurrence}"
    }

    /**
     * Read-only Android checks only. A granted permission is not treated as proof
     * that the corresponding runtime engine or overlay is currently functioning.
     */
    private fun verifyAndroidConfiguration(
        action: Action,
        area: SelfHealingRecoveryPolicy.FailureArea
    ): String = when (area) {
        SelfHealingRecoveryPolicy.FailureArea.ACCESSIBILITY -> {
            if (JarvisAccessibilityService.instance != null) {
                "Jarvis Accessibility service is connected now; the earlier screen observation still failed. Request a fresh read-only observation."
            } else {
                "Jarvis Accessibility service is not connected. Enable it in Android Accessibility settings; Jarvis did not change the setting."
            }
        }
        SelfHealingRecoveryPolicy.FailureArea.OVERLAY -> {
            val granted = runCatching { Settings.canDrawOverlays(appContext) }.getOrDefault(false)
            if (granted) {
                "Display-over-other-apps permission is granted. This does not prove the floating overlay process is running; check/restart it manually."
            } else {
                "Display-over-other-apps permission is not granted. Enable it manually in Android app settings; Jarvis did not change the setting."
            }
        }
        SelfHealingRecoveryPolicy.FailureArea.SPEECH -> {
            val granted = appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                "Microphone permission is granted. This does not prove speech activation; a live listening test is still required."
            } else {
                "Microphone permission is not granted. Enable it manually in Android app permissions; Jarvis did not request or change it."
            }
        }
        SelfHealingRecoveryPolicy.FailureArea.APP_LAUNCH -> {
            val packageName = action.packageName?.takeIf { it.isNotBlank() }
            if (packageName == null) {
                "The failed app target has no resolved package name, so installation/launcher availability could not be verified."
            } else {
                val launchable = runCatching {
                    appContext.packageManager.getLaunchIntentForPackage(packageName) != null
                }.getOrDefault(false)
                if (launchable) {
                    "The target app is installed and has a launcher activity. Jarvis did not launch it again."
                } else {
                    "The target app has no available launcher activity. Check whether it is installed or disabled."
                }
            }
        }
        else -> SelfHealingVerificationPolicy.instruction(area)
    }
}
