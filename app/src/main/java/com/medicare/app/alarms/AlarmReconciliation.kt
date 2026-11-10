package com.medicare.app.alarms

import android.content.Context
import com.medicare.app.data.models.SessionStatus
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.JsonStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlarmReconciliation {
    fun reconcileAlarms(context: Context) {
        val jsonStorage = JsonStorage(context)
        val medicationRepository = MedicationRepository(jsonStorage)
        val sessionRepository = SessionRepository(jsonStorage)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        CoroutineScope(Dispatchers.IO).launch {
            val meds = medicationRepository.getMedications()
            sessionRepository.generateSessionsForDate(todayStr, meds)
            val sessions = sessionRepository.getSessionsForDate(todayStr)

            for (session in sessions) {
                if (session.status != SessionStatus.COMPLETED) {
                    AlarmScheduler.scheduleSessionAlarm(context, session)
                }
            }
        }
    }
}
