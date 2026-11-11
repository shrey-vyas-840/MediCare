package com.medicare.app.ui.medication

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medicare.app.alarms.AlarmScheduler
import com.medicare.app.data.models.Medication
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.AppDatabase
import com.medicare.app.alarms.AlarmReconciliation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MedicationViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val repository = MedicationRepository(database)

    val medications: StateFlow<List<Medication>> = repository.medicationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveMedication(medication: Medication) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            Log.d("MEDICARE_MEDICINE_SAVE", "Saving medication: id=${medication.id}, name=${medication.name}, times=${medication.times}, dosage=${medication.dosage} ${medication.unit}")

            repository.saveMedication(medication)

            val sessionRepo = SessionRepository(database)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(Date())

            val meds = repository.getMedications()
            sessionRepo.generateSessionsForDate(todayStr, meds)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true
            Log.d("MEDICARE_SCHEDULER", "Medication saved, occurrences regenerated, exact alarm access=$canExact")
            AlarmReconciliation.reconcileAlarms(context)
        }
    }

    fun deleteMedication(id: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            Log.d("MEDICARE_MEDICINE_SAVE", "Deleting medication: id=$id")
            val events = repository.pendingDoses(id)
            repository.deleteMedication(id)
            events.forEach { event ->
                SessionRepository(database).cancelDose(event.eventId)
                AlarmScheduler.cancelDoseAlarms(context, event.eventId)
            }
        }
    }

    fun toggleActive(id: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            Log.d("MEDICARE_MEDICINE_SAVE", "Toggling active for medication: id=$id")
            repository.toggleActive(id)

            val sessionRepo = SessionRepository(database)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(Date())

            val meds = repository.getMedications()
            val medicine = meds.firstOrNull { it.id == id }
            if (medicine?.active == false) {
                repository.pendingDoses(id).forEach { event ->
                    sessionRepo.cancelDose(event.eventId)
                    AlarmScheduler.cancelDoseAlarms(context, event.eventId)
                }
            }
            sessionRepo.generateSessionsForDate(todayStr, meds)
            AlarmReconciliation.reconcileAlarms(context)
        }
    }

}
