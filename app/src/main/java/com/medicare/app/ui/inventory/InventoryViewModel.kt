package com.medicare.app.ui.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medicare.app.data.models.Medication
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.storage.JsonStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val jsonStorage = JsonStorage(application)
    private val medicationRepository = MedicationRepository(jsonStorage)

    val medications: StateFlow<List<Medication>> = medicationRepository.medicationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun reconcileInventory(medicationId: String, newCount: Int) {
        viewModelScope.launch {
            medicationRepository.reconcileInventory(medicationId, newCount)
        }
    }
}
