package com.example.speach_recognotion_llm.data.audio

import android.content.Context
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback

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
                .setKeyword(Porcupine.BuiltInKeyword.PORCUPINE)
                .setSensitivity(0.7f)
                .build(
                    context,
                    PorcupineManagerCallback { onWakeWordDetected() }
                ) { error ->
                    _isListening = false
                    onError("Porcupine error: ${error.message}")
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
        } catch (_: PorcupineException) {
        }
    }

    fun destroy() {
        stop()
        try {
            porcupineManager?.delete()
        } catch (_: PorcupineException) {
        }
        porcupineManager = null
    }
}
