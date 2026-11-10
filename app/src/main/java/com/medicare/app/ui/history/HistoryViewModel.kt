package com.medicare.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.Medication
import com.medicare.app.data.repositories.HistoryRepository
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.storage.JsonStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val jsonStorage = JsonStorage(application)
    private val historyRepository = HistoryRepository(jsonStorage)
    private val medicationRepository = MedicationRepository(jsonStorage)

    val doseEvents: StateFlow<List<DoseEvent>> = historyRepository.observeAllDoseEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val medications: StateFlow<List<Medication>> = medicationRepository.medicationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun calculate60DayAdherence(): Float {
        val calendar = Calendar.getInstance()
        val endDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -60)
        val startDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        return historyRepository.calculateAdherenceRate(startDateStr, endDateStr)
    }
}
