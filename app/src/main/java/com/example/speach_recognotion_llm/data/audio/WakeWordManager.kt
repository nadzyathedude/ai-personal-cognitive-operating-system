package com.example.speach_recognotion_llm.data.audio

import android.content.Context
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager

class WakeWordManager(
    private val context: Context,
    private val accessKey: String,
    private val onWakeWordDetected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var porcupineManager: PorcupineManager? = null

    @Volatile
    private var _isListening = false
    val isActive: Boolean get() = _isListening

    fun start() {
        if (_isListening) return

        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath("hi-dude_en_android_v4_0_0.ppn")
                .setSensitivity(0.7f)
                .build(context) { keywordIndex ->
                    onWakeWordDetected()
                }

            porcupineManager?.start()
            _isListening = true
        } catch (e: PorcupineException) {
            _isListening = false
            onError("Failed to start Porcupine: ${e.message}")
        }
    }

    fun stop() {
        if (!_isListening) return
        _isListening = false
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (_: PorcupineException) {
        }
        porcupineManager = null
    }

    fun destroy() {
        _isListening = false
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (_: PorcupineException) {
        }
        porcupineManager = null
    }
}
