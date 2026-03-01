package com.example.speach_recognotion_llm.data.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AssistantStateManager {

    private val _state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    val currentState: AssistantState
        get() = _state.value

    fun onWakeWordDetected(): Boolean {
        return _state.compareAndSet(AssistantState.Idle, AssistantState.WakeDetected)
    }

    fun onReadyToListen() {
        _state.value = AssistantState.Listening
    }

    fun onListening() {
        _state.value = AssistantState.Listening
    }

    fun onProcessing() {
        _state.value = AssistantState.Processing
    }

    fun onResponding() {
        _state.value = AssistantState.Responding
    }

    fun onComplete() {
        _state.value = AssistantState.Idle
    }

    fun onError(message: String) {
        _state.value = AssistantState.Error(message)
    }

    fun reset() {
        _state.value = AssistantState.Idle
    }
}
