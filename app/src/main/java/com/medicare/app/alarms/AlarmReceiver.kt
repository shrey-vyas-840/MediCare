package com.medicare.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.storage.AppDatabase
import com.medicare.app.data.storage.PreferencesManager
import kotlinx.coroutines.flow.first

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "MEDICARE_ALARM_RECEIVER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> AlarmReconciliation.reconcileAlarms(context)
            AlarmScheduler.ACTION_MEDICATION_ALARM -> {
                if (intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_TEST, false)) {
                    val testId = intent.getStringExtra(AlarmScheduler.EXTRA_SESSION_ID).orEmpty()
                    context.getSharedPreferences("medicare_alarm_registry", Context.MODE_PRIVATE).edit().remove("test_${testId.hashCode()}").apply()
                    AlarmRingService.start(context, "", testId, "Test medicine", intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME).orEmpty())
                    return
                }
                val pendingResult = goAsync()
                val alarmWakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MediCare:AlarmValidation").apply {
                        setReferenceCounted(false)
                        acquire(30_000L)
                    }
                val doseId = intent.getStringExtra(AlarmScheduler.EXTRA_DOSE_ID)
                val kind = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND)
                val expectedAt = intent.getLongExtra(AlarmScheduler.EXTRA_EXPECTED_TRIGGER_AT, 0L)
                val deliveredAt = System.currentTimeMillis()
                Log.i(TAG, "Alarm delivery dose=$doseId kind=$kind expectedAt=$expectedAt deliveredAt=$deliveredAt delayMs=${if (expectedAt > 0L) AlarmTiming.deliveryDelayMillis(expectedAt, deliveredAt) else "unknown"}")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (doseId.isNullOrBlank()) return@launch
                        val db = AppDatabase.get(context)
                        val dose = db.doses().byId(doseId)
                        val medicine = dose?.let { db.medications().byId(it.medicineId) }
                        val stillValid = dose != null && medicine?.active == true &&
                            dose.sessionId == intent.getStringExtra(AlarmScheduler.EXTRA_SESSION_ID) &&
                            dose.scheduledDate == intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_DATE) &&
                            dose.scheduledTime == intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME) &&
                            dose.status in setOf(DoseStatus.SCHEDULED, DoseStatus.PENDING_VERIFICATION)
                        if (!stillValid) {
                            Log.w(TAG, "Ignoring stale alarm for dose=$doseId")
                            AlarmScheduler.cancelDoseAlarms(context, doseId)
                            return@launch
                        }
                        if (kind == AlarmScheduler.AlarmKind.SNOOZE.name || kind == AlarmScheduler.AlarmKind.RETRY.name) {
                            db.doses().clearRetry(doseId)
                        }
                        AlarmScheduler.cancelDoseAlarms(context, doseId)
                        val preferences = PreferencesManager(context)
                        val patientName = preferences.userNameFlow.first()
                        val ringtoneUri = preferences.ringtoneUriFlow.first()
                        AlarmRingService.start(context, doseId, dose.sessionId, medicine!!.name, dose.scheduledTime, patientName, ringtoneUri)
                    } catch (error: Exception) {
                        Log.e(TAG, "Alarm validation failed for dose=$doseId; no alarm shown", error)
                    } finally {
                        if (alarmWakeLock.isHeld) alarmWakeLock.release()
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
