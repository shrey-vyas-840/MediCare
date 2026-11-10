package com.medicare.app.alarms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.medicare.app.MainActivity

class AlarmRingService : Service() {
    companion object {
        const val TAG = "MediCareAlarm"
        const val CHANNEL_ID = "alarm_ring_channel"
        const val NOTIFICATION_ID = 9999
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_MEDICATION_NAME = "extra_medication_name"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"

        fun start(context: Context, sessionId: String, medicationName: String, scheduledTime: String) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_MEDICATION_NAME, medicationName)
                putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmRingService::class.java)
            context.stopService(intent)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmRingService.onCreate")
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MediCare:AlarmWakeLock").apply {
            acquire(10 * 60 * 1000L) // 10 minutes max
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID) ?: "session_unknown"
        val medName = intent?.getStringExtra(EXTRA_MEDICATION_NAME) ?: "Scheduled Medication"
        val scheduledTime = intent?.getStringExtra(EXTRA_SCHEDULED_TIME) ?: "08:00"

        Log.d(TAG, "AlarmRingService.onStartCommand: sessionId=$sessionId, medName=$medName, scheduledTime=$scheduledTime")

        createNotificationChannel()
        val notification = buildNotification(sessionId, medName, scheduledTime)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        playAlarmRingtone()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Ringing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active medication alarm ringing channel"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(sessionId: String, medName: String, scheduledTime: String): Notification {
        val fullScreenIntent = Intent(this, AlarmAlertActivity::class.java).apply {
            putExtra(AlarmAlertActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(AlarmAlertActivity.EXTRA_MEDICATION_NAME, medName)
            putExtra(AlarmAlertActivity.EXTRA_SCHEDULED_TIME, scheduledTime)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            sessionId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("TIME TO TAKE YOUR MEDICINE ($scheduledTime)")
            .setContentText(medName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .build()
    }

    private fun playAlarmRingtone() {
        try {
            mediaPlayer?.release()
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.d(TAG, "Alarm ringtone playing started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm ringtone", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AlarmRingService.onDestroy - stopping ringtone and releasing wakelock")
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player", e)
        }
        try {
            wakeLock?.release()
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wakelock", e)
        }
    }
}
