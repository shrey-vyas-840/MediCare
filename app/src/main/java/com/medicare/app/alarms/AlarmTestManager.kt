package com.medicare.app.alarms

import android.content.Context
import androidx.room.withTransaction
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.Frequency
import com.medicare.app.data.models.Medication
import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.storage.AppDatabase
import java.util.UUID

/** Creates isolated synthetic Room rows so the Settings test buttons exercise the real dose alarm and snooze path. */
object AlarmTestManager {
    const val PREFIX = "__medicare_alarm_test__"

    fun isTestDose(id: String): Boolean = id.startsWith(PREFIX)

    suspend fun schedule(context: Context, delayMinutes: Int) {
        require(delayMinutes > 0)
        val db = AppDatabase.get(context)
        val suffix = UUID.randomUUID().toString()
        val medicineId = "$PREFIX" + "medicine_$suffix"
        val sessionId = "$PREFIX" + "session_$suffix"
        val doseId = "$PREFIX" + "dose_$suffix"
        val time = "08:00"
        val date = "2099-01-01"
        val medicine = Medication(medicineId, "MediCare alarm test", 1.0, "tablet", listOf(time), Frequency.DAILY, active = true, initialQuantity = 1, currentInventory = 1)
        val session = MedicineSession(sessionId, date, time, listOf(doseId), totalMedicineCount = 1)
        val dose = DoseEvent(doseId, sessionId, medicineId, date, time)
        db.withTransaction {
            db.medications().upsert(medicine)
            db.sessions().upsert(session)
            db.doses().upsert(dose)
        }
        context.getSharedPreferences("medicare_alarm_registry", Context.MODE_PRIVATE).edit()
            .putString("test_${doseId.hashCode()}", doseId).apply()
        val triggerAt = AlarmTiming.snoozeTriggerAt(System.currentTimeMillis(), delayMinutes)
        AlarmScheduler.scheduleDoseAlarm(context, dose, AlarmScheduler.AlarmKind.ORIGINAL, triggerAt)
    }

    suspend fun cleanup(context: Context, doseId: String) {
        if (!isTestDose(doseId)) return
        val db = AppDatabase.get(context)
        val dose = db.doses().byId(doseId)
        AlarmScheduler.cancelDoseAlarms(context, doseId)
        if (dose != null) {
            AlarmScheduler.cancelAlarm(context, dose.sessionId)
            db.withTransaction {
                db.doses().deleteById(dose.eventId)
                db.sessions().deleteById(dose.sessionId)
                db.medications().deleteById(dose.medicineId)
            }
        }
        context.getSharedPreferences("medicare_alarm_registry", Context.MODE_PRIVATE).edit().remove("test_${doseId.hashCode()}").apply()
    }

    suspend fun cleanupAll(context: Context) {
        val registry = context.getSharedPreferences("medicare_alarm_registry", Context.MODE_PRIVATE)
        val ids = registry.all.filterKeys { it.startsWith("test_") }.values.filterIsInstance<String>()
        ids.forEach { cleanup(context, it) }
        registry.edit().clear().apply()
    }
}
