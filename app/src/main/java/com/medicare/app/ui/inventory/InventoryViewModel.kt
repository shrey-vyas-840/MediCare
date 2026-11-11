package com.medicare.app.ui.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medicare.app.data.models.Medication
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val medicationRepository = MedicationRepository(db)
    private val sessionRepository = SessionRepository(db)

    val medications: StateFlow<List<Medication>> = medicationRepository.medicationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun reconcileInventory(medicationId: String, newCount: Int) {
        viewModelScope.launch {
            medicationRepository.reconcileInventory(medicationId, newCount)
        }
    }

    fun observeUsage(medicineId: String) = db.asNeededUsage().observeMedicine(medicineId)
    fun markOneTabletUsed(medicineId: String) {
        viewModelScope.launch { sessionRepository.recordAsNeededUse(medicineId, 1) }
    }
}
