package com.example.assistant.integration

import com.example.assistant.models.CompositeStressIndex
import com.example.assistant.models.HrvData

interface GarminBridge {
    suspend fun isConnected(): Boolean
    suspend fun requestPermission(): Boolean
    suspend fun fetchLatestHrv(): HrvData?
    suspend fun fetchDailyHrv(): List<HrvData>
}

class GarminIntegrationManager(private val bridge: GarminBridge) {

    suspend fun getLatestHrv(): HrvData? {
        if (!bridge.isConnected()) return null
        return bridge.fetchLatestHrv()
    }

    suspend fun computeCompositeStress(
        voiceStress: Float,
        textStress: Float,
        timestamp: String,
        voiceWeight: Float = 0.4f,
        textWeight: Float = 0.3f,
        hrvWeight: Float = 0.3f
    ): CompositeStressIndex {
        val hrvData = bridge.fetchLatestHrv()
        val hrvStress = if (hrvData != null) {
            computeHrvStress(hrvData)
        } else {
            0f
        }

        val effectiveHrvWeight = if (hrvData != null) hrvWeight else 0f
        val totalWeight = voiceWeight + textWeight + effectiveHrvWeight
        val composite = if (totalWeight > 0) {
            (voiceStress * voiceWeight + textStress * textWeight + hrvStress * effectiveHrvWeight) / totalWeight
        } else 0f

        return CompositeStressIndex(
            voiceStress = voiceStress,
            textStress = textStress,
            hrvStress = hrvStress,
            composite = composite.coerceIn(0f, 1f),
            timestamp = timestamp
        )
    }

    private fun computeHrvStress(hrv: HrvData): Float {
        val hrvScore = when {
            hrv.hrv > 60 -> 0.2f
            hrv.hrv > 40 -> 0.5f
            hrv.hrv > 20 -> 0.7f
            else -> 0.9f
        }

        val hrScore = when {
            hrv.restingHr < 60 -> 0.2f
            hrv.restingHr < 75 -> 0.4f
            hrv.restingHr < 90 -> 0.6f
            else -> 0.8f
        }

        val sleepScore = 1f - (hrv.sleepScore / 100f).coerceIn(0f, 1f)

        return (hrvScore * 0.5f + hrScore * 0.3f + sleepScore * 0.2f)
    }
}
