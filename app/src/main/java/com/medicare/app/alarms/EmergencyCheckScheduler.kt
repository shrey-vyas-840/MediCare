package com.medicare.app.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.medicare.app.data.models.MedicineSession

object EmergencyCheckScheduler {
    const val ACTION = "com.medicare.app.ACTION_EMERGENCY_SESSION_CHECK"
    const val EXTRA_SESSION_ID = "emergency_session_id"
    const val EXTRA_TRIGGER_AT = "emergency_trigger_at"

    fun triggerAt(date: String, time: String): Long = EmergencyTiming.checkAt(date, time)

    fun schedule(context: Context, session: MedicineSession) {
        val at = triggerAt(session.scheduledDate, session.scheduledTime)
        val intent = intent(context, session.sessionId).putExtra(EXTRA_TRIGGER_AT, at)
        val pending = PendingIntent.getBroadcast(context, session.sessionId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fireAt = at.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.w("MediCareEmergency", "Exact alarm access unavailable; using inexact idle-allowed check")
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            } else manager.setExact(AlarmManager.RTC_WAKEUP, fireAt, pending)
            Log.i("MediCareEmergency", "Scheduled incomplete-session check session=${session.sessionId} targetAt=$at fireAt=$fireAt")
        } catch (error: SecurityException) {
            Log.e("MediCareEmergency", "Could not schedule incomplete-session check", error)
        }
    }

    fun cancel(context: Context, sessionId: String) {
        val pending = PendingIntent.getBroadcast(context, sessionId.hashCode(), intent(context, sessionId), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
    }

    private fun intent(context: Context, sessionId: String) = Intent(context, EmergencyCheckReceiver::class.java).apply {
        action = ACTION
        data = Uri.parse("medicare://emergency-session/$sessionId")
        putExtra(EXTRA_SESSION_ID, sessionId)
    }
}
