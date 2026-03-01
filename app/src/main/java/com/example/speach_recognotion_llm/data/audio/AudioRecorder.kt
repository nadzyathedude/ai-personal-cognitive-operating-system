package com.example.speach_recognotion_llm.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioRecorder(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_DURATION_MS = 200
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun start(
        scope: CoroutineScope,
        onChunk: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!hasPermission) {
            onError("Microphone permission not granted")
            return
        }

        if (isRecording) return

        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            SAMPLE_RATE * 2 * CHUNK_DURATION_MS / 1000
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: SecurityException) {
            onError("Microphone permission denied")
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            onError("Failed to initialize AudioRecord")
            audioRecord?.release()
            audioRecord = null
            return
        }

        isRecording = true
        audioRecord?.startRecording()

        val chunkSize = SAMPLE_RATE * 2 * CHUNK_DURATION_MS / 1000
        val buffer = ByteArray(chunkSize)

        recordingJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, chunkSize) ?: -1

                if (bytesRead > 0) {
                    val encoded = Base64.encodeToString(
                        buffer.copyOf(bytesRead),
                        Base64.NO_WRAP
                    )
                    onChunk(encoded)
                } else if (bytesRead < 0) {
                    isRecording = false
                    onError("AudioRecord read error: $bytesRead")
                }
            }
        }
    }

    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {}
        audioRecord?.release()
        audioRecord = null
    }
}
