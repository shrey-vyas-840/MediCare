package com.medicare.app.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.storage.AppDatabase

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "MEDICARE_ALARM_RECEIVER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> AlarmReconciliation.reconcileAlarms(context)
            AlarmScheduler.ACTION_MEDICATION_ALARM -> {
                if (intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_TEST, false)) {
                    AlarmRingService.start(context, "", intent.getStringExtra(AlarmScheduler.EXTRA_SESSION_ID).orEmpty(), "Test medicine", intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME).orEmpty())
                    return
                }
                val pendingResult = goAsync()
                val doseId = intent.getStringExtra(AlarmScheduler.EXTRA_DOSE_ID)
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
                        AlarmRingService.start(context, doseId, dose.sessionId, medicine!!.name, dose.scheduledTime)
                    } catch (error: Exception) {
                        Log.e(TAG, "Alarm validation failed for dose=$doseId; no alarm shown", error)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
