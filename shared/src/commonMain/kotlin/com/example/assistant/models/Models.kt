package com.example.assistant.models

import kotlinx.serialization.Serializable

@Serializable
data class Goal(
    val goalId: String = "",
    val title: String = "",
    val description: String = "",
    val createdAt: String = "",
    val deadline: String = "",
    val status: GoalStatus = GoalStatus.ACTIVE,
    val relatedCalendarEventId: String? = null,
    val emotionalHistory: List<EmotionalEntry> = emptyList(),
    val stressHistory: List<StressEntry> = emptyList()
)

@Serializable
enum class GoalStatus {
    ACTIVE, PAUSED, COMPLETED, ABANDONED
}

@Serializable
data class EmotionalEntry(
    val timestamp: String,
    val emotion: String,
    val valence: Float,
    val arousal: Float
)

@Serializable
data class StressEntry(
    val timestamp: String,
    val stressScore: Float,
    val confidence: Float
)

@Serializable
data class MemoryNode(
    val id: String,
    val timestamp: String,
    val emotionalState: EmotionalState,
    val relatedGoalId: String? = null,
    val embeddingVector: List<Float> = emptyList(),
    val semanticTags: List<String> = emptyList(),
    val content: String = ""
)

@Serializable
data class EmotionalState(
    val sentiment: String = "neutral",
    val primaryEmotion: String = "neutral",
    val valence: Float = 0f,
    val arousal: Float = 0f,
    val stressScore: Float = 0f
)

@Serializable
data class EmotionalTimelineEntry(
    val timestamp: String,
    val valence: Float,
    val stressScore: Float,
    val primaryEmotion: String,
    val relatedGoal: String? = null
)

@Serializable
data class CalendarEvent(
    val eventId: String = "",
    val title: String = "",
    val description: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val goalId: String? = null
)

@Serializable
data class CalendarCommand(
    val action: CalendarAction,
    val event: CalendarEvent
)

@Serializable
enum class CalendarAction {
    CREATE, UPDATE, DELETE, READ
}

@Serializable
data class HrvData(
    val hrv: Float = 0f,
    val restingHr: Float = 0f,
    val sleepScore: Float = 0f,
    val timestamp: String = ""
)

@Serializable
data class CompositeStressIndex(
    val voiceStress: Float = 0f,
    val textStress: Float = 0f,
    val hrvStress: Float = 0f,
    val composite: Float = 0f,
    val timestamp: String = ""
)

@Serializable
data class BurnoutRisk(
    val goalId: String,
    val burnoutRisk: Float,
    val confidence: Float,
    val riskLevel: RiskLevel,
    val mainDrivers: List<String>,
    val projection7Day: Float? = null,
    val suggestion: String? = null
)

@Serializable
enum class RiskLevel {
    LOW, MODERATE, HIGH
}

@Serializable
data class VerificationResult(
    val verified: Boolean,
    val score: Float,
    val threshold: Float
)

@Serializable
data class SessionInfo(
    val sessionId: String,
    val userId: String,
    val verified: Boolean = false,
    val limitedMode: Boolean = false
)
