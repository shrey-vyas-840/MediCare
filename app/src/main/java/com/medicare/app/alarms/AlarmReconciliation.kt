package com.medicare.app.alarms

import android.content.Context
import com.medicare.app.data.models.SessionStatus
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

object AlarmReconciliation {
    fun reconcileAlarms(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.get(context)
            val medicationRepository = MedicationRepository(db)
            val sessionRepository = SessionRepository(db)
            val meds = medicationRepository.getMedications()
            val today = LocalDate.now()
            repeat(8) { offset ->
                val date = today.plusDays(offset.toLong()).toString()
                sessionRepository.generateSessionsForDate(date, meds)
                val sessions = sessionRepository.getSessionsForDate(date)
                for (session in sessions) {
                    AlarmScheduler.cancelAlarm(context, session.sessionId) // cancel pre-dose-ID alarms from older builds
                    val sessionDoses = sessionRepository.getDoseEventsForSession(session.sessionId)
                    sessionDoses.forEach { dose ->
                        val activeMedicine = medicationRepository.getMedicationById(dose.medicineId)?.active == true
                        if (!activeMedicine || dose.status.name !in setOf("SCHEDULED", "PENDING_VERIFICATION")) {
                            AlarmScheduler.cancelDoseAlarms(context, dose.eventId)
                        } else {
                            val retryAt = dose.nextReminderAt?.takeIf { it > System.currentTimeMillis() }
                            val kind = if (retryAt == null) AlarmScheduler.AlarmKind.ORIGINAL else AlarmScheduler.AlarmKind.RETRY
                            val at = retryAt ?: AlarmScheduler.scheduledTimeMillis(dose.scheduledDate, dose.scheduledTime)
                            AlarmScheduler.scheduleDoseAlarm(context, dose, kind, at)
                        }
                    }
                    if (sessionDoses.any { it.status.name in setOf("SCHEDULED", "PENDING_VERIFICATION") }) {
                        EmergencyCheckScheduler.schedule(context, session)
                    } else EmergencyCheckScheduler.cancel(context, session.sessionId)
                }
            }
        }
    }
}
