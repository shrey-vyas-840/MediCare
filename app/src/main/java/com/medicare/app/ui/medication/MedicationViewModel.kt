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
import com.medicare.app.data.models.SessionStatus
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.JsonStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MedicationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MedicationRepository(JsonStorage(application))

    val medications: StateFlow<List<Medication>> = repository.medicationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveMedication(medication: Medication) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            Log.d("MEDICARE_MEDICINE_SAVE", "Saving medication: id=${medication.id}, name=${medication.name}, times=${medication.times}, dosage=${medication.dosage} ${medication.unit}")

            repository.saveMedication(medication)

            val jsonStorage = JsonStorage(context)
            val sessionRepo = SessionRepository(jsonStorage)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(Date())

            val meds = repository.getMedications()
            sessionRepo.generateSessionsForDate(todayStr, meds)
            val sessions = sessionRepo.getSessionsForDate(todayStr)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true
            Log.d("MEDICARE_SCHEDULER", "Post-save session generation complete. Total sessions for today ($todayStr): ${sessions.size}, Exact alarm permission: $canExact")

            for (session in sessions) {
                if (session.status != SessionStatus.COMPLETED) {
                    AlarmScheduler.scheduleSessionAlarm(context, session)
                }
            }
        }
    }

    fun deleteMedication(id: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            Log.d("MEDICARE_MEDICINE_SAVE", "Deleting medication: id=$id")
            repository.deleteMedication(context, id)
        }
    }

    fun toggleActive(id: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            Log.d("MEDICARE_MEDICINE_SAVE", "Toggling active for medication: id=$id")
            repository.toggleActive(id)

            val jsonStorage = JsonStorage(context)
            val sessionRepo = SessionRepository(jsonStorage)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(Date())

            val meds = repository.getMedications()
            sessionRepo.generateSessionsForDate(todayStr, meds)
            val sessions = sessionRepo.getSessionsForDate(todayStr)
            for (session in sessions) {
                if (session.status != SessionStatus.COMPLETED) {
                    AlarmScheduler.scheduleSessionAlarm(context, session)
                }
            }
        }
    }

    fun getMedicationById(id: String): Medication? {
        return repository.getMedicationById(id)
    }
}
