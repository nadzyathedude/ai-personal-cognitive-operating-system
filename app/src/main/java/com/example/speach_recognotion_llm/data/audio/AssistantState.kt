package com.example.speach_recognotion_llm.data.audio

sealed class AssistantState {
    data object Idle : AssistantState()
    data object WakeDetected : AssistantState()
    data object Listening : AssistantState()
    data object Processing : AssistantState()
    data object Responding : AssistantState()
    data class Error(val message: String) : AssistantState()
}
