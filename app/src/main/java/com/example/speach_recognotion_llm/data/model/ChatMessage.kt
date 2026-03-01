package com.example.speach_recognotion_llm.data.model

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}
