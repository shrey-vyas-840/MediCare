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
        val pending = dosePendingIntent(context, dose, kind)
        val fireAt = triggerAt.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, pending), pending)
            Log.i(TAG, "Scheduled $kind alarm for dose=${dose.eventId} at=$fireAt")
        } catch (error: SecurityException) {
            Log.e(TAG, "Exact alarm access unavailable for dose=${dose.eventId}", error)
            runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending) }
                .onFailure { Log.e(TAG, "Could not schedule fallback alarm for dose=${dose.eventId}", it) }
        } catch (error: Exception) {
            Log.e(TAG, "Alarm scheduling failed for dose=${dose.eventId}", error)
        }
    }

    private fun dosePendingIntent(context: Context, dose: DoseEvent, kind: AlarmKind): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_ALARM
            data = Uri.parse("medicare://dose/${dose.eventId}/${kind.name.lowercase()}")
            putExtra(EXTRA_DOSE_ID, dose.eventId)
            putExtra(EXTRA_SESSION_ID, dose.sessionId)
            putExtra(EXTRA_SCHEDULED_TIME, dose.scheduledTime)
            putExtra(EXTRA_SCHEDULED_DATE, dose.scheduledDate)
            putExtra(EXTRA_ALARM_KIND, kind.name)
        }
        val requestCode = 31 * dose.eventId.hashCode() + kind.ordinal
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun cancelDoseAlarms(context: Context, doseId: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        AlarmKind.entries.forEach { kind ->
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
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val testSessionId = "test_alarm_${System.currentTimeMillis()}"
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_ALARM
            putExtra(EXTRA_SESSION_ID, testSessionId)
            putExtra(EXTRA_SCHEDULED_TIME, "+${delayMinutes}m")
            putExtra(EXTRA_SCHEDULED_DATE, "Today (Test)")
            putExtra(EXTRA_IS_TEST, true)
        }

        val requestCode = testSessionId.hashCode()
        intent.data = Uri.parse("medicare://test/$testSessionId")
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTimeMillis = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        Log.d(TAG, "Scheduling TEST AlarmClock: delayMinutes=$delayMinutes, triggerMillis=$triggerTimeMillis")

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: Exception) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        }
    }

    fun scheduleSnooze(context: Context, session: MedicineSession, snoozeMinutes: Int = 5) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.get(context).doses().nextPending(session.scheduledDate, session.scheduledTime)?.let { dose ->
                scheduleDoseAlarm(context, dose, AlarmKind.SNOOZE, System.currentTimeMillis() + snoozeMinutes * 60_000L)
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
