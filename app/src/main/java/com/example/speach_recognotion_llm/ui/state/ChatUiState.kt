package com.example.speach_recognotion_llm.ui.state

import com.example.speach_recognotion_llm.data.model.ChatMessage
import com.example.speach_recognotion_llm.data.remote.ConnectionState

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val liveTranscription: String = "",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val error: String? = null
)
