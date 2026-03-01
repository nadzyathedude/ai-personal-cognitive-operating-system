package com.example.assistant.platform

import com.example.assistant.integration.GarminBridge
import com.example.assistant.models.HrvData

class IosGarminBridge : GarminBridge {

    override suspend fun isConnected(): Boolean = false

    override suspend fun requestPermission(): Boolean {
        // iOS: Use Garmin Health API via HealthKit bridge
        return false
    }

    override suspend fun fetchLatestHrv(): HrvData? {
        // iOS: Query via HealthKit HKQuantityType.quantityType(forIdentifier: .heartRateVariabilitySDNN)
        return null
    }

    override suspend fun fetchDailyHrv(): List<HrvData> {
        return emptyList()
    }
}
