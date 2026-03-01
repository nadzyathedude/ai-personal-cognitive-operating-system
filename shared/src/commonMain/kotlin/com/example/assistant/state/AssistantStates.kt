package com.example.assistant.state

import com.example.assistant.models.BurnoutRisk
import com.example.assistant.models.EmotionalTimelineEntry
import com.example.assistant.models.Goal
import com.example.assistant.models.MemoryNode
import com.example.assistant.models.VerificationResult
import kotlinx.serialization.Serializable

sealed class WakeWordState {
    data object Idle : WakeWordState()
    data object Detected : WakeWordState()
    data object Listening : WakeWordState()
    data object Processing : WakeWordState()
    data object Responding : WakeWordState()
    data class Error(val message: String) : WakeWordState()
}

sealed class VerificationState {
    data object Idle : VerificationState()
    data object Recording : VerificationState()
    data object Verifying : VerificationState()
    data class Verified(val result: VerificationResult) : VerificationState()
    data class Rejected(val score: Float) : VerificationState()
    data class Error(val message: String) : VerificationState()
}

sealed class MoodJournalState {
    data object NotStarted : MoodJournalState()
    data object AskingInitial : MoodJournalState()
    data object ProcessingInitial : MoodJournalState()
    data class AskingFollowup(val index: Int, val total: Int) : MoodJournalState()
    data object ProcessingFollowup : MoodJournalState()
    data class Complete(val summary: String) : MoodJournalState()
    data class Error(val message: String) : MoodJournalState()
}

sealed class GoalEngineState {
    data object Idle : GoalEngineState()
    data object ExtractingIntent : GoalEngineState()
    data class GoalCreated(val goal: Goal) : GoalEngineState()
    data class CalendarSuggested(val goal: Goal) : GoalEngineState()
    data class Error(val message: String) : GoalEngineState()
}

sealed class MemoryQueryState {
    data object Idle : MemoryQueryState()
    data object Searching : MemoryQueryState()
    data class Results(val nodes: List<MemoryNode>) : MemoryQueryState()
    data class Error(val message: String) : MemoryQueryState()
}

sealed class AnalyticsState {
    data object Loading : AnalyticsState()
    data class Loaded(
        val timeline: List<EmotionalTimelineEntry>,
        val burnoutRisks: List<BurnoutRisk>
    ) : AnalyticsState()
    data class Error(val message: String) : AnalyticsState()
}
