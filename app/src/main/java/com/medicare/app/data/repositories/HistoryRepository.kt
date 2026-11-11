package com.medicare.app.data.repositories

import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.storage.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class HistoryRepository(private val db: AppDatabase) {
    fun observeAllStoredDoseEvents(): Flow<List<DoseEvent>> = db.doses().observeAll()

    fun observeAllDoseEvents(): Flow<List<DoseEvent>> = observeAllStoredDoseEvents().map { events ->
        val today = LocalDate.now().toString()
        events.filter { eligibleForHistory(it, today) }
    }

    companion object {
        fun eligibleForHistory(event: DoseEvent, today: String): Boolean =
            event.status == DoseStatus.TAKEN && event.userAcknowledged && event.submissionTime != null && event.scheduledDate <= today
    }

    suspend fun calculateAdherenceRate(startDate: String, endDate: String): Float {
        val events = db.doses().between(startDate, endDate).filter { it.scheduledDate <= endDate && (it.status in setOf(DoseStatus.MISSED, DoseStatus.SKIPPED) || (it.status == DoseStatus.TAKEN && it.userAcknowledged && it.submissionTime != null)) }
        if (events.isEmpty()) return 0f
        val takenCount = events.count { it.status == DoseStatus.TAKEN && it.userAcknowledged && it.submissionTime != null }
        val totalValid = events.size
        if (totalValid == 0) return 0f
        return takenCount.toFloat() / totalValid.toFloat()
    }
}
