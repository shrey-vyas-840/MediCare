package com.medicare.app.ui.session

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medicare.app.alarms.AlarmScheduler
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.JsonStorage
import com.medicare.app.engine.SessionGroupingEngine
import com.medicare.app.engine.TimeSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    private val jsonStorage = JsonStorage(application)
    private val sessionRepository = SessionRepository(jsonStorage)
    private val medicationRepository = MedicationRepository(jsonStorage)
    private val groupingEngine = SessionGroupingEngine()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val _selectedDate = MutableStateFlow(dateFormat.format(Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val sessions: StateFlow<List<MedicineSession>> = sessionRepository.sessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _groupedSessions = MutableStateFlow<Map<TimeSlot, List<MedicineSession>>>(emptyMap())
    val groupedSessions: StateFlow<Map<TimeSlot, List<MedicineSession>>> = _groupedSessions.asStateFlow()

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
            val dateSessions = sessionRepository.getSessionsForDate(date)
            _groupedSessions.value = groupingEngine.groupSessionsByTimeSlot(dateSessions)
        }
    }

    fun getDoseEvents(sessionId: String): List<DoseEvent> {
        return sessionRepository.getDoseEventsForSession(sessionId)
    }

    fun updateDoseStatus(eventId: String, status: DoseStatus, quantityRecorded: Int? = null) {
        viewModelScope.launch {
            sessionRepository.updateDoseEventStatus(eventId, status, quantityRecorded = quantityRecorded)
            refreshSessions(_selectedDate.value)
        }
    }

    fun snoozeSession(context: Context, session: MedicineSession) {
        AlarmScheduler.scheduleSnooze(context, session, 5)
    }
}
