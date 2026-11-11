package com.medicare.app.data.repositories

import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.storage.AppDatabase
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val db: AppDatabase) {
    fun observeAllDoseEvents(): Flow<List<DoseEvent>> = db.doses().observeAll()

    suspend fun calculateAdherenceRate(startDate: String, endDate: String): Float {
        val events = db.doses().between(startDate, endDate)
        if (events.isEmpty()) return 0f
        val takenCount = events.count { it.status == DoseStatus.TAKEN }
        val totalValid = events.count { it.status == DoseStatus.TAKEN || it.status == DoseStatus.MISSED || it.status == DoseStatus.SKIPPED }
        if (totalValid == 0) return 0f
        return takenCount.toFloat() / totalValid.toFloat()
    }
}
