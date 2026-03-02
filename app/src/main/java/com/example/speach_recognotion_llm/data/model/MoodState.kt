package com.example.speach_recognotion_llm.data.model

data class MoodSessionState(
    val sessionId: String = "",
    val phase: MoodPhase = MoodPhase.NOT_STARTED,
    val initialQuestion: String = "How was your day?",
    val initialAnswer: String = "",
    val emotion: EmotionResult? = null,
    val toneResult: ToneAnalysisResult? = null,
    val followupQuestions: List<String> = emptyList(),
    val followupAnswers: List<String> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val summary: String = "",
    val stressScore: Float? = null,
    val isRecordingVoice: Boolean = false,
    val lastAudioBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MoodSessionState) return false
        return sessionId == other.sessionId && phase == other.phase &&
                initialQuestion == other.initialQuestion && initialAnswer == other.initialAnswer &&
                emotion == other.emotion && toneResult == other.toneResult &&
                followupQuestions == other.followupQuestions && followupAnswers == other.followupAnswers &&
                currentQuestionIndex == other.currentQuestionIndex && summary == other.summary &&
                stressScore == other.stressScore && isRecordingVoice == other.isRecordingVoice &&
                lastAudioBytes.contentEquals(other.lastAudioBytes)
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + phase.hashCode()
        result = 31 * result + (lastAudioBytes?.contentHashCode() ?: 0)
        return result
    }
}

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

data class ToneAnalysisResult(
    val acousticEmotion: String = "",
    val toneDescriptor: String = "",
    val vocalStressScore: Float = 0f,
    val acousticConfidence: Float = 0f,
    val fusionEmotion: String? = null,
    val fusionValence: Float? = null,
    val fusionArousal: Float? = null,
    val fusionStressIndex: Float? = null,
    val fusionConfidence: Float? = null,
    val compositeTone: String? = null,
    val mismatchDetected: Boolean = false,
    val mismatchType: String? = null,
    val reflectivePrompt: String? = null
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
