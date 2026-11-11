package com.medicare.app.ui.session

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medicare.app.alarms.AlarmScheduler
import com.medicare.app.alarms.AlarmTiming
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.models.Medication
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.AppDatabase
import com.medicare.app.engine.SessionGroupingEngine
import com.medicare.app.engine.TimeSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val sessionRepository = SessionRepository(database)
    private val medicationRepository = MedicationRepository(database)
    private val groupingEngine = SessionGroupingEngine()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val _selectedDate = MutableStateFlow(dateFormat.format(Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val medications: StateFlow<List<Medication>> = medicationRepository.medicationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<MedicineSession>> = selectedDate
        .flatMapLatest { sessionRepository.observeSessionsForDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedSessions: StateFlow<Map<TimeSlot, List<MedicineSession>>> = sessions
        .map(groupingEngine::groupSessionsByTimeSlot)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        refreshSessions(_selectedDate.value)
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
        refreshSessions(date)
    }

    fun refreshSessions(date: String) {
        viewModelScope.launch {
            val meds = medicationRepository.getMedications()
            sessionRepository.generateSessionsForDate(date, meds)
        }
    }

    fun observeDoseEvents(sessionId: String) = sessionRepository.observeDoseEventsForSession(sessionId)

    fun updateDoseStatus(eventId: String, status: DoseStatus, quantityRecorded: Int? = null) {
        viewModelScope.launch {
            sessionRepository.updateDoseEventStatus(eventId, status, quantityRecorded = quantityRecorded)
            refreshSessions(_selectedDate.value)
        }
    }

    fun snoozeSession(context: Context, session: MedicineSession) {
        viewModelScope.launch {
            sessionRepository.nextOutstanding(session.sessionId)?.let { event ->
                val scheduledAt = System.currentTimeMillis()
                val triggerAt = AlarmTiming.snoozeTriggerAt(scheduledAt)
                if (sessionRepository.scheduleRetry(event.eventId, triggerAt)) {
                    AlarmScheduler.scheduleDoseAlarm(context, event.copy(nextReminderAt = triggerAt), AlarmScheduler.AlarmKind.SNOOZE, triggerAt)
                }
            }
        }
    }
}
