package com.medicare.app.data.repositories

import com.medicare.app.data.models.Medication
import com.medicare.app.data.storage.AppDatabase
import kotlinx.coroutines.flow.Flow

class MedicationRepository(private val db: AppDatabase) {
    val medicationsFlow: Flow<List<Medication>> = db.medications().observeAll()
    suspend fun getMedications(): List<Medication> = db.medications().all()
    suspend fun getMedicationById(id: String): Medication? = db.medications().byId(id)
    suspend fun saveMedication(medication: Medication) {
        val existing = db.medications().byId(medication.id)
        val toSave = if (existing == null && medication.currentInventory == 0 && medication.initialQuantity > 0) medication.copy(currentInventory = medication.initialQuantity) else medication
        db.medications().upsert(toSave)
    }
    suspend fun deleteMedication(id: String) = db.medications().archive(id)
    suspend fun pendingDoses(id: String) = db.doses().pendingForMedicine(id)
    suspend fun toggleActive(id: String) {
        db.medications().byId(id)?.let { db.medications().upsert(it.copy(active = !it.active)) }
    }
    suspend fun reconcileInventory(id: String, newInventoryCount: Int) {
        db.medications().byId(id)?.let { med ->
            db.medications().setInventory(id, newInventoryCount.coerceIn(0, med.initialQuantity), med.manualAdjustments + System.currentTimeMillis())
        }
    }
    suspend fun decrementInventory(id: String, amount: Int) {
        require(amount >= 0)
        db.medications().decrement(id, amount)
    }
}
