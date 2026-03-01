package com.example.speach_recognotion_llm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speach_recognotion_llm.data.model.MoodPhase
import com.example.speach_recognotion_llm.data.model.MoodSessionState
import com.example.speach_recognotion_llm.data.model.WeeklyAnalytics
import com.example.speach_recognotion_llm.data.repository.BiometricRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MoodViewModel(application: Application) : AndroidViewModel(application) {

    private val authToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkZXYtdXNlci0xIiwiaWF0IjoxNzcyMzY5OTM2LCJleHAiOjE3NzI0NTYzMzZ9.7Q9ox-A0eZVgjVMoCuaYpI5aoXhZx_CdT5nhQAwqobM"
    private val repository = BiometricRepository(authToken)

    private val _moodState = MutableStateFlow(MoodSessionState())
    val moodState: StateFlow<MoodSessionState> = _moodState.asStateFlow()

    private val _analytics = MutableStateFlow<WeeklyAnalytics?>(null)
    val analytics: StateFlow<WeeklyAnalytics?> = _analytics.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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

    fun submitInitialAnswer(answer: String) {
        val sessionId = _moodState.value.sessionId
        if (sessionId.isEmpty()) return

        _moodState.update { it.copy(phase = MoodPhase.PROCESSING_INITIAL, initialAnswer = answer) }

        viewModelScope.launch {
            try {
                val (emotion, questions) = repository.submitInitialAnswer(sessionId, answer)
                _moodState.update {
                    it.copy(
                        phase = MoodPhase.FOLLOWUP_QUESTIONS,
                        emotion = emotion,
                        followupQuestions = questions,
                        currentQuestionIndex = 0
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
        _moodState.update { it.copy(phase = MoodPhase.PROCESSING_FOLLOWUP) }

        viewModelScope.launch {
            try {
                val (complete, summary) = repository.submitFollowupAnswer(
                    state.sessionId, index, answer
                )

                _moodState.update { current ->
                    val answers = current.followupAnswers + answer
                    if (complete && summary != null) {
                        current.copy(
                            phase = MoodPhase.COMPLETE,
                            followupAnswers = answers,
                            summary = summary
                        )
                    } else {
                        current.copy(
                            phase = MoodPhase.FOLLOWUP_QUESTIONS,
                            followupAnswers = answers,
                            currentQuestionIndex = current.currentQuestionIndex + 1
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
}
