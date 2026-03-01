package com.example.speach_recognotion_llm.data.model

import org.json.JSONObject

sealed class ClientMessage(val type: String) {
    class Authenticate(val token: String) : ClientMessage("authenticate")
    class AudioChunk(val data: String) : ClientMessage("audio_chunk")
    class EndAudio : ClientMessage("end_audio")
    class WakeTriggered : ClientMessage("wake_triggered")

    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type)
        when (this) {
            is Authenticate -> json.put("token", token)
            is AudioChunk -> json.put("data", data)
            is EndAudio -> {}
            is WakeTriggered -> {}
        }
        return json.toString()
    }
}

sealed class ServerMessage {
    data class Authenticated(val sessionId: String) : ServerMessage()
    data class Transcription(val text: String, val isFinal: Boolean) : ServerMessage()
    data class LlmToken(val token: String) : ServerMessage()
    data class LlmDone(val fullText: String) : ServerMessage()
    data class Error(val message: String, val code: String?) : ServerMessage()
    data object Pong : ServerMessage()
    data class Unknown(val raw: String) : ServerMessage()

    companion object {
        fun fromJson(text: String): ServerMessage {
            return try {
                val json = JSONObject(text)
                when (json.optString("type")) {
                    "authenticated" -> Authenticated(
                        sessionId = json.getString("sessionId")
                    )
                    "transcription" -> Transcription(
                        text = json.getString("text"),
                        isFinal = json.getBoolean("isFinal")
                    )
                    "llm_token" -> LlmToken(
                        token = json.getString("token")
                    )
                    "llm_done" -> LlmDone(
                        fullText = json.getString("fullText")
                    )
                    "error" -> Error(
                        message = json.getString("message"),
                        code = if (json.has("code")) json.getString("code") else null
                    )
                    "pong" -> Pong
                    else -> Unknown(text)
                }
            } catch (e: Exception) {
                Unknown(text)
            }
        }
    }
}
