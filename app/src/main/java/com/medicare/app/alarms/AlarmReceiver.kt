package com.medicare.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.JsonStorage

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "MEDICARE_ALARM_RECEIVER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val executionTime = System.currentTimeMillis()
        Log.d(TAG, "AlarmReceiver.onReceive invoked at timestamp $executionTime with action: ${intent.action}")

        if (intent.action == AlarmScheduler.ACTION_MEDICATION_ALARM || intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val sessionId = intent.getStringExtra(AlarmScheduler.EXTRA_SESSION_ID) ?: "unknown_session"
            val scheduledTime = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME) ?: "08:00"
            val scheduledDate = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_DATE) ?: ""
            val isTest = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_TEST, false)

            Log.d(TAG, "Alarm fired: sessionId=$sessionId, scheduledTime=$scheduledTime, scheduledDate=$scheduledDate, isTest=$isTest, executionTime=$executionTime")

            if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                Log.d(TAG, "Boot completed / package replaced event received, reconciling alarms...")
                AlarmReconciliation.reconcileAlarms(context)
            } else {
                if (!isTest) {
                    try {
                        val jsonStorage = JsonStorage(context)
                        val sessionRepo = SessionRepository(jsonStorage)
                        val events = sessionRepo.getDoseEventsForSession(sessionId)
                        val hasPending = events.isEmpty() || events.any { it.status == DoseStatus.SCHEDULED }
                        if (!hasPending) {
                            Log.w(TAG, "IGNORING ALARM: Session $sessionId and all its doses are already COMPLETED/TAKEN. Cancelling alarm.")
                            AlarmScheduler.cancelAlarm(context, sessionId)
                            return
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking dose pending status in AlarmReceiver", e)
                    }
                }

                val medName = if (isTest) "Test Medication" else "Scheduled Medication (Time: $scheduledTime)"
                AlarmRingService.start(context, sessionId, medName, scheduledTime)
            }
        }
    }
}
