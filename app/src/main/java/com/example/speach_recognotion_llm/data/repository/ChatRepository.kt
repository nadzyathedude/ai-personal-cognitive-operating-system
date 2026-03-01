package com.example.speach_recognotion_llm.data.repository

import com.example.speach_recognotion_llm.data.model.ClientMessage
import com.example.speach_recognotion_llm.data.model.ServerMessage
import com.example.speach_recognotion_llm.data.remote.ConnectionState
import com.example.speach_recognotion_llm.data.remote.WebSocketManager
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class ChatRepository(private val wsManager: WebSocketManager) {

    val connectionState: StateFlow<ConnectionState> = wsManager.connectionState
    val messages: SharedFlow<ServerMessage> = wsManager.messages

    fun connect(token: String) {
        wsManager.connect(token)
    }

    fun sendAudioChunk(base64Data: String) {
        wsManager.send(ClientMessage.AudioChunk(base64Data))
    }

    fun endAudio() {
        wsManager.send(ClientMessage.EndAudio())
    }

    fun disconnect() {
        wsManager.disconnect()
    }

    fun destroy() {
        wsManager.destroy()
    }
}
