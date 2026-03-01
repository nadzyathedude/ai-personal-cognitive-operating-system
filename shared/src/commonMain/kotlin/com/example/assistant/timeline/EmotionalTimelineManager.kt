package com.example.assistant.timeline

import com.example.assistant.models.EmotionalTimelineEntry
import kotlin.math.sqrt

class EmotionalTimelineManager {

    private val entries = mutableListOf<EmotionalTimelineEntry>()

    fun addEntry(entry: EmotionalTimelineEntry) {
        entries.add(entry)
        entries.sortBy { it.timestamp }
    }

    fun getTimeline(start: String? = null, end: String? = null): List<EmotionalTimelineEntry> {
        return entries.filter { entry ->
            (start == null || entry.timestamp >= start) &&
                    (end == null || entry.timestamp <= end)
        }
    }

    fun weeklyAggregation(weekStart: String): WeeklyAggregation {
        val weekEntries = entries.filter { it.timestamp >= weekStart }

        if (weekEntries.isEmpty()) return WeeklyAggregation()

        val valences = weekEntries.map { it.valence }
        val stresses = weekEntries.map { it.stressScore }
        val emotions = weekEntries.groupBy { it.primaryEmotion }
            .mapValues { it.value.size }

        return WeeklyAggregation(
            avgValence = valences.average().toFloat(),
            avgStress = stresses.average().toFloat(),
            moodVariability = standardDeviation(valences),
            dominantEmotion = emotions.maxByOrNull { it.value }?.key ?: "neutral",
            stressTrend = computeTrend(stresses),
            entryCount = weekEntries.size,
            emotionDistribution = emotions
        )
    }

    fun monthlyAggregation(monthStart: String): MonthlyAggregation {
        val monthEntries = entries.filter { it.timestamp >= monthStart }

        if (monthEntries.isEmpty()) return MonthlyAggregation()

        val valences = monthEntries.map { it.valence }
        val stresses = monthEntries.map { it.stressScore }
        val byGoal = monthEntries.filter { it.relatedGoal != null }
            .groupBy { it.relatedGoal!! }

        val goalStress = byGoal.mapValues { (_, entries) ->
            entries.map { it.stressScore }.average().toFloat()
        }

        return MonthlyAggregation(
            avgValence = valences.average().toFloat(),
            avgStress = stresses.average().toFloat(),
            moodVariability = standardDeviation(valences),
            stressTrend = computeTrend(stresses),
            entryCount = monthEntries.size,
            goalStressMap = goalStress
        )
    }

    fun getStressHeatmapData(): List<HeatmapPoint> {
        return entries.map { entry ->
            HeatmapPoint(
                timestamp = entry.timestamp,
                intensity = entry.stressScore
            )
        }
    }

    fun getGoalEmotionOverlay(goalId: String): List<EmotionalTimelineEntry> {
        return entries.filter { it.relatedGoal == goalId }
    }

    private fun standardDeviation(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance).toFloat()
    }

    private fun computeTrend(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val n = values.size.toFloat()
        val indices = values.indices.map { it.toFloat() }
        val sumX = indices.sum()
        val sumY = values.sum()
        val sumXY = indices.zip(values) { x, y -> x * y }.sum()
        val sumX2 = indices.map { it * it }.sum()
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
    }
}

data class WeeklyAggregation(
    val avgValence: Float = 0f,
    val avgStress: Float = 0f,
    val moodVariability: Float = 0f,
    val dominantEmotion: String = "neutral",
    val stressTrend: Float = 0f,
    val entryCount: Int = 0,
    val emotionDistribution: Map<String, Int> = emptyMap()
)

data class MonthlyAggregation(
    val avgValence: Float = 0f,
    val avgStress: Float = 0f,
    val moodVariability: Float = 0f,
    val stressTrend: Float = 0f,
    val entryCount: Int = 0,
    val goalStressMap: Map<String, Float> = emptyMap()
)

data class HeatmapPoint(
    val timestamp: String,
    val intensity: Float
)
