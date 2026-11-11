package com.medicare.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.Medication
import com.medicare.app.data.repositories.HistoryRepository
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.storage.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val historyRepository = HistoryRepository(database)
    private val medicationRepository = MedicationRepository(database)

    val doseEvents: StateFlow<List<DoseEvent>> = historyRepository.observeAllDoseEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val medications: StateFlow<List<Medication>> = medicationRepository.medicationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun calculate60DayAdherence(): Float {
        val calendar = Calendar.getInstance()
        val endDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -60)
        val startDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val events = doseEvents.value.filter { it.scheduledDate in startDateStr..endDateStr }
        if (events.isEmpty()) return 0f
        val eligible = events.count { it.status in listOf(com.medicare.app.data.models.DoseStatus.TAKEN, com.medicare.app.data.models.DoseStatus.MISSED, com.medicare.app.data.models.DoseStatus.SKIPPED) }
        if (eligible == 0) return 0f
        return events.count { it.status == com.medicare.app.data.models.DoseStatus.TAKEN }.toFloat() / eligible
    }
}
