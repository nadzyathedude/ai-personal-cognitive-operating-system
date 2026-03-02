package com.example.assistant.engine

import com.example.assistant.models.BurnoutRisk
import com.example.assistant.models.CalendarAction
import com.example.assistant.models.CalendarCommand
import com.example.assistant.models.CalendarEvent
import com.example.assistant.models.Goal
import com.example.assistant.models.RiskLevel
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class AdjustmentPlan(
    val commands: List<CalendarCommand> = emptyList(),
    val reasoning: List<String> = emptyList(),
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val recoveryBlocksCount: Int = 0,
    val rescheduledCount: Int = 0,
    val coachingRemindersCount: Int = 0,
    val goalSplitCount: Int = 0
)

class CalendarStressAdjuster(
    private val rescheduleThreshold: Float = 0.7f,
    private val recoveryBlockThreshold: Float = 0.5f,
    private val coachingReminderThreshold: Float = 0.4f,
    private val goalSplitBurnoutRisk: Float = 0.7f
) {
    private var lastAdjustmentTime: Long = 0L
    private val cooldownMs = 4 * 60 * 60 * 1000L // 4 hours

    fun generateAdjustmentPlan(
        events: List<CalendarEvent>,
        goals: List<Goal>,
        currentStress: Float,
        burnoutRisks: List<BurnoutRisk>,
        nowIso: String? = null
    ): AdjustmentPlan {
        val now = if (nowIso != null) {
            try { LocalDateTime.parse(nowIso) } catch (_: Exception) { currentTime() }
        } else {
            currentTime()
        }

        val nowMillis = now.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        if (nowMillis - lastAdjustmentTime < cooldownMs) {
            return AdjustmentPlan(reasoning = listOf("Cooldown active, skipping adjustment"))
        }

        val commands = mutableListOf<CalendarCommand>()
        val reasoning = mutableListOf<String>()
        var recoveryCount = 0
        var rescheduledCount = 0
        var coachingCount = 0
        var goalSplitCount = 0

        val riskLevel = when {
            currentStress >= 0.7f -> RiskLevel.HIGH
            currentStress >= 0.4f -> RiskLevel.MODERATE
            else -> RiskLevel.LOW
        }

        // 1. Recovery blocks: find gaps >= 30min in next 2 days
        if (currentStress >= recoveryBlockThreshold) {
            val recoveryCommands = generateRecoveryBlocks(events, now)
            commands.addAll(recoveryCommands)
            recoveryCount = recoveryCommands.size
            if (recoveryCount > 0) {
                reasoning.add("Added $recoveryCount recovery blocks in schedule gaps to reduce stress")
            }
        }

        // 2. Reschedule non-urgent events when stress is high
        if (currentStress >= rescheduleThreshold) {
            val rescheduleCommands = rescheduleNonUrgent(events, goals, now)
            commands.addAll(rescheduleCommands)
            rescheduledCount = rescheduleCommands.size
            if (rescheduledCount > 0) {
                reasoning.add("Rescheduled $rescheduledCount non-urgent events to tomorrow")
            }
        }

        // 3. Split goals with high burnout risk
        val highRiskGoals = burnoutRisks.filter { it.burnoutRisk >= goalSplitBurnoutRisk }
        for (risk in highRiskGoals) {
            val linkedEvents = events.filter { it.goalId == risk.goalId }
            for (event in linkedEvents) {
                val splitCommands = splitGoalEvent(event)
                if (splitCommands.isNotEmpty()) {
                    commands.addAll(splitCommands)
                    goalSplitCount++
                    reasoning.add("Split '${event.title}' into smaller sessions (burnout risk: ${(risk.burnoutRisk * 100).toInt()}%)")
                }
            }
        }

        // 4. Coaching reminders
        if (currentStress >= coachingReminderThreshold) {
            val coachingCommands = generateCoachingReminders(events, now)
            commands.addAll(coachingCommands)
            coachingCount = coachingCommands.size
            if (coachingCount > 0) {
                reasoning.add("Added $coachingCount coaching check-in reminders")
            }
        }

        if (commands.isNotEmpty()) {
            lastAdjustmentTime = nowMillis
        }

        return AdjustmentPlan(
            commands = commands,
            reasoning = reasoning,
            riskLevel = riskLevel,
            recoveryBlocksCount = recoveryCount,
            rescheduledCount = rescheduledCount,
            coachingRemindersCount = coachingCount,
            goalSplitCount = goalSplitCount
        )
    }

    fun resetCooldown() {
        lastAdjustmentTime = 0L
    }

    private fun generateRecoveryBlocks(
        events: List<CalendarEvent>,
        now: LocalDateTime
    ): List<CalendarCommand> {
        val commands = mutableListOf<CalendarCommand>()
        val tz = TimeZone.currentSystemDefault()

        // Look at next 2 days, find gaps between events
        val sortedEvents = events
            .filter {
                try {
                    val start = LocalDateTime.parse(it.startTime)
                    start > now
                } catch (_: Exception) { false }
            }
            .sortedBy { it.startTime }

        if (sortedEvents.isEmpty()) {
            // No upcoming events — add one recovery block at next hour
            val nextHour = roundToNextHour(now)
            val endTime = addMinutes(nextHour, 15)
            commands.add(
                CalendarCommand(
                    action = CalendarAction.CREATE,
                    event = CalendarEvent(
                        title = "[RECOVERY] Breathing & Stretch",
                        description = "Take a moment to breathe and stretch. Auto-suggested by stress management.",
                        startTime = nextHour.toString(),
                        endTime = endTime.toString()
                    )
                )
            )
            return commands
        }

        // Find gaps between consecutive events
        for (i in 0 until sortedEvents.size - 1) {
            val currentEnd = try { LocalDateTime.parse(sortedEvents[i].endTime) } catch (_: Exception) { continue }
            val nextStart = try { LocalDateTime.parse(sortedEvents[i + 1].startTime) } catch (_: Exception) { continue }

            val gapMinutes = minutesBetween(currentEnd, nextStart)
            if (gapMinutes >= 30) {
                val recoveryStart = addMinutes(currentEnd, 5)
                val recoveryEnd = addMinutes(recoveryStart, 15)
                commands.add(
                    CalendarCommand(
                        action = CalendarAction.CREATE,
                        event = CalendarEvent(
                            title = "[RECOVERY] Breathing & Stretch",
                            description = "Take a moment to breathe and stretch. Auto-suggested by stress management.",
                            startTime = recoveryStart.toString(),
                            endTime = recoveryEnd.toString()
                        )
                    )
                )
                if (commands.size >= 3) break // Max 3 recovery blocks
            }
        }

        return commands
    }

    private fun rescheduleNonUrgent(
        events: List<CalendarEvent>,
        goals: List<Goal>,
        now: LocalDateTime
    ): List<CalendarCommand> {
        val commands = mutableListOf<CalendarCommand>()
        val tz = TimeZone.currentSystemDefault()

        for (event in events) {
            // Skip recovery/coaching events
            if (event.title.startsWith("[RECOVERY]") || event.title.startsWith("[COACHING]")) continue

            val eventStart = try { LocalDateTime.parse(event.startTime) } catch (_: Exception) { continue }
            if (eventStart <= now) continue // Skip past events

            // Check if linked goal has a deadline within 48h
            val linkedGoal = if (event.goalId != null) {
                goals.find { it.goalId == event.goalId }
            } else null

            val hasUrgentDeadline = linkedGoal?.deadline?.let { deadline ->
                try {
                    val dl = LocalDateTime.parse(deadline)
                    val hoursUntilDeadline = minutesBetween(now, dl) / 60
                    hoursUntilDeadline <= 48
                } catch (_: Exception) { false }
            } ?: false

            if (hasUrgentDeadline) continue

            // Reschedule to tomorrow same time
            val newStart = addDays(eventStart, 1)
            val eventEnd = try { LocalDateTime.parse(event.endTime) } catch (_: Exception) { continue }
            val newEnd = addDays(eventEnd, 1)

            commands.add(
                CalendarCommand(
                    action = CalendarAction.UPDATE,
                    event = event.copy(
                        startTime = newStart.toString(),
                        endTime = newEnd.toString()
                    )
                )
            )

            if (commands.size >= 3) break // Max 3 reschedules per run
        }

        return commands
    }

    private fun splitGoalEvent(event: CalendarEvent): List<CalendarCommand> {
        val startTime = try { LocalDateTime.parse(event.startTime) } catch (_: Exception) { return emptyList() }
        val endTime = try { LocalDateTime.parse(event.endTime) } catch (_: Exception) { return emptyList() }
        val durationMinutes = minutesBetween(startTime, endTime)

        if (durationMinutes < 60) return emptyList() // Too short to split

        val commands = mutableListOf<CalendarCommand>()

        // Delete original
        commands.add(CalendarCommand(action = CalendarAction.DELETE, event = event))

        // Split into 2-3 sessions
        val sessionCount = if (durationMinutes >= 120) 3 else 2
        val sessionDuration = (durationMinutes / sessionCount).toInt()
        val breakDuration = 15

        var currentStart = startTime
        for (i in 0 until sessionCount) {
            val sessionEnd = addMinutes(currentStart, sessionDuration)
            commands.add(
                CalendarCommand(
                    action = CalendarAction.CREATE,
                    event = CalendarEvent(
                        title = "${event.title} (${i + 1}/$sessionCount)",
                        description = event.description,
                        startTime = currentStart.toString(),
                        endTime = sessionEnd.toString(),
                        goalId = event.goalId
                    )
                )
            )
            currentStart = addMinutes(sessionEnd, breakDuration)
        }

        return commands
    }

    private fun generateCoachingReminders(
        events: List<CalendarEvent>,
        now: LocalDateTime
    ): List<CalendarCommand> {
        val commands = mutableListOf<CalendarCommand>()

        // Add coaching reminder at 9 AM and 6 PM today/tomorrow if not already present
        val existingCoaching = events.any { it.title.startsWith("[COACHING]") }
        if (existingCoaching) return commands

        val times = listOf(9, 18) // 9 AM, 6 PM
        for (hour in times) {
            val reminderTime = LocalDateTime(
                now.date.year, now.date.monthNumber, now.date.dayOfMonth,
                hour, 0, 0
            )
            if (reminderTime <= now) continue

            val endTime = addMinutes(reminderTime, 10)
            commands.add(
                CalendarCommand(
                    action = CalendarAction.CREATE,
                    event = CalendarEvent(
                        title = "[COACHING] Check in with yourself",
                        description = "Take a moment to check in with how you're feeling. Auto-suggested by stress management.",
                        startTime = reminderTime.toString(),
                        endTime = endTime.toString()
                    )
                )
            )
        }

        return commands
    }

    private fun currentTime(): LocalDateTime {
        return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }

    private fun roundToNextHour(dt: LocalDateTime): LocalDateTime {
        val nextHour = if (dt.minute > 0 || dt.second > 0) dt.hour + 1 else dt.hour
        return LocalDateTime(dt.year, dt.monthNumber, dt.dayOfMonth, nextHour.coerceAtMost(23), 0, 0)
    }

    private fun addMinutes(dt: LocalDateTime, minutes: Int): LocalDateTime {
        val tz = TimeZone.currentSystemDefault()
        val instant = dt.toInstant(tz).plus(minutes, DateTimeUnit.MINUTE, tz)
        return instant.toLocalDateTime(tz)
    }

    private fun addDays(dt: LocalDateTime, days: Int): LocalDateTime {
        val tz = TimeZone.currentSystemDefault()
        val instant = dt.toInstant(tz).plus(days, DateTimeUnit.DAY, tz)
        return instant.toLocalDateTime(tz)
    }

    private fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Long {
        val tz = TimeZone.currentSystemDefault()
        val fromInstant = from.toInstant(tz)
        val toInstant = to.toInstant(tz)
        return (toInstant.toEpochMilliseconds() - fromInstant.toEpochMilliseconds()) / 60000
    }
}
