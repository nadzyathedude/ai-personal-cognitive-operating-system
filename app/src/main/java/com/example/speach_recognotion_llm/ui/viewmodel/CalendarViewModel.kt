package com.example.speach_recognotion_llm.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.assistant.engine.AdjustmentPlan
import com.example.assistant.engine.CalendarStressAdjuster
import com.example.assistant.models.BurnoutRisk
import com.example.assistant.models.CalendarAction
import com.example.assistant.models.CalendarEvent
import com.example.assistant.models.Goal
import com.example.assistant.models.RiskLevel
import com.example.speach_recognotion_llm.data.repository.AndroidCalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

sealed class CalendarUiState {
    data object PermissionRequired : CalendarUiState()
    data object Loading : CalendarUiState()
    data class Loaded(
        val events: List<CalendarEvent> = emptyList(),
        val selectedDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
        val weekDates: List<LocalDate> = emptyList(),
        val autoAdjustEnabled: Boolean = false,
        val currentStress: Float = 0f,
        val riskLevel: RiskLevel = RiskLevel.LOW
    ) : CalendarUiState()
    data class Adjusting(val plan: AdjustmentPlan) : CalendarUiState()
    data class Error(val message: String) : CalendarUiState()
}

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private var calendarRepository: AndroidCalendarRepository? = null

    private val stressAdjuster = CalendarStressAdjuster()

    private val _state = MutableStateFlow<CalendarUiState>(CalendarUiState.PermissionRequired)
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    private val _pendingPlan = MutableStateFlow<AdjustmentPlan?>(null)
    val pendingPlan: StateFlow<AdjustmentPlan?> = _pendingPlan.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setPermissionGranted(granted: Boolean) {
        if (granted) {
            calendarRepository = AndroidCalendarRepository(
                getApplication<Application>().contentResolver
            )
            loadEvents()
        } else {
            _state.value = CalendarUiState.PermissionRequired
        }
    }

    fun loadEvents(date: LocalDate? = null) {
        val repo = calendarRepository ?: return
        _state.value = CalendarUiState.Loading

        viewModelScope.launch {
            try {
                val today = date ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
                val weekStart = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
                val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
                val weekDates = (0..6).map { weekStart.plus(it, DateTimeUnit.DAY) }

                val events = repo.getEvents(weekStart.toString(), weekEnd.toString())

                _state.value = CalendarUiState.Loaded(
                    events = events,
                    selectedDate = today,
                    weekDates = weekDates
                )
            } catch (e: Exception) {
                _state.value = CalendarUiState.Error("Failed to load calendar: ${e.message}")
            }
        }
    }

    fun selectDate(date: LocalDate) {
        val current = _state.value
        if (current is CalendarUiState.Loaded) {
            _state.value = current.copy(selectedDate = date)
        }
    }

    fun toggleAutoAdjust() {
        val current = _state.value
        if (current is CalendarUiState.Loaded) {
            _state.value = current.copy(autoAdjustEnabled = !current.autoAdjustEnabled)
        }
    }

    fun runStressAnalysis(
        compositeStress: Float,
        goals: List<Goal> = emptyList(),
        burnoutRisks: List<BurnoutRisk> = emptyList()
    ) {
        val current = _state.value
        if (current !is CalendarUiState.Loaded) return

        viewModelScope.launch {
            val riskLevel = when {
                compositeStress >= 0.7f -> RiskLevel.HIGH
                compositeStress >= 0.4f -> RiskLevel.MODERATE
                else -> RiskLevel.LOW
            }

            _state.value = current.copy(
                currentStress = compositeStress,
                riskLevel = riskLevel
            )

            val plan = stressAdjuster.generateAdjustmentPlan(
                events = current.events,
                goals = goals,
                currentStress = compositeStress,
                burnoutRisks = burnoutRisks
            )

            if (plan.commands.isNotEmpty()) {
                if (current.autoAdjustEnabled) {
                    applyPlan(plan)
                } else {
                    _pendingPlan.value = plan
                }
            }
        }
    }

    fun applyAdjustmentPlan() {
        val plan = _pendingPlan.value ?: return
        _pendingPlan.value = null
        applyPlan(plan)
    }

    fun dismissAdjustmentPlan() {
        _pendingPlan.value = null
    }

    fun clearError() {
        _error.value = null
    }

    private fun applyPlan(plan: AdjustmentPlan) {
        val repo = calendarRepository ?: return

        val prevState = _state.value
        _state.value = CalendarUiState.Adjusting(plan)

        viewModelScope.launch {
            try {
                for (cmd in plan.commands) {
                    when (cmd.action) {
                        CalendarAction.CREATE -> {
                            repo.createEvent(
                                title = cmd.event.title,
                                startTime = cmd.event.startTime,
                                endTime = cmd.event.endTime,
                                goalId = cmd.event.goalId
                            )
                        }
                        CalendarAction.UPDATE -> {
                            repo.updateEvent(
                                eventId = cmd.event.eventId,
                                title = cmd.event.title,
                                startTime = cmd.event.startTime,
                                endTime = cmd.event.endTime
                            )
                        }
                        CalendarAction.DELETE -> {
                            repo.deleteEvent(cmd.event.eventId)
                        }
                        CalendarAction.READ -> { /* no-op */ }
                    }
                }
                // Reload events after applying changes
                loadEvents(
                    (prevState as? CalendarUiState.Loaded)?.selectedDate
                )
            } catch (e: Exception) {
                _error.value = "Failed to apply adjustments: ${e.message}"
                _state.value = prevState
            }
        }
    }
}
