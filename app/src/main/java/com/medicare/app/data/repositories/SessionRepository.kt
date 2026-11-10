package com.medicare.app.data.repositories

import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.models.Medication
import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.models.SessionStatus
import com.medicare.app.data.storage.JsonStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class SessionRepository(private val jsonStorage: JsonStorage) {
    private val sessionsFileName = "medicine_sessions.json"
    private val eventsFileName = "dose_events.json"

    private val _sessionsFlow = MutableStateFlow<List<MedicineSession>>(emptyList())
    val sessionsFlow: Flow<List<MedicineSession>> = _sessionsFlow.asStateFlow()

    private val _eventsFlow = MutableStateFlow<List<DoseEvent>>(emptyList())
    val eventsFlow: Flow<List<DoseEvent>> = _eventsFlow.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        _sessionsFlow.value = jsonStorage.load<List<MedicineSession>>(sessionsFileName, emptyList(), 1)
        _eventsFlow.value = jsonStorage.load<List<DoseEvent>>(eventsFileName, emptyList(), 1)
    }

    fun getSessionsForDate(date: String): List<MedicineSession> {
        return _sessionsFlow.value.filter { it.scheduledDate == date }
    }

    fun observeSessionsForDate(date: String): Flow<List<MedicineSession>> {
        return sessionsFlow.map { list -> list.filter { it.scheduledDate == date } }
    }

    fun getDoseEventsForSession(sessionId: String): List<DoseEvent> {
        return _eventsFlow.value.filter { it.sessionId == sessionId }
    }

    fun getDoseEvent(eventId: String): DoseEvent? {
        return _eventsFlow.value.find { it.eventId == eventId }
    }

    suspend fun generateSessionsForDate(date: String, medications: List<Medication>) {
        val timeToMeds = mutableMapOf<String, MutableList<Medication>>()
        for (med in medications) {
            if (!med.active) continue
            for (time in med.times) {
                timeToMeds.getOrPut(time) { mutableListOf() }.add(med)
            }
        }

        val existingSessions = _sessionsFlow.value.toMutableList()
        val existingEvents = _eventsFlow.value.toMutableList()

        val affectedSessionIds = existingSessions.filter { it.scheduledDate == date }.map { it.sessionId }.toSet()
        val existingEventsForDate = existingEvents.filter { it.sessionId in affectedSessionIds }
        val existingEventMap = existingEventsForDate.associateBy { "${it.medicineId}_${it.scheduledTime}" }

        existingSessions.removeIf { it.scheduledDate == date }

        val newSessions = mutableListOf<MedicineSession>()
        val newEvents = mutableListOf<DoseEvent>()

        for ((time, meds) in timeToMeds.entries.sortedBy { it.key }) {
            val session = MedicineSession(
                scheduledDate = date,
                scheduledTime = time,
                doseEventIds = emptyList(),
                status = SessionStatus.PENDING,
                submittedCount = 0,
                totalMedicineCount = meds.size
            )

            val eventIds = mutableListOf<String>()
            for (med in meds) {
                val key = "${med.id}_$time"
                val existingEvent = existingEventMap[key]
                val event = if (existingEvent != null) {
                    existingEvent // Preserve existing submission status and photoPath!
                } else {
                    DoseEvent(
                        sessionId = session.sessionId,
                        medicineId = med.id,
                        scheduledDate = date,
                        scheduledTime = time,
                        status = DoseStatus.SCHEDULED
                    )
                }
                newEvents.add(event)
                eventIds.add(event.eventId)
            }

            val submittedCount = eventIds.count { id ->
                val ev = newEvents.find { it.eventId == id }
                ev?.status == DoseStatus.TAKEN || ev?.status == DoseStatus.SKIPPED
            }
            val allTaken = eventIds.all { id ->
                val ev = newEvents.find { it.eventId == id }
                ev?.status == DoseStatus.TAKEN || ev?.status == DoseStatus.SKIPPED
            }

            val sessionStatus = when {
                allTaken -> SessionStatus.COMPLETED
                submittedCount > 0 -> SessionStatus.PARTIAL
                else -> SessionStatus.PENDING
            }

            val finalSession = session.copy(
                doseEventIds = eventIds,
                status = sessionStatus,
                submittedCount = submittedCount
            )
            newSessions.add(finalSession)
        }

        val updatedSessions = existingSessions + newSessions
        val filteredEvents = existingEvents.filter { it.sessionId !in affectedSessionIds } + newEvents

        _sessionsFlow.value = updatedSessions
        _eventsFlow.value = filteredEvents

        jsonStorage.save(sessionsFileName, updatedSessions)
        jsonStorage.save(eventsFileName, filteredEvents)
    }

    suspend fun updateDoseEventStatus(
        eventId: String,
        status: DoseStatus,
        photoPath: String? = null,
        quantityRecorded: Int? = null,
        userAcknowledged: Boolean = true
    ) {
        val currentTime = System.currentTimeMillis()
        val events = _eventsFlow.value.map { event ->
            if (event.eventId == eventId) {
                event.copy(
                    status = status,
                    captureTime = if (photoPath != null && event.captureTime == null) currentTime else event.captureTime,
                    submissionTime = if (status == DoseStatus.TAKEN || status == DoseStatus.SKIPPED) currentTime else event.submissionTime,
                    photoPath = photoPath ?: event.photoPath,
                    quantityRecorded = quantityRecorded ?: event.quantityRecorded,
                    userAcknowledged = userAcknowledged
                )
            } else {
                event
            }
        }
        _eventsFlow.value = events
        jsonStorage.save(eventsFileName, events)

        val updatedEvent = events.find { it.eventId == eventId }
        if (updatedEvent != null) {
            updateSessionStatus(updatedEvent.sessionId, events)
        }
    }

    private suspend fun updateSessionStatus(sessionId: String, allEvents: List<DoseEvent>) {
        val sessionEvents = allEvents.filter { it.sessionId == sessionId }
        if (sessionEvents.isEmpty()) return

        val submittedCount = sessionEvents.count { it.status == DoseStatus.TAKEN || it.status == DoseStatus.SKIPPED || it.status == DoseStatus.MISSED }
        val allTakenOrSkipped = sessionEvents.all { it.status == DoseStatus.TAKEN || it.status == DoseStatus.SKIPPED }

        val sessionStatus = when {
            allTakenOrSkipped -> SessionStatus.COMPLETED
            submittedCount > 0 && submittedCount < sessionEvents.size -> SessionStatus.PARTIAL
            submittedCount == sessionEvents.size -> SessionStatus.COMPLETED
            else -> SessionStatus.PENDING
        }

        val sessions = _sessionsFlow.value.map { session ->
            if (session.sessionId == sessionId) {
                session.copy(
                    status = sessionStatus,
                    submittedCount = sessionEvents.count { it.status == DoseStatus.TAKEN || it.status == DoseStatus.SKIPPED }
                )
            } else {
                session
            }
        }

        _sessionsFlow.value = sessions
        jsonStorage.save(sessionsFileName, sessions)
    }
}
