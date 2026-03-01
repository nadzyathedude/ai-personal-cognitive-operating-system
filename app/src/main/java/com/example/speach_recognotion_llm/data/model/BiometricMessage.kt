package com.example.speach_recognotion_llm.data.model

import org.json.JSONObject

sealed class BiometricClientMessage(val type: String) {
    class Authenticate(val token: String) : BiometricClientMessage("authenticate")
    class VerificationStart : BiometricClientMessage("voice_verification_start")
    class VerificationAudio(val data: String) : BiometricClientMessage("voice_verification_audio")
    class VerificationEnd : BiometricClientMessage("voice_verification_end")
    class StressAnalysisStart : BiometricClientMessage("stress_analysis_start")
    class StressAnalysisAudio(val data: String) : BiometricClientMessage("stress_analysis_audio")
    class StressAnalysisEnd(
        val transcription: String = "",
        val sessionId: String = ""
    ) : BiometricClientMessage("stress_analysis_end")

    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type)
        when (this) {
            is Authenticate -> json.put("token", token)
            is VerificationAudio -> json.put("data", data)
            is StressAnalysisAudio -> json.put("data", data)
            is StressAnalysisEnd -> {
                json.put("transcription", transcription)
                json.put("session_id", sessionId)
            }
            else -> {}
        }
        return json.toString()
    }
}

sealed class BiometricServerMessage {
    data class VerificationResult(
        val verified: Boolean,
        val score: Float,
        val threshold: Float
    ) : BiometricServerMessage()

    data class VerificationError(val message: String) : BiometricServerMessage()

    data class StressResult(
        val stressScore: Float,
        val stressConfidence: Float,
        val primaryEmotion: String?,
        val sentiment: String?,
        val combinedStress: Float?
    ) : BiometricServerMessage()

    data class StressError(val message: String) : BiometricServerMessage()
    data class Error(val message: String) : BiometricServerMessage()
    data class Unknown(val raw: String) : BiometricServerMessage()

    companion object {
        fun fromJson(text: String): BiometricServerMessage {
            return try {
                val json = JSONObject(text)
                when (json.optString("type")) {
                    "verification_result" -> {
                        if (json.has("verified")) {
                            VerificationResult(
                                verified = json.getBoolean("verified"),
                                score = json.optDouble("score", 0.0).toFloat(),
                                threshold = json.optDouble("threshold", 0.65).toFloat()
                            )
                        } else {
                            Unknown(text)
                        }
                    }
                    "verification_error" -> VerificationError(
                        message = json.optString("message", "Verification failed")
                    )
                    "stress_result" -> {
                        val acoustic = json.optJSONObject("acoustic_stress")
                        val textEmotion = json.optJSONObject("text_emotion")
                        val combined = json.optJSONObject("combined")
                        StressResult(
                            stressScore = acoustic?.optDouble("stress_score", 0.0)?.toFloat() ?: 0f,
                            stressConfidence = acoustic?.optDouble("confidence", 0.0)?.toFloat() ?: 0f,
                            primaryEmotion = textEmotion?.optString("primary_emotion"),
                            sentiment = textEmotion?.optString("sentiment"),
                            combinedStress = combined?.optDouble("combined_stress")?.toFloat()
                        )
                    }
                    "stress_error" -> StressError(
                        message = json.optString("message", "Stress analysis failed")
                    )
                    "error" -> Error(
                        message = json.optString("message", "Unknown error")
                    )
                    else -> Unknown(text)
                }
            } catch (e: Exception) {
                Unknown(text)
            }
        }
    }
}
