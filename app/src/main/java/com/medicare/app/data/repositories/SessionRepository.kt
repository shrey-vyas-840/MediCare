package com.medicare.app.data.repositories

import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.models.Medication
import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.models.SessionStatus
import androidx.room.withTransaction
import com.medicare.app.data.storage.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SessionRepository(private val db: AppDatabase) {
    fun observeSessionsForDate(date: String): Flow<List<MedicineSession>> = db.sessions().observeDate(date)
    suspend fun getSessionsForDate(date: String): List<MedicineSession> = db.sessions().forDate(date)
    fun observeDoseEventsForSession(sessionId: String): Flow<List<DoseEvent>> = db.doses().observeSession(sessionId)
    suspend fun getDoseEventsForSession(sessionId: String): List<DoseEvent> = db.doses().forSession(sessionId)
    suspend fun getDoseEvent(eventId: String): DoseEvent? = db.doses().byId(eventId)

    suspend fun generateSessionsForDate(date: String, medications: List<Medication>) {
        val timeToMeds = sortedMapOf<String, MutableList<Medication>>()
        for (med in medications) {
            if (!med.active) continue
            for (time in med.times.distinct()) {
                timeToMeds.getOrPut(time) { mutableListOf() }.add(med)
            }
        }
        db.withTransaction {
            val scheduledOccurrences = timeToMeds.flatMap { (time, meds) -> meds.map { it.id to time } }.toSet()
            db.doses().forDate(date).forEach { existing ->
                if ((existing.medicineId to existing.scheduledTime) !in scheduledOccurrences &&
                    existing.status in setOf(DoseStatus.SCHEDULED, DoseStatus.PENDING_VERIFICATION)) {
                    db.doses().cancelPending(existing.eventId)
                }
            }
            for ((time, meds) in timeToMeds) {
                val session = db.sessions().byOccurrence(date, time) ?: MedicineSession(
                    scheduledDate = date, scheduledTime = time, doseEventIds = emptyList()
                ).also { db.sessions().upsert(it) }
                val eventIds = db.doses().forSession(session.sessionId).map { it.eventId }.toMutableList()
                for (med in meds) {
                    val existing = db.doses().occurrence(med.id, date, time)
                    val event = when {
                        existing == null -> DoseEvent(sessionId = session.sessionId, medicineId = med.id, scheduledDate = date, scheduledTime = time).also { db.doses().upsert(it) }
                        existing.status == DoseStatus.CANCELLED -> existing.copy(sessionId = session.sessionId, status = DoseStatus.SCHEDULED, nextReminderAt = null).also { db.doses().upsert(it) }
                        existing.sessionId != session.sessionId -> existing.copy(sessionId = session.sessionId).also { db.doses().upsert(it) }
                        else -> existing
                    }
                    if (event.eventId !in eventIds) eventIds += event.eventId
                }
                val events = db.doses().forSession(session.sessionId)
                val activeEvents = events.filter { it.status != DoseStatus.CANCELLED }
                val completed = activeEvents.count { it.status == DoseStatus.TAKEN }
                val status = when {
                    activeEvents.isEmpty() -> SessionStatus.CANCELLED
                    completed == activeEvents.size -> SessionStatus.COMPLETED
                    completed > 0 -> SessionStatus.PARTIAL
                    else -> SessionStatus.PENDING
                }
                db.sessions().upsert(session.copy(doseEventIds = eventIds, status = status, submittedCount = completed, totalMedicineCount = activeEvents.size))
            }
            db.sessions().forDate(date).forEach { refreshSession(it.sessionId) }
        }
    }

    suspend fun updateDoseEventStatus(
        eventId: String,
        status: DoseStatus,
        photoPath: String? = null,
        quantityRecorded: Int? = null,
        userAcknowledged: Boolean = true
    ) {
        val event = db.doses().byId(eventId) ?: return
        if (event.status == DoseStatus.TAKEN || event.status == DoseStatus.CANCELLED) return
        if (status == DoseStatus.TAKEN) {
            if (!event.photoPath.isNullOrBlank()) completeDose(eventId, event.photoPath, event.captureTime ?: System.currentTimeMillis(), quantityRecorded ?: 1)
            return
        }
        db.withTransaction {
            db.doses().setStatus(eventId, status, quantityRecorded, userAcknowledged)
            refreshSession(event.sessionId)
        }
    }

    suspend fun saveCapturedPhoto(eventId: String, uri: String, capturedAt: Long): Boolean = db.doses().savePhoto(eventId, uri, capturedAt) == 1

    suspend fun completeDose(eventId: String, uri: String, capturedAt: Long, quantity: Int): Boolean = db.withTransaction {
        require(quantity > 0)
        val event = db.doses().byId(eventId) ?: return@withTransaction false
        if (event.status == DoseStatus.TAKEN || event.status == DoseStatus.CANCELLED) return@withTransaction false
        if (event.photoPath != uri || event.captureTime != capturedAt) return@withTransaction false
        val medicine = db.medications().byId(event.medicineId) ?: return@withTransaction false
        if (db.doses().complete(eventId, System.currentTimeMillis(), quantity) != 1) return@withTransaction false
        db.medications().decrement(medicine.id, quantity)
        refreshSession(event.sessionId)
        true
    }

    suspend fun scheduleRetry(eventId: String, retryAt: Long): Boolean {
        val event = db.doses().byId(eventId) ?: return false
        return db.doses().retry(eventId, retryAt) == 1
    }

    suspend fun cancelDose(eventId: String): Boolean {
        val event = db.doses().byId(eventId) ?: return false
        return db.withTransaction {
            val changed = db.doses().cancelPending(eventId) == 1
            if (changed) refreshSession(event.sessionId)
            changed
        }
    }

    suspend fun nextOutstanding(sessionId: String): DoseEvent? = db.doses().forSession(sessionId)
        .firstOrNull { it.status == DoseStatus.SCHEDULED || it.status == DoseStatus.PENDING_VERIFICATION }

    private suspend fun refreshSession(sessionId: String) {
        val events = db.doses().forSession(sessionId)
        if (events.isEmpty()) return
        val active = events.filter { it.status != DoseStatus.CANCELLED }
        val done = active.count { it.status == DoseStatus.TAKEN }
        val status = when {
            active.isEmpty() -> SessionStatus.CANCELLED
            done == active.size -> SessionStatus.COMPLETED
            done > 0 -> SessionStatus.PARTIAL
            else -> SessionStatus.PENDING
        }
        db.sessions().updateSummary(sessionId, status, done, events.size)
    }
}
