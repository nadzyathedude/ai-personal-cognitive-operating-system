package com.example.assistant.platform

import com.example.assistant.integration.GarminBridge
import com.example.assistant.models.HrvData

class AndroidGarminBridge : GarminBridge {

    private var connected = false

    override suspend fun isConnected(): Boolean = connected

    override suspend fun requestPermission(): Boolean {
        // TODO: Integration with Garmin Connect IQ SDK
        // Requires com.garmin.connectiq:ciq-companion-app-sdk
        // For now, returns false since no real Garmin SDK is integrated
        return false
    }

    override suspend fun fetchLatestHrv(): HrvData? {
        if (!connected) return null
        // Placeholder: real implementation queries Garmin Health API
        return null
    }

    override suspend fun fetchDailyHrv(): List<HrvData> {
        if (!connected) return emptyList()
        // Placeholder: real implementation queries Garmin Health API
        return emptyList()
    }
}
