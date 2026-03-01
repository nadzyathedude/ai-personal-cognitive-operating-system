package com.example.assistant.engine

import com.example.assistant.models.EmotionalState
import com.example.assistant.repository.MoodRepository
import com.example.assistant.state.MoodJournalState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MoodEngine(private val repository: MoodRepository) {

    private val _state = MutableStateFlow<MoodJournalState>(MoodJournalState.NotStarted)
    val state: StateFlow<MoodJournalState> = _state.asStateFlow()

    private var currentSessionId: String = ""
    private var followupQuestions: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var detectedEmotion: EmotionalState? = null

    suspend fun startSession() {
        try {
            val (sessionId, question) = repository.startSession()
            currentSessionId = sessionId
            _state.value = MoodJournalState.AskingInitial
        } catch (e: Exception) {
            _state.value = MoodJournalState.Error(e.message ?: "Failed to start session")
        }
    }

    suspend fun submitInitialAnswer(answer: String) {
        _state.value = MoodJournalState.ProcessingInitial

        try {
            val result = repository.submitInitialAnswer(currentSessionId, answer)
            detectedEmotion = result.emotion
            followupQuestions = result.questions
            currentIndex = 0
            _state.value = MoodJournalState.AskingFollowup(0, followupQuestions.size)
        } catch (e: Exception) {
            _state.value = MoodJournalState.Error(e.message ?: "Processing failed")
        }
    }

    suspend fun submitFollowupAnswer(answer: String) {
        _state.value = MoodJournalState.ProcessingFollowup

        try {
            val result = repository.submitFollowupAnswer(
                currentSessionId, currentIndex, answer
            )
            if (result.complete) {
                _state.value = MoodJournalState.Complete(result.summary)
            } else {
                currentIndex++
                _state.value = MoodJournalState.AskingFollowup(
                    currentIndex, followupQuestions.size
                )
            }
        } catch (e: Exception) {
            _state.value = MoodJournalState.Error(e.message ?: "Failed")
        }
    }

    fun getCurrentQuestion(): String? {
        return when (val s = _state.value) {
            is MoodJournalState.AskingFollowup -> followupQuestions.getOrNull(s.index)
            else -> null
        }
    }

    fun getDetectedEmotion(): EmotionalState? = detectedEmotion

    fun reset() {
        _state.value = MoodJournalState.NotStarted
        currentSessionId = ""
        followupQuestions = emptyList()
        currentIndex = 0
        detectedEmotion = null
    }
}

data class MoodResult(
    val emotion: EmotionalState,
    val questions: List<String>
)

data class FollowupResult(
    val complete: Boolean,
    val summary: String
)
