package com.example.speach_recognotion_llm.data.repository

import com.example.speach_recognotion_llm.BuildConfig
import com.example.speach_recognotion_llm.data.model.DailyScore
import com.example.speach_recognotion_llm.data.model.EmotionResult
import com.example.speach_recognotion_llm.data.model.ToneAnalysisResult
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

    data class VoiceAnswerResult(
        val transcription: String,
        val emotion: EmotionResult,
        val followupQuestions: List<String>
    )

    data class VoiceFollowupResult(
        val transcription: String,
        val complete: Boolean,
        val summary: String?
    )

    suspend fun submitInitialAnswerVoice(
        sessionId: String,
        audioBytes: ByteArray
    ): VoiceAnswerResult = withContext(Dispatchers.IO) {
        val audioBody = audioBytes.toRequestBody("audio/wav".toMediaType())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", "recording.wav", audioBody)
            .addFormDataPart("session_id", sessionId)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/mood/answer-voice")
            .post(multipartBody)
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        if (!response.isSuccessful) {
            throw Exception("Voice answer failed: ${response.code} $body")
        }
        val json = JSONObject(body)

        val transcription = json.getString("transcription")
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

        VoiceAnswerResult(transcription, emotion, questions)
    }

    suspend fun submitFollowupAnswerVoice(
        sessionId: String,
        questionIndex: Int,
        audioBytes: ByteArray
    ): VoiceFollowupResult = withContext(Dispatchers.IO) {
        val audioBody = audioBytes.toRequestBody("audio/wav".toMediaType())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", "recording.wav", audioBody)
            .addFormDataPart("session_id", sessionId)
            .addFormDataPart("question_index", questionIndex.toString())
            .build()

        val request = Request.Builder()
            .url("$baseUrl/mood/followup-voice")
            .post(multipartBody)
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        if (!response.isSuccessful) {
            throw Exception("Voice followup failed: ${response.code} $body")
        }
        val json = JSONObject(body)

        VoiceFollowupResult(
            transcription = json.getString("transcription"),
            complete = json.getBoolean("complete"),
            summary = json.optString("summary", null)
        )
    }

    suspend fun submitToneAnalysis(
        audioBytes: ByteArray,
        transcription: String,
        sessionId: String
    ): ToneAnalysisResult = withContext(Dispatchers.IO) {
        val audioBody = audioBytes.toRequestBody("audio/wav".toMediaType())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", "recording.wav", audioBody)
            .addFormDataPart("transcription", transcription)
            .addFormDataPart("session_id", sessionId)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/tone/analyze")
            .post(multipartBody)
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")

        val acoustic = json.optJSONObject("acoustic")
        val fusion = json.optJSONObject("fusion")

        ToneAnalysisResult(
            acousticEmotion = acoustic?.optString("detected_emotion", "") ?: "",
            toneDescriptor = acoustic?.optString("tone_descriptor", "") ?: "",
            vocalStressScore = acoustic?.optDouble("vocal_stress_score", 0.0)?.toFloat() ?: 0f,
            acousticConfidence = acoustic?.optDouble("confidence", 0.0)?.toFloat() ?: 0f,
            fusionEmotion = fusion?.optString("primary_emotion"),
            fusionValence = fusion?.optDouble("final_valence")?.toFloat(),
            fusionArousal = fusion?.optDouble("arousal_level")?.toFloat(),
            fusionStressIndex = fusion?.optDouble("stress_index")?.toFloat(),
            fusionConfidence = fusion?.optDouble("fusion_confidence")?.toFloat(),
            compositeTone = fusion?.optString("composite_tone"),
            mismatchDetected = fusion?.optBoolean("mismatch_detected", false) ?: false,
            mismatchType = fusion?.optString("mismatch_type"),
            reflectivePrompt = fusion?.optString("reflective_prompt")
        )
    }

    // --- Garmin API ---

    data class GarminStatus(val connected: Boolean, val connectedAt: String?, val lastSync: String?)

    data class GarminHrvResult(
        val connected: Boolean,
        val hrv: Float,
        val restingHr: Float,
        val sleepScore: Float,
        val stressLevel: Float,
        val date: String
    )

    data class GarminDailyEntry(
        val date: String,
        val hrv: Float,
        val restingHr: Float,
        val sleepScore: Float,
        val stressLevel: Float
    )

    suspend fun getGarminAuthUrl(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/garmin/connect")
            .get()
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        if (!response.isSuccessful) {
            throw Exception(JSONObject(body).optString("detail", "Failed to connect"))
        }
        JSONObject(body).getString("authorization_url")
    }

    suspend fun getGarminStatus(): GarminStatus = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/garmin/status")
            .get()
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")
        GarminStatus(
            connected = json.optBoolean("connected", false),
            connectedAt = json.optString("connected_at", null),
            lastSync = json.optString("last_sync", null)
        )
    }

    suspend fun syncGarminData(): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/garmin/sync")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        if (!response.isSuccessful) {
            throw Exception(JSONObject(body).optString("detail", "Sync failed"))
        }
        JSONObject(body).optInt("synced", 0)
    }

    suspend fun getGarminHrv(): GarminHrvResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/garmin/hrv")
            .get()
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")
        val connected = json.optBoolean("connected", false)
        val data = json.optJSONObject("data") ?: return@withContext if (connected) {
            GarminHrvResult(connected = true, 0f, 0f, 0f, 0f, "")
        } else null

        GarminHrvResult(
            connected = true,
            hrv = data.optDouble("hrv", 0.0).toFloat(),
            restingHr = data.optDouble("resting_hr", 0.0).toFloat(),
            sleepScore = data.optDouble("sleep_score", 0.0).toFloat(),
            stressLevel = data.optDouble("stress_level", 0.0).toFloat(),
            date = data.optString("date", "")
        )
    }

    suspend fun getGarminDaily(days: Int = 7): List<GarminDailyEntry> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/garmin/daily?days=$days")
            .get()
            .addHeader("Authorization", "Bearer $authToken")
            .build()

        val response = client.newCall(request).execute()
        val json = JSONObject(response.body?.string() ?: "{}")
        val array = json.optJSONArray("daily") ?: return@withContext emptyList()

        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            GarminDailyEntry(
                date = item.optString("date", ""),
                hrv = item.optDouble("hrv", 0.0).toFloat(),
                restingHr = item.optDouble("resting_hr", 0.0).toFloat(),
                sleepScore = item.optDouble("sleep_score", 0.0).toFloat(),
                stressLevel = item.optDouble("stress_level", 0.0).toFloat()
            )
        }
    }

    suspend fun disconnectGarmin() = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/garmin/disconnect")
            .delete()
            .addHeader("Authorization", "Bearer $authToken")
            .build()
        client.newCall(request).execute()
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
