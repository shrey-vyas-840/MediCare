package com.medicare.app.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.medicare.app.data.models.MedicineSession
import java.util.Calendar
import java.util.TimeZone

object AlarmScheduler {
    const val TAG = "MEDICARE_SCHEDULER"
    const val ACTION_MEDICATION_ALARM = "com.medicare.app.ACTION_MEDICATION_ALARM"
    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
    const val EXTRA_SCHEDULED_DATE = "extra_scheduled_date"
    const val EXTRA_IS_TEST = "extra_is_test"

    fun scheduleSessionAlarm(context: Context, session: MedicineSession) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_ALARM
            putExtra(EXTRA_SESSION_ID, session.sessionId)
            putExtra(EXTRA_SCHEDULED_TIME, session.scheduledTime)
            putExtra(EXTRA_SCHEDULED_DATE, session.scheduledDate)
            putExtra(EXTRA_IS_TEST, false)
        }

        val requestCode = session.sessionId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTimeMillis = parseDateTimeToMillis(session.scheduledDate, session.scheduledTime)
        val currentTime = System.currentTimeMillis()
        val timeRemaining = triggerTimeMillis - currentTime
        val tzId = TimeZone.getDefault().id
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

        Log.d(TAG, "Scheduling session alarm: sessionId=${session.sessionId}, date=${session.scheduledDate}, time=${session.scheduledTime}, currentTime=$currentTime, tz=$tzId, triggerMillis=$triggerTimeMillis, timeRemainingMs=$timeRemaining, canExact=$canExact, pendingIntentHashCode=${pendingIntent.hashCode()}")

        val effectiveTrigger = if (triggerTimeMillis <= currentTime) {
            Log.w(TAG, "Trigger time is in the past for session ${session.sessionId}. Triggering immediately (within 5s).")
            currentTime + 5000L
        } else {
            triggerTimeMillis
        }

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(effectiveTrigger, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Successfully registered AlarmClock with AlarmManager for session ${session.sessionId} at epoch $effectiveTrigger")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling AlarmClock for session ${session.sessionId}", e)
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, effectiveTrigger, pendingIntent)
                Log.d(TAG, "Fallback to setExactAndAllowWhileIdle succeeded for session ${session.sessionId}")
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback scheduling failed for session ${session.sessionId}", ex)
                alarmManager.set(AlarmManager.RTC_WAKEUP, effectiveTrigger, pendingIntent)
            }
        }
    }

    fun scheduleSnoozeById(context: Context, sessionId: String, scheduledTime: String, snoozeMinutes: Int = 5) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_ALARM
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_IS_TEST, false)
        }

        val requestCode = sessionId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTimeMillis = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        Log.d(TAG, "Scheduling snooze/repeat alarm for sessionId=$sessionId in $snoozeMinutes minutes at $triggerTimeMillis")

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTimeMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: Exception) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        }
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
        scheduleSnoozeById(context, session.sessionId, session.scheduledTime, snoozeMinutes)
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
