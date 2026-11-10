package com.medicare.app.data.repositories

import android.content.Context
import com.medicare.app.alarms.AlarmScheduler
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.Medication
import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.storage.JsonStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MedicationRepository(private val jsonStorage: JsonStorage) {
    private val fileName = "medications.json"
    private val _medicationsFlow = MutableStateFlow<List<Medication>>(emptyList())
    val medicationsFlow: Flow<List<Medication>> = _medicationsFlow.asStateFlow()

    init {
        loadMedications()
    }

    private fun loadMedications() {
        val list = jsonStorage.load<List<Medication>>(fileName, emptyList(), currentSchemaVersion = 1)
        val normalized = list.map { med ->
            if (med.currentInventory == 0 && med.initialQuantity > 0) {
                med.copy(currentInventory = med.initialQuantity)
            } else {
                med
            }
        }
        _medicationsFlow.value = normalized
    }

    fun getMedications(): List<Medication> {
        loadMedications()
        return _medicationsFlow.value
    }

    fun getMedicationById(id: String): Medication? {
        loadMedications()
        return _medicationsFlow.value.find { it.id == id }
    }

    suspend fun saveMedication(medication: Medication) {
        val current = _medicationsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == medication.id }
        val toSave = if (medication.currentInventory == 0 && medication.initialQuantity > 0 && index < 0) {
            medication.copy(currentInventory = medication.initialQuantity)
        } else {
            medication
        }

        if (index >= 0) {
            current[index] = toSave
        } else {
            current.add(toSave)
        }
        _medicationsFlow.value = current
        jsonStorage.save(fileName, current)
    }

    suspend fun deleteMedication(id: String) {
        val current = _medicationsFlow.value.filter { it.id != id }
        _medicationsFlow.value = current
        jsonStorage.save(fileName, current)
    }

    suspend fun deleteMedication(context: Context, id: String) {
        deleteMedication(id)

        try {
            val sessions = jsonStorage.load<List<MedicineSession>>("medicine_sessions.json", emptyList(), 1)
            val events = jsonStorage.load<List<DoseEvent>>("dose_events.json", emptyList(), 1)

            val removedEventIds = events.filter { it.medicineId == id }.map { it.eventId }.toSet()
            val filteredEvents = events.filter { it.medicineId != id }
            val filteredSessions = sessions.map { session ->
                val newEventIds = session.doseEventIds.filter { it !in removedEventIds }
                session.copy(doseEventIds = newEventIds, totalMedicineCount = newEventIds.size)
            }.filter { it.doseEventIds.isNotEmpty() }

            jsonStorage.save("medicine_sessions.json", filteredSessions)
            jsonStorage.save("dose_events.json", filteredEvents)

            AlarmScheduler.cancelAlarm(context, id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun toggleActive(id: String) {
        val current = _medicationsFlow.value.map { med ->
            if (med.id == id) med.copy(active = !med.active) else med
        }
        _medicationsFlow.value = current
        jsonStorage.save(fileName, current)
    }

    suspend fun reconcileInventory(id: String, newInventoryCount: Int) {
        val current = _medicationsFlow.value.map { med ->
            if (med.id == id) {
                med.copy(
                    currentInventory = newInventoryCount.coerceAtLeast(0),
                    manualAdjustments = med.manualAdjustments + System.currentTimeMillis()
                )
            } else {
                med
            }
        }
        _medicationsFlow.value = current
        jsonStorage.save(fileName, current)
    }

    suspend fun decrementInventory(id: String, amount: Int) {
        val current = _medicationsFlow.value.map { med ->
            if (med.id == id) {
                val newInv = (med.currentInventory - amount).coerceAtLeast(0)
                med.copy(currentInventory = newInv)
            } else {
                med
            }
        }
        _medicationsFlow.value = current
        jsonStorage.save(fileName, current)
    }
}
