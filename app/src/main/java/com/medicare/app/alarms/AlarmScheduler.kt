package com.medicare.app.alarms

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.net.Uri
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.MedicineSession
import com.medicare.app.data.storage.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

object AlarmScheduler {
    const val TAG = "MEDICARE_SCHEDULER"
    const val ACTION_MEDICATION_ALARM = "com.medicare.app.ACTION_MEDICATION_ALARM"
    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
    const val EXTRA_SCHEDULED_DATE = "extra_scheduled_date"
    const val EXTRA_IS_TEST = "extra_is_test"
    const val EXTRA_DOSE_ID = "extra_dose_id"
    const val EXTRA_ALARM_KIND = "extra_alarm_kind"
    const val EXTRA_EXPECTED_TRIGGER_AT = "extra_expected_trigger_at"
    enum class AlarmKind { ORIGINAL, SNOOZE, RETRY }

    fun scheduleSessionAlarm(context: Context, session: MedicineSession) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.get(context).doses().forSession(session.sessionId).forEach { event ->
                if (event.status.name == "SCHEDULED" || event.status.name == "PENDING_VERIFICATION") {
                    scheduleDoseAlarm(context, event, AlarmKind.ORIGINAL, parseDateTimeToMillis(event.scheduledDate, event.scheduledTime))
                }
            }
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleDoseAlarm(context: Context, dose: DoseEvent, kind: AlarmKind, triggerAt: Long) {
        if (dose.status.name !in setOf("SCHEDULED", "PENDING_VERIFICATION")) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelDoseAlarms(context, dose.eventId, except = kind)
        val fireAt = triggerAt.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        val pending = dosePendingIntent(context, dose, kind, fireAt)
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, pending), pending)
            Log.i(TAG, "Scheduled $kind alarm dose=${dose.eventId} requestedAt=${System.currentTimeMillis()} triggerAt=$fireAt")
        } catch (error: SecurityException) {
            Log.e(TAG, "Exact alarm access unavailable for dose=${dose.eventId}", error)
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending) }
                .onFailure { Log.e(TAG, "Could not schedule fallback alarm for dose=${dose.eventId}", it) }
        } catch (error: Exception) {
            Log.e(TAG, "Alarm scheduling failed for dose=${dose.eventId}", error)
        }
    }

    private fun dosePendingIntent(context: Context, dose: DoseEvent, kind: AlarmKind, triggerAt: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_ALARM
            data = Uri.parse("medicare://dose/${dose.eventId}/${kind.name.lowercase()}")
            putExtra(EXTRA_DOSE_ID, dose.eventId)
            putExtra(EXTRA_SESSION_ID, dose.sessionId)
            putExtra(EXTRA_SCHEDULED_TIME, dose.scheduledTime)
            putExtra(EXTRA_SCHEDULED_DATE, dose.scheduledDate)
            putExtra(EXTRA_ALARM_KIND, kind.name)
            putExtra(EXTRA_EXPECTED_TRIGGER_AT, triggerAt)
        }
        val requestCode = 31 * dose.eventId.hashCode() + kind.ordinal
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun cancelDoseAlarms(context: Context, doseId: String, except: AlarmKind? = null) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        AlarmKind.entries.forEach { kind ->
            if (kind == except) return@forEach
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_MEDICATION_ALARM
                data = Uri.parse("medicare://dose/$doseId/${kind.name.lowercase()}")
            }
            val pending = PendingIntent.getBroadcast(context, 31 * doseId.hashCode() + kind.ordinal, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pending != null) {
                manager.cancel(pending)
                pending.cancel()
            }
        }
    }

    fun scheduleSnoozeById(context: Context, sessionId: String, scheduledTime: String, snoozeMinutes: Int = 5) {
        Log.w(TAG, "Ignoring legacy session-only snooze request for $sessionId ($scheduledTime)")
    }

    fun scheduleTestAlarm(context: Context, delayMinutes: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { AlarmTestManager.schedule(context, delayMinutes) }
                .onFailure { Log.e(TAG, "Could not schedule isolated alarm test", it) }
        }
    }

    fun scheduleSnooze(context: Context, session: MedicineSession, snoozeMinutes: Int = 5) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.get(context).doses().nextPending(session.scheduledDate, session.scheduledTime)?.let { dose ->
                val triggerAt = AlarmTiming.snoozeTriggerAt(System.currentTimeMillis(), snoozeMinutes)
                if (AppDatabase.get(context).doses().retry(dose.eventId, triggerAt) == 1) {
                    scheduleDoseAlarm(context, dose.copy(nextReminderAt = triggerAt), AlarmKind.SNOOZE, triggerAt)
                }
            }
        }
    }

    fun cancelAlarm(context: Context, sessionId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled alarm for session $sessionId")
    }

    fun cancelAllTestAlarms(context: Context) {
        val registry = context.getSharedPreferences("medicare_alarm_registry", Context.MODE_PRIVATE)
        registry.all.filterKeys { it.startsWith("test_") }.values.filterIsInstance<String>()
            .forEach { cancelDoseAlarms(context, it) }
    }

    fun scheduledTimeMillis(dateStr: String, timeStr: String): Long = parseDateTimeToMillis(dateStr, timeStr)

    private fun parseDateTimeToMillis(dateStr: String, timeStr: String): Long {
        return try {
            val dateParts = dateStr.split("-")
            val timeParts = timeStr.split(":")
            val year = dateParts[0].toInt()
            val month = dateParts[1].toInt() - 1
            val day = dateParts[2].toInt()
            val hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()

            val calendar = Calendar.getInstance().apply {
                set(year, month, day, hour, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing date/time: date=$dateStr, time=$timeStr", e)
            System.currentTimeMillis() + 60000L
        }
    }
}
