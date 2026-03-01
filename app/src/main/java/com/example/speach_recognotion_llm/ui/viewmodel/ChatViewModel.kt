package com.example.speach_recognotion_llm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speach_recognotion_llm.BuildConfig
import com.example.speach_recognotion_llm.data.audio.AudioRecorder
import com.example.speach_recognotion_llm.data.model.ChatMessage
import com.example.speach_recognotion_llm.data.model.ServerMessage
import com.example.speach_recognotion_llm.data.remote.WebSocketManager
import com.example.speach_recognotion_llm.data.repository.ChatRepository
import com.example.speach_recognotion_llm.ui.state.ChatUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val wsManager = WebSocketManager(BuildConfig.WS_URL)
    private val repository = ChatRepository(wsManager)
    private val audioRecorder = AudioRecorder(application.applicationContext)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentAssistantMessageId: String? = null
    private val assistantBuffer = StringBuilder()

    // In production, obtain this from your auth flow (login screen, OAuth, etc.)
    private val authToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkZXYtdXNlci0xIiwiaWF0IjoxNzcyMzY5OTM2LCJleHAiOjE3NzI0NTYzMzZ9.7Q9ox-A0eZVgjVMoCuaYpI5aoXhZx_CdT5nhQAwqobM"

    init {
        observeConnection()
        observeMessages()
        repository.connect(authToken)
    }

    val hasMicPermission: Boolean
        get() = audioRecorder.hasPermission

    private fun observeConnection() {
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            repository.messages.collect { message ->
                handleServerMessage(message)
            }
        }
    }

    private fun handleServerMessage(message: ServerMessage) {
        when (message) {
            is ServerMessage.Authenticated -> {
                _uiState.update { it.copy(error = null) }
            }

            is ServerMessage.Transcription -> {
                if (message.isFinal) {
                    _uiState.update { current ->
                        current.copy(
                            messages = current.messages + ChatMessage(
                                role = ChatMessage.Role.USER,
                                content = message.text
                            ),
                            liveTranscription = "",
                            isProcessing = true
                        )
                    }
                } else {
                    _uiState.update { it.copy(liveTranscription = message.text) }
                }
            }

            is ServerMessage.LlmToken -> {
                assistantBuffer.append(message.token)
                val msgId = currentAssistantMessageId

                if (msgId == null) {
                    val newMsg = ChatMessage(
                        role = ChatMessage.Role.ASSISTANT,
                        content = assistantBuffer.toString(),
                        isStreaming = true
                    )
                    currentAssistantMessageId = newMsg.id
                    _uiState.update { current ->
                        current.copy(
                            messages = current.messages + newMsg,
                            isProcessing = false
                        )
                    }
                } else {
                    _uiState.update { current ->
                        current.copy(
                            messages = current.messages.map { msg ->
                                if (msg.id == msgId) {
                                    msg.copy(content = assistantBuffer.toString())
                                } else msg
                            }
                        )
                    }
                }
            }

            is ServerMessage.LlmDone -> {
                val msgId = currentAssistantMessageId
                if (msgId != null) {
                    _uiState.update { current ->
                        current.copy(
                            messages = current.messages.map { msg ->
                                if (msg.id == msgId) {
                                    msg.copy(
                                        content = message.fullText,
                                        isStreaming = false
                                    )
                                } else msg
                            },
                            isProcessing = false
                        )
                    }
                }
                currentAssistantMessageId = null
                assistantBuffer.clear()
            }

            is ServerMessage.Error -> {
                _uiState.update {
                    it.copy(
                        error = message.message,
                        isProcessing = false,
                        isRecording = false
                    )
                }
            }

            is ServerMessage.Pong -> {}
            is ServerMessage.Unknown -> {}
        }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        _uiState.update { it.copy(isRecording = true, error = null, liveTranscription = "") }

        audioRecorder.start(
            scope = viewModelScope,
            onChunk = { base64Chunk ->
                repository.sendAudioChunk(base64Chunk)
            },
            onError = { errorMsg ->
                _uiState.update {
                    it.copy(isRecording = false, error = errorMsg)
                }
            }
        )
    }

    private fun stopRecording() {
        audioRecorder.stop()
        repository.endAudio()
        _uiState.update { it.copy(isRecording = false, isProcessing = true) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stop()
        repository.destroy()
    }
}
