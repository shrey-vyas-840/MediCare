package com.medicare.app.data.repositories

import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.storage.JsonStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryRepository(private val jsonStorage: JsonStorage) {
    private val eventsFileName = "dose_events.json"
    private val _eventsFlow = MutableStateFlow<List<DoseEvent>>(emptyList())
    val eventsFlow: Flow<List<DoseEvent>> = _eventsFlow.asStateFlow()

    init {
        loadEvents()
    }

    private fun loadEvents() {
        val list = jsonStorage.load<List<DoseEvent>>(eventsFileName, emptyList(), 1)
        _eventsFlow.value = list
    }

    fun getAllDoseEvents(): List<DoseEvent> {
        loadEvents()
        return _eventsFlow.value
    }

    fun observeAllDoseEvents(): Flow<List<DoseEvent>> {
        loadEvents()
        return eventsFlow
    }

    fun getDoseEventsBetween(startDate: String, endDate: String): List<DoseEvent> {
        loadEvents()
        return _eventsFlow.value.filter { it.scheduledDate in startDate..endDate }
    }

    fun calculateAdherenceRate(startDate: String, endDate: String): Float {
        val events = getDoseEventsBetween(startDate, endDate)
        if (events.isEmpty()) return 0f
        val takenCount = events.count { it.status == DoseStatus.TAKEN }
        val totalValid = events.count { it.status == DoseStatus.TAKEN || it.status == DoseStatus.MISSED || it.status == DoseStatus.SKIPPED }
        if (totalValid == 0) return 0f
        return takenCount.toFloat() / totalValid.toFloat()
    }
}
