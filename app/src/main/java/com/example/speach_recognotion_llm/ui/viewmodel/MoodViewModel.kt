package com.example.speach_recognotion_llm.ui.viewmodel

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speach_recognotion_llm.data.audio.AssistantStateManager
import com.example.speach_recognotion_llm.data.model.MoodPhase
import com.example.speach_recognotion_llm.data.model.MoodSessionState
import com.example.speach_recognotion_llm.data.model.WeeklyAnalytics
import com.example.speach_recognotion_llm.data.repository.BiometricRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MoodViewModel(application: Application) : AndroidViewModel(application) {

    private val authToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkZXYtdXNlci0xIiwiaWF0IjoxNzcyMzY5OTM2LCJleHAiOjE3NzI0NTYzMzZ9.7Q9ox-A0eZVgjVMoCuaYpI5aoXhZx_CdT5nhQAwqobM"
    private val repository = BiometricRepository(authToken)

    private val _moodState = MutableStateFlow(MoodSessionState())
    val moodState: StateFlow<MoodSessionState> = _moodState.asStateFlow()

    private val _analytics = MutableStateFlow<WeeklyAnalytics?>(null)
    val analytics: StateFlow<WeeklyAnalytics?> = _analytics.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val audioBuffer = ByteArrayOutputStream()

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    fun startMoodSession() {
        viewModelScope.launch {
            try {
                val (sessionId, question) = repository.startMoodSession()
                _moodState.value = MoodSessionState(
                    sessionId = sessionId,
                    phase = MoodPhase.INITIAL_QUESTION,
                    initialQuestion = question
                )
            } catch (e: Exception) {
                _error.value = "Failed to start mood session: ${e.message}"
            }
        }
    }

    fun startVoiceRecording() {
        audioBuffer.reset()
        _moodState.update { it.copy(isRecordingVoice = true) }

        // Signal Porcupine to release the mic
        AssistantStateManager.onListening()

        viewModelScope.launch {
            // Wait for Porcupine to fully release the mic
            delay(600)
            initAudioRecording()
        }
    }

    private fun initAudioRecording() {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT),
            SAMPLE_RATE * 2 * 200 / 1000
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
            _moodState.update { it.copy(isRecordingVoice = false) }
            AssistantStateManager.reset()
            _error.value = "Microphone permission denied"
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            _moodState.update { it.copy(isRecordingVoice = false) }
            AssistantStateManager.reset()
            _error.value = "Failed to initialize audio recorder"
            return
        }

        audioRecord?.startRecording()
        val buffer = ByteArray(bufferSize)

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    synchronized(audioBuffer) {
                        audioBuffer.write(buffer, 0, bytesRead)
                    }
                } else if (bytesRead < 0) {
                    break
                }
            }
        }
    }

    fun stopVoiceRecording(): ByteArray {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {}
        audioRecord?.release()
        audioRecord = null

        val pcmData: ByteArray
        synchronized(audioBuffer) {
            pcmData = audioBuffer.toByteArray()
            audioBuffer.reset()
        }

        _moodState.update { it.copy(isRecordingVoice = false, lastAudioBytes = wrapWithWavHeader(pcmData)) }
        return _moodState.value.lastAudioBytes ?: ByteArray(0)
    }

    fun submitVoiceAnswer() {
        val state = _moodState.value
        val audioBytes = state.lastAudioBytes ?: return
        if (audioBytes.size < 1000) return

        when (state.phase) {
            MoodPhase.INITIAL_QUESTION -> submitVoiceInitialAnswer(audioBytes)
            MoodPhase.FOLLOWUP_QUESTIONS -> submitVoiceFollowupAnswer(audioBytes)
            else -> return
        }
    }

    private fun submitVoiceInitialAnswer(audioBytes: ByteArray) {
        val sessionId = _moodState.value.sessionId
        if (sessionId.isEmpty()) return

        _moodState.update { it.copy(phase = MoodPhase.PROCESSING_INITIAL) }

        viewModelScope.launch {
            try {
                val result = repository.submitInitialAnswerVoice(sessionId, audioBytes)

                // Also run tone analysis on the audio
                var toneResult = _moodState.value.toneResult
                try {
                    toneResult = repository.submitToneAnalysis(audioBytes, result.transcription, sessionId)
                } catch (_: Exception) {}

                _moodState.update {
                    it.copy(
                        phase = MoodPhase.FOLLOWUP_QUESTIONS,
                        initialAnswer = result.transcription,
                        emotion = result.emotion,
                        toneResult = toneResult,
                        followupQuestions = result.followupQuestions,
                        currentQuestionIndex = 0,
                        lastAudioBytes = null
                    )
                }
                AssistantStateManager.reset()
            } catch (e: Exception) {
                _error.value = "Failed to process voice answer: ${e.message}"
                _moodState.update { it.copy(phase = MoodPhase.INITIAL_QUESTION, lastAudioBytes = null) }
                AssistantStateManager.reset()
            }
        }
    }

    private fun submitVoiceFollowupAnswer(audioBytes: ByteArray) {
        val state = _moodState.value
        if (state.sessionId.isEmpty()) return

        val index = state.currentQuestionIndex

        _moodState.update { it.copy(phase = MoodPhase.PROCESSING_FOLLOWUP) }

        viewModelScope.launch {
            try {
                // Tone analysis
                try {
                    val toneResult = repository.submitToneAnalysis(audioBytes, "", state.sessionId)
                    _moodState.update { it.copy(toneResult = toneResult) }
                } catch (_: Exception) {}

                val result = repository.submitFollowupAnswerVoice(
                    state.sessionId, index, audioBytes
                )

                _moodState.update { current ->
                    val answers = current.followupAnswers + result.transcription
                    if (result.complete && result.summary != null) {
                        current.copy(
                            phase = MoodPhase.COMPLETE,
                            followupAnswers = answers,
                            summary = result.summary,
                            lastAudioBytes = null
                        )
                    } else {
                        current.copy(
                            phase = MoodPhase.FOLLOWUP_QUESTIONS,
                            followupAnswers = answers,
                            currentQuestionIndex = current.currentQuestionIndex + 1,
                            lastAudioBytes = null
                        )
                    }
                }
                AssistantStateManager.reset()
            } catch (e: Exception) {
                _error.value = "Failed to process voice follow-up: ${e.message}"
                _moodState.update { it.copy(phase = MoodPhase.FOLLOWUP_QUESTIONS, lastAudioBytes = null) }
                AssistantStateManager.reset()
            }
        }
    }

    fun submitInitialAnswer(answer: String) {
        val sessionId = _moodState.value.sessionId
        if (sessionId.isEmpty()) return

        val audioBytes = _moodState.value.lastAudioBytes

        _moodState.update { it.copy(phase = MoodPhase.PROCESSING_INITIAL, initialAnswer = answer) }

        viewModelScope.launch {
            try {
                val (emotion, questions) = repository.submitInitialAnswer(sessionId, answer)

                // Send audio for tone analysis if available
                var toneResult = _moodState.value.toneResult
                if (audioBytes != null && audioBytes.size > 1000) {
                    try {
                        toneResult = repository.submitToneAnalysis(audioBytes, answer, sessionId)
                    } catch (e: Exception) {
                        // Tone analysis is optional, don't block the flow
                    }
                }

                _moodState.update {
                    it.copy(
                        phase = MoodPhase.FOLLOWUP_QUESTIONS,
                        emotion = emotion,
                        toneResult = toneResult,
                        followupQuestions = questions,
                        currentQuestionIndex = 0,
                        lastAudioBytes = null
                    )
                }
            } catch (e: Exception) {
                _error.value = "Failed to process answer: ${e.message}"
                _moodState.update { it.copy(phase = MoodPhase.INITIAL_QUESTION) }
            }
        }
    }

    fun submitFollowupAnswer(answer: String) {
        val state = _moodState.value
        if (state.sessionId.isEmpty()) return

        val index = state.currentQuestionIndex
        val audioBytes = state.lastAudioBytes

        _moodState.update { it.copy(phase = MoodPhase.PROCESSING_FOLLOWUP) }

        viewModelScope.launch {
            try {
                // Send audio for tone analysis if available
                if (audioBytes != null && audioBytes.size > 1000) {
                    try {
                        val toneResult = repository.submitToneAnalysis(audioBytes, answer, state.sessionId)
                        _moodState.update { it.copy(toneResult = toneResult) }
                    } catch (_: Exception) {}
                }

                val (complete, summary) = repository.submitFollowupAnswer(
                    state.sessionId, index, answer
                )

                _moodState.update { current ->
                    val answers = current.followupAnswers + answer
                    if (complete && summary != null) {
                        current.copy(
                            phase = MoodPhase.COMPLETE,
                            followupAnswers = answers,
                            summary = summary,
                            lastAudioBytes = null
                        )
                    } else {
                        current.copy(
                            phase = MoodPhase.FOLLOWUP_QUESTIONS,
                            followupAnswers = answers,
                            currentQuestionIndex = current.currentQuestionIndex + 1,
                            lastAudioBytes = null
                        )
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to process follow-up: ${e.message}"
                _moodState.update { it.copy(phase = MoodPhase.FOLLOWUP_QUESTIONS) }
            }
        }
    }

    fun loadWeeklyAnalytics() {
        viewModelScope.launch {
            try {
                _analytics.value = repository.getWeeklyAnalytics()
            } catch (e: Exception) {
                _error.value = "Failed to load analytics: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun resetMoodSession() {
        _moodState.value = MoodSessionState()
    }

    private fun wrapWithWavHeader(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8
        val header = ByteArray(44)

        // RIFF header
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeInt(header, 4, totalDataLen)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeInt(header, 16, 16) // chunk size
        writeShort(header, 20, 1) // PCM format
        writeShort(header, 22, 1) // mono
        writeInt(header, 24, SAMPLE_RATE)
        writeInt(header, 28, byteRate)
        writeShort(header, 32, 2) // block align
        writeShort(header, 34, 16) // bits per sample

        // data chunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeInt(header, 40, pcmData.size)

        return header + pcmData
    }

    private fun writeInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xff).toByte()
        data[offset + 1] = ((value shr 8) and 0xff).toByte()
        data[offset + 2] = ((value shr 16) and 0xff).toByte()
        data[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun writeShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xff).toByte()
        data[offset + 1] = ((value shr 8) and 0xff).toByte()
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        audioRecord?.release()
    }
}
