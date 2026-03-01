package com.example.speach_recognotion_llm.data.model

data class MoodSessionState(
    val sessionId: String = "",
    val phase: MoodPhase = MoodPhase.NOT_STARTED,
    val initialQuestion: String = "How was your day?",
    val initialAnswer: String = "",
    val emotion: EmotionResult? = null,
    val followupQuestions: List<String> = emptyList(),
    val followupAnswers: List<String> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val summary: String = "",
    val stressScore: Float? = null
)

enum class MoodPhase {
    NOT_STARTED,
    INITIAL_QUESTION,
    PROCESSING_INITIAL,
    FOLLOWUP_QUESTIONS,
    PROCESSING_FOLLOWUP,
    COMPLETE
}

data class EmotionResult(
    val sentiment: String,
    val primaryEmotion: String,
    val confidence: Float,
    val valence: Float,
    val arousal: Float
)

data class VerificationState(
    val isVerifying: Boolean = false,
    val isVerified: Boolean = false,
    val score: Float = 0f,
    val error: String? = null
)

data class WeeklyAnalytics(
    val dailyMoodScores: List<DailyScore> = emptyList(),
    val dailyStressScores: List<DailyScore> = emptyList(),
    val emotionDistribution: Map<String, Int> = emptyMap(),
    val weeklySummary: String = "",
    val avgValence: Float = 0f,
    val avgStress: Float = 0f,
    val dominantEmotion: String = "",
    val stressTrend: Float = 0f
)

data class DailyScore(
    val date: String,
    val value: Float
)
