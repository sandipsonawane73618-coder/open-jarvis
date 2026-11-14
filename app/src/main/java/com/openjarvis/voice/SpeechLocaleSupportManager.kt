package com.openjarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Reports on-device model availability and only requests a model download when the
 * Settings UI calls requestDownload after an explicit user confirmation.
 */
class SpeechLocaleSupportManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("speech_locale_status", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun savedStatus(locale: String): String =
        prefs.getString("status_$locale", "Not checked").orEmpty()

    fun checkRecognitionSupport(locale: String, onStatus: (String) -> Unit) {
        mainHandler.post {
            if (Build.VERSION.SDK_INT < 33) {
                publish(locale, "Requires Android 13+", onStatus)
                return@post
            }
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                publish(locale, "On-device recognizer unavailable", onStatus)
                return@post
            }
            val recognizer = try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } catch (_: RuntimeException) {
                publish(locale, "On-device recognizer unavailable", onStatus)
                return@post
            }
            recognizer.checkRecognitionSupport(localeIntent(locale), context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: android.speech.RecognitionSupport) {
                        val status = RecognitionLocaleRetryPolicy.statusFor(
                            locale,
                            support.installedOnDeviceLanguages.toSet(),
                            support.pendingOnDeviceLanguages.toSet(),
                            support.supportedOnDeviceLanguages.toSet()
                        )
                        recognizer.destroy()
                        publish(locale, status, onStatus)
                    }

                    override fun onError(error: Int) {
                        recognizer.destroy()
                        publish(locale, "Status check failed ($error)", onStatus)
                    }
                })
        }
    }

    /** Must only be called from an explicit user-consent action in Settings. */
    fun requestDownload(locale: String, onStatus: (String) -> Unit) {
        mainHandler.post {
            if (Build.VERSION.SDK_INT < 33) {
                publish(locale, "Requires Android 13+", onStatus)
                return@post
            }
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                publish(locale, "On-device recognizer unavailable", onStatus)
                return@post
            }
            val recognizer = try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } catch (_: RuntimeException) {
                publish(locale, "On-device recognizer unavailable", onStatus)
                return@post
            }
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    recognizer.triggerModelDownload(localeIntent(locale), context.mainExecutor,
                        object : ModelDownloadListener {
                            override fun onProgress(completedPercent: Int) {
                                publish(locale, "Downloading $completedPercent%", onStatus)
                            }
                            override fun onSuccess() {
                                recognizer.destroy()
                                publish(locale, "Installed", onStatus)
                            }
                            override fun onScheduled() {
                                recognizer.destroy()
                                publish(locale, "Download scheduled", onStatus)
                            }
                            override fun onError(error: Int) {
                                recognizer.destroy()
                                publish(locale, "Download failed ($error)", onStatus)
                            }
                        })
                } else {
                    recognizer.triggerModelDownload(localeIntent(locale))
                    publish(locale, "Download requested; system approval may be required", onStatus)
                    mainHandler.postDelayed({ recognizer.destroy() }, 1500L)
                }
            } catch (error: RuntimeException) {
                recognizer.destroy()
                publish(locale, "Download request failed", onStatus)
            }
        }
    }

    private fun localeIntent(locale: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
    }

    private fun publish(locale: String, status: String, callback: (String) -> Unit) {
        prefs.edit().putString("status_$locale", status).apply()
        callback(status)
    }
}
