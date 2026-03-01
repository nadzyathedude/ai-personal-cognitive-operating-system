package com.example.assistant.memory

import com.example.assistant.models.EmotionalState
import com.example.assistant.models.EmotionalTimelineEntry
import com.example.assistant.models.MemoryNode
import com.example.assistant.state.MemoryQueryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class MemoryGraphManager {

    private val _state = MutableStateFlow<MemoryQueryState>(MemoryQueryState.Idle)
    val state: StateFlow<MemoryQueryState> = _state.asStateFlow()

    private val nodes = mutableListOf<MemoryNode>()

    private val edges = mutableListOf<MemoryEdge>()

    fun addMemoryNode(node: MemoryNode) {
        nodes.add(node)

        for (existing in nodes) {
            if (existing.id == node.id) continue

            // Temporal edge
            addTemporalEdge(existing, node)

            // Emotional similarity edge
            val emotionalSim = computeEmotionalSimilarity(
                existing.emotionalState, node.emotionalState
            )
            if (emotionalSim > 0.7f) {
                edges.add(
                    MemoryEdge(
                        sourceId = existing.id,
                        targetId = node.id,
                        type = EdgeType.EMOTIONAL_SIMILARITY,
                        weight = emotionalSim
                    )
                )
            }

            // Goal-related edge
            if (existing.relatedGoalId != null &&
                existing.relatedGoalId == node.relatedGoalId
            ) {
                edges.add(
                    MemoryEdge(
                        sourceId = existing.id,
                        targetId = node.id,
                        type = EdgeType.GOAL_RELATED,
                        weight = 1.0f
                    )
                )
            }

            // Stress correlation edge
            val stressCorr = computeStressCorrelation(
                existing.emotionalState, node.emotionalState
            )
            if (stressCorr > 0.6f) {
                edges.add(
                    MemoryEdge(
                        sourceId = existing.id,
                        targetId = node.id,
                        type = EdgeType.STRESS_CORRELATION,
                        weight = stressCorr
                    )
                )
            }
        }
    }

    fun retrieveRelatedMemories(query: String, limit: Int = 10): List<MemoryNode> {
        _state.value = MemoryQueryState.Searching

        val queryTags = query.lowercase().split(" ").filter { it.length > 3 }
        val scored = nodes.map { node ->
            val tagScore = node.semanticTags.count { tag ->
                queryTags.any { q -> tag.contains(q, ignoreCase = true) }
            }.toFloat()
            val contentScore = if (node.content.contains(query, ignoreCase = true)) 2f else 0f
            node to (tagScore + contentScore)
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

        _state.value = MemoryQueryState.Results(scored)
        return scored
    }

    fun buildEmotionalTimeline(
        startTimestamp: String,
        endTimestamp: String
    ): List<EmotionalTimelineEntry> {
        return nodes
            .filter { it.timestamp >= startTimestamp && it.timestamp <= endTimestamp }
            .sortedBy { it.timestamp }
            .map { node ->
                EmotionalTimelineEntry(
                    timestamp = node.timestamp,
                    valence = node.emotionalState.valence,
                    stressScore = node.emotionalState.stressScore,
                    primaryEmotion = node.emotionalState.primaryEmotion,
                    relatedGoal = node.relatedGoalId
                )
            }
    }

    fun detectRecurringEmotionalPatterns(): List<RecurringPattern> {
        val emotionCounts = mutableMapOf<String, MutableList<MemoryNode>>()
        for (node in nodes) {
            val emotion = node.emotionalState.primaryEmotion
            emotionCounts.getOrPut(emotion) { mutableListOf() }.add(node)
        }

        return emotionCounts
            .filter { it.value.size >= 3 }
            .map { (emotion, occurrences) ->
                val avgValence = occurrences.map { it.emotionalState.valence }.average().toFloat()
                val avgStress = occurrences.map { it.emotionalState.stressScore }.average().toFloat()
                val relatedGoals = occurrences.mapNotNull { it.relatedGoalId }.distinct()

                RecurringPattern(
                    emotion = emotion,
                    frequency = occurrences.size,
                    avgValence = avgValence,
                    avgStress = avgStress,
                    relatedGoalIds = relatedGoals
                )
            }
            .sortedByDescending { it.frequency }
    }

    fun getEdges(): List<MemoryEdge> = edges.toList()

    private fun addTemporalEdge(a: MemoryNode, b: MemoryNode) {
        edges.add(
            MemoryEdge(
                sourceId = a.id,
                targetId = b.id,
                type = EdgeType.TEMPORAL,
                weight = 1.0f
            )
        )
    }

    private fun computeEmotionalSimilarity(a: EmotionalState, b: EmotionalState): Float {
        val valenceDiff = (a.valence - b.valence)
        val arousalDiff = (a.arousal - b.arousal)
        val distance = sqrt((valenceDiff * valenceDiff + arousalDiff * arousalDiff).toDouble())
        return (1.0 - distance / 2.0).coerceIn(0.0, 1.0).toFloat()
    }

    private fun computeStressCorrelation(a: EmotionalState, b: EmotionalState): Float {
        val diff = kotlin.math.abs(a.stressScore - b.stressScore)
        return (1.0f - diff).coerceIn(0f, 1f)
    }
}

data class MemoryEdge(
    val sourceId: String,
    val targetId: String,
    val type: EdgeType,
    val weight: Float
)

enum class EdgeType {
    TEMPORAL, EMOTIONAL_SIMILARITY, GOAL_RELATED, STRESS_CORRELATION
}

data class RecurringPattern(
    val emotion: String,
    val frequency: Int,
    val avgValence: Float,
    val avgStress: Float,
    val relatedGoalIds: List<String>
)
