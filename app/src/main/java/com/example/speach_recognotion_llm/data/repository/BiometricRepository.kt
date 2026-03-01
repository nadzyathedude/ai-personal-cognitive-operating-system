package com.example.speach_recognotion_llm.data.repository

import com.example.speach_recognotion_llm.BuildConfig
import com.example.speach_recognotion_llm.data.model.DailyScore
import com.example.speach_recognotion_llm.data.model.EmotionResult
import com.example.speach_recognotion_llm.data.model.WeeklyAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BiometricRepository(private val authToken: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl = BuildConfig.WS_URL
        .replace("ws://", "http://")
        .replace("wss://", "https://")
        .replace(":3001", ":8000")

    suspend fun startMoodSession(): Pair<String, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/mood/start")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")
        Pair(json.getString("session_id"), json.getString("question"))
    }

    suspend fun submitInitialAnswer(
        sessionId: String,
        answer: String
    ): Pair<EmotionResult, List<String>> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("session_id", sessionId)
            put("answer", answer)
        }
        val request = Request.Builder()
            .url("$baseUrl/mood/answer")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")
        val emotionJson = json.getJSONObject("emotion")
        val emotion = EmotionResult(
            sentiment = emotionJson.getString("sentiment"),
            primaryEmotion = emotionJson.getString("primary_emotion"),
            confidence = emotionJson.getDouble("confidence").toFloat(),
            valence = emotionJson.getDouble("valence").toFloat(),
            arousal = emotionJson.getDouble("arousal").toFloat()
        )

        val questionsArray = json.getJSONArray("followup_questions")
        val questions = (0 until questionsArray.length()).map { questionsArray.getString(it) }

        Pair(emotion, questions)
    }

    suspend fun submitFollowupAnswer(
        sessionId: String,
        questionIndex: Int,
        answer: String
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("session_id", sessionId)
            put("question_index", questionIndex)
            put("answer", answer)
        }
        val request = Request.Builder()
            .url("$baseUrl/mood/followup")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")
        Pair(json.getBoolean("complete"), json.optString("summary", null))
    }

    suspend fun getWeeklyAnalytics(): WeeklyAnalytics = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/analytics/weekly")
            .get()
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")

        val moodArray = json.optJSONArray("daily_mood_scores")
        val stressArray = json.optJSONArray("daily_stress_scores")
        val emotionDist = json.optJSONObject("emotion_distribution")
        val statsJson = json.optJSONObject("stats")

        val dailyMood = mutableListOf<DailyScore>()
        if (moodArray != null) {
            for (i in 0 until moodArray.length()) {
                val item = moodArray.getJSONObject(i)
                dailyMood.add(DailyScore(item.getString("date"), item.getDouble("valence").toFloat()))
            }
        }

        val dailyStress = mutableListOf<DailyScore>()
        if (stressArray != null) {
            for (i in 0 until stressArray.length()) {
                val item = stressArray.getJSONObject(i)
                dailyStress.add(DailyScore(item.getString("date"), item.getDouble("stress_score").toFloat()))
            }
        }

        val emotions = mutableMapOf<String, Int>()
        if (emotionDist != null) {
            for (key in emotionDist.keys()) {
                emotions[key] = emotionDist.getInt(key)
            }
        }

        WeeklyAnalytics(
            dailyMoodScores = dailyMood,
            dailyStressScores = dailyStress,
            emotionDistribution = emotions,
            weeklySummary = json.optString("weekly_summary", ""),
            avgValence = statsJson?.optDouble("avg_valence", 0.0)?.toFloat() ?: 0f,
            avgStress = statsJson?.optDouble("avg_stress", 0.0)?.toFloat() ?: 0f,
            dominantEmotion = statsJson?.optString("dominant_emotion", "") ?: "",
            stressTrend = statsJson?.optDouble("stress_trend", 0.0)?.toFloat() ?: 0f
        )
    }
}
