package com.example.assistant.repository

import com.example.assistant.engine.FollowupResult
import com.example.assistant.engine.MoodResult
import com.example.assistant.models.BurnoutRisk
import com.example.assistant.models.EmotionalState
import com.example.assistant.models.EmotionalTimelineEntry
import com.example.assistant.models.Goal
import com.example.assistant.models.VerificationResult

interface GoalRepository {
    suspend fun extractGoalFromText(text: String): Goal
    suspend fun updateGoal(goal: Goal)
    suspend fun getAllGoals(): List<Goal>
    suspend fun getGoalById(goalId: String): Goal?
    suspend fun deleteGoal(goalId: String)
}

interface MoodRepository {
    suspend fun startSession(): Pair<String, String>
    suspend fun submitInitialAnswer(sessionId: String, answer: String): MoodResult
    suspend fun submitFollowupAnswer(sessionId: String, index: Int, answer: String): FollowupResult
}

interface AnalyticsRepository {
    suspend fun getWeeklyTimeline(userId: String): List<EmotionalTimelineEntry>
    suspend fun getMonthlyTimeline(userId: String): List<EmotionalTimelineEntry>
    suspend fun getBurnoutRisk(goalId: String): BurnoutRisk
    suspend fun getAllBurnoutRisks(userId: String): List<BurnoutRisk>
}

interface VerificationRepository {
    suspend fun startVerification()
    suspend fun sendAudioChunk(base64Data: String)
    suspend fun endVerification(): VerificationResult
}

interface CalendarRepository {
    suspend fun createEvent(title: String, startTime: String, endTime: String, goalId: String?): String
    suspend fun updateEvent(eventId: String, title: String?, startTime: String?, endTime: String?)
    suspend fun deleteEvent(eventId: String)
    suspend fun getEvents(startDate: String, endDate: String): List<com.example.assistant.models.CalendarEvent>
}
