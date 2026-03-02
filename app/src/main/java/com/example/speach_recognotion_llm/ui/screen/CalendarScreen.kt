package com.example.speach_recognotion_llm.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.assistant.engine.AdjustmentPlan
import com.example.assistant.models.CalendarEvent
import com.example.assistant.models.RiskLevel
import com.example.speach_recognotion_llm.R
import com.example.speach_recognotion_llm.ui.viewmodel.CalendarUiState
import com.example.speach_recognotion_llm.ui.viewmodel.CalendarViewModel
import com.example.speach_recognotion_llm.ui.viewmodel.HrvViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.DayOfWeek

@Composable
fun CalendarScreen(
    calendarViewModel: CalendarViewModel,
    hrvViewModel: HrvViewModel,
    onRequestPermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by calendarViewModel.state.collectAsState()
    val pendingPlan by calendarViewModel.pendingPlan.collectAsState()
    val hrvState by hrvViewModel.state.collectAsState()

    // Feed stress data into calendar when loaded
    LaunchedEffect(state, hrvState.compositeStress) {
        if (state is CalendarUiState.Loaded) {
            val stress = hrvState.compositeStress?.composite ?: 0f
            if (stress > 0f) {
                calendarViewModel.runStressAnalysis(stress)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val s = state) {
            is CalendarUiState.PermissionRequired -> {
                CalendarPermissionCard(
                    onGrantAccess = onRequestPermissions
                )
            }
            is CalendarUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.calendar_loading))
                    }
                }
            }
            is CalendarUiState.Loaded -> {
                CalendarLoadedContent(
                    state = s,
                    pendingPlan = pendingPlan,
                    onDateSelected = { calendarViewModel.selectDate(it) },
                    onToggleAutoAdjust = { calendarViewModel.toggleAutoAdjust() },
                    onApplyPlan = { calendarViewModel.applyAdjustmentPlan() },
                    onDismissPlan = { calendarViewModel.dismissAdjustmentPlan() }
                )
            }
            is CalendarUiState.Adjusting -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.calendar_adjusting))
                    }
                }
            }
            is CalendarUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarPermissionCard(onGrantAccess: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.calendar_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.calendar_permission_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onGrantAccess) {
                    Text(stringResource(R.string.calendar_grant_access))
                }
            }
        }
    }
}

@Composable
private fun CalendarLoadedContent(
    state: CalendarUiState.Loaded,
    pendingPlan: AdjustmentPlan?,
    onDateSelected: (LocalDate) -> Unit,
    onToggleAutoAdjust: () -> Unit,
    onApplyPlan: () -> Unit,
    onDismissPlan: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with title and auto-adjust toggle
        item {
            CalendarHeader(
                autoAdjustEnabled = state.autoAdjustEnabled,
                currentStress = state.currentStress,
                riskLevel = state.riskLevel,
                onToggleAutoAdjust = onToggleAutoAdjust
            )
        }

        // Date selector strip
        item {
            DateSelectorStrip(
                weekDates = state.weekDates,
                selectedDate = state.selectedDate,
                onDateSelected = onDateSelected
            )
        }

        // Adjustment banner
        if (pendingPlan != null && pendingPlan.commands.isNotEmpty()) {
            item {
                AdjustmentBanner(
                    plan = pendingPlan,
                    onApply = onApplyPlan,
                    onDismiss = onDismissPlan
                )
            }
        }

        // Events for selected date
        val dayEvents = state.events.filter { event ->
            try {
                val eventDate = LocalDateTime.parse(event.startTime).date
                eventDate == state.selectedDate
            } catch (_: Exception) { false }
        }

        if (dayEvents.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.calendar_no_events),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            items(dayEvents, key = { "${it.eventId}_${it.startTime}" }) { event ->
                EventCard(event = event, currentStress = state.currentStress)
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    autoAdjustEnabled: Boolean,
    currentStress: Float,
    riskLevel: RiskLevel,
    onToggleAutoAdjust: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.calendar_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (currentStress > 0f) {
                StressBadge(stress = currentStress, riskLevel = riskLevel)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.calendar_auto_adjust),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.calendar_auto_adjust_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = autoAdjustEnabled,
                onCheckedChange = { onToggleAutoAdjust() }
            )
        }
    }
}

@Composable
private fun StressBadge(stress: Float, riskLevel: RiskLevel) {
    val color = when (riskLevel) {
        RiskLevel.LOW -> Color(0xFF4CAF50)
        RiskLevel.MODERATE -> Color(0xFFFF9800)
        RiskLevel.HIGH -> Color(0xFFF44336)
    }
    val labelRes = when (riskLevel) {
        RiskLevel.LOW -> R.string.calendar_risk_low
        RiskLevel.MODERATE -> R.string.calendar_risk_moderate
        RiskLevel.HIGH -> R.string.calendar_risk_high
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DateSelectorStrip(
    weekDates: List<LocalDate>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(weekDates) { date ->
            val isSelected = date == selectedDate
            val dayName = date.dayOfWeek.shortName()

            FilterChip(
                selected = isSelected,
                onClick = { onDateSelected(date) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
private fun AdjustmentBanner(
    plan: AdjustmentPlan,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.calendar_adjustment_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            for (reason in plan.reasoning) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (plan.recoveryBlocksCount > 0) {
                    Text(
                        text = stringResource(R.string.calendar_recovery_count, plan.recoveryBlocksCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                }
                if (plan.rescheduledCount > 0) {
                    Text(
                        text = stringResource(R.string.calendar_rescheduled_count, plan.rescheduledCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800)
                    )
                }
                if (plan.coachingRemindersCount > 0) {
                    Text(
                        text = stringResource(R.string.calendar_coaching_count, plan.coachingRemindersCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2196F3)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.calendar_dismiss))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onApply) {
                    Text(stringResource(R.string.calendar_apply))
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent, currentStress: Float) {
    val isRecovery = event.title.startsWith("[RECOVERY]")
    val isCoaching = event.title.startsWith("[COACHING]")

    val backgroundColor = when {
        isRecovery -> Color(0xFFE8F5E9)
        isCoaching -> Color(0xFFE3F2FD)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isRecovery -> Color(0xFF4CAF50)
        isCoaching -> Color(0xFF2196F3)
        currentStress >= 0.7f -> Color(0xFFF44336)
        currentStress >= 0.4f -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }

    val icon = when {
        isRecovery -> Icons.Default.SelfImprovement
        isCoaching -> Icons.Default.Psychology
        else -> null
    }

    val displayTitle = when {
        isRecovery -> event.title.removePrefix("[RECOVERY] ")
        isCoaching -> event.title.removePrefix("[COACHING] ")
        else -> event.title
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Color indicator bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(72.dp)
                    .background(borderColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = borderColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatTimeRange(event.startTime, event.endTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    if (isRecovery) {
                        Text(
                            text = stringResource(R.string.calendar_recovery_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50)
                        )
                    } else if (isCoaching) {
                        Text(
                            text = stringResource(R.string.calendar_coaching_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2196F3)
                        )
                    }

                    if (event.goalId != null && !isRecovery && !isCoaching) {
                        Text(
                            text = "Goal linked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimeRange(start: String, end: String): String {
    return try {
        val s = LocalDateTime.parse(start)
        val e = LocalDateTime.parse(end)
        "${s.hour.toString().padStart(2, '0')}:${s.minute.toString().padStart(2, '0')} - " +
                "${e.hour.toString().padStart(2, '0')}:${e.minute.toString().padStart(2, '0')}"
    } catch (_: Exception) {
        "$start - $end"
    }
}

private fun DayOfWeek.shortName(): String = when (this) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
    else -> name.take(3)
}
