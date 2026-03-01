package com.example.assistant.engine

import com.example.assistant.models.CalendarAction
import com.example.assistant.models.CalendarCommand
import com.example.assistant.models.CalendarEvent
import com.example.assistant.models.EmotionalEntry
import com.example.assistant.models.EmotionalState
import com.example.assistant.models.Goal
import com.example.assistant.models.GoalStatus
import com.example.assistant.models.StressEntry
import com.example.assistant.repository.GoalRepository
import com.example.assistant.state.GoalEngineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GoalEngine(private val repository: GoalRepository) {

    private val _state = MutableStateFlow<GoalEngineState>(GoalEngineState.Idle)
    val state: StateFlow<GoalEngineState> = _state.asStateFlow()

    private val _goals = MutableStateFlow<List<Goal>>(emptyList())
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()

    suspend fun processVoiceIntent(transcription: String) {
        _state.value = GoalEngineState.ExtractingIntent

        try {
            val goal = repository.extractGoalFromText(transcription)
            _goals.value = _goals.value + goal
            _state.value = GoalEngineState.GoalCreated(goal)
        } catch (e: Exception) {
            _state.value = GoalEngineState.Error("Failed to create goal: ${e.message}")
        }
    }

    suspend fun suggestCalendarEvent(goal: Goal): CalendarCommand {
        _state.value = GoalEngineState.CalendarSuggested(goal)

        return CalendarCommand(
            action = CalendarAction.CREATE,
            event = CalendarEvent(
                title = goal.title,
                description = goal.description,
                startTime = goal.deadline,
                endTime = goal.deadline,
                goalId = goal.goalId
            )
        )
    }

    suspend fun linkEmotionToGoal(
        goalId: String,
        emotionalState: EmotionalState,
        timestamp: String
    ) {
        _goals.value = _goals.value.map { goal ->
            if (goal.goalId == goalId) {
                goal.copy(
                    emotionalHistory = goal.emotionalHistory + EmotionalEntry(
                        timestamp = timestamp,
                        emotion = emotionalState.primaryEmotion,
                        valence = emotionalState.valence,
                        arousal = emotionalState.arousal
                    )
                )
            } else goal
        }

        repository.updateGoal(_goals.value.first { it.goalId == goalId })
    }

    suspend fun linkStressToGoal(
        goalId: String,
        stressScore: Float,
        confidence: Float,
        timestamp: String
    ) {
        _goals.value = _goals.value.map { goal ->
            if (goal.goalId == goalId) {
                goal.copy(
                    stressHistory = goal.stressHistory + StressEntry(
                        timestamp = timestamp,
                        stressScore = stressScore,
                        confidence = confidence
                    )
                )
            } else goal
        }

        repository.updateGoal(_goals.value.first { it.goalId == goalId })
    }

    fun getGoalEmotionalStats(goalId: String): GoalEmotionalStats? {
        val goal = _goals.value.find { it.goalId == goalId } ?: return null

        val avgStress = if (goal.stressHistory.isNotEmpty()) {
            goal.stressHistory.map { it.stressScore }.average().toFloat()
        } else 0f

        val avgValence = if (goal.emotionalHistory.isNotEmpty()) {
            goal.emotionalHistory.map { it.valence }.average().toFloat()
        } else 0f

        val valences = goal.emotionalHistory.map { it.valence }
        val volatility = if (valences.size > 1) {
            val mean = valences.average()
            kotlin.math.sqrt(
                valences.map { (it - mean) * (it - mean) }.average()
            ).toFloat()
        } else 0f

        val motivationTrend = if (valences.size >= 2) {
            val recent = valences.takeLast(valences.size / 2).average()
            val earlier = valences.take(valences.size / 2).average()
            (recent - earlier).toFloat()
        } else 0f

        return GoalEmotionalStats(
            avgStress = avgStress,
            avgValence = avgValence,
            emotionalVolatility = volatility,
            motivationTrend = motivationTrend
        )
    }

    suspend fun updateGoalStatus(goalId: String, status: GoalStatus) {
        _goals.value = _goals.value.map { goal ->
            if (goal.goalId == goalId) goal.copy(status = status)
            else goal
        }

        val updated = _goals.value.find { it.goalId == goalId }
        if (updated != null) {
            repository.updateGoal(updated)
        }
    }

    suspend fun loadGoals() {
        _goals.value = repository.getAllGoals()
    }

    fun reset() {
        _state.value = GoalEngineState.Idle
    }
}

data class GoalEmotionalStats(
    val avgStress: Float,
    val avgValence: Float,
    val emotionalVolatility: Float,
    val motivationTrend: Float
)
