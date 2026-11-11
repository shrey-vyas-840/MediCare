package com.medicare.app.alarms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.medicare.app.data.models.EmergencyAlert
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.storage.AppDatabase
import com.medicare.app.data.storage.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest

class EmergencyCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EmergencyCheckScheduler.EXTRA_SESSION_ID) ?: return
        val expectedAt = intent.getLongExtra(EmergencyCheckScheduler.EXTRA_TRIGGER_AT, 0L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(context)
                val session = db.sessions().byId(sessionId) ?: return@launch
                val now = System.currentTimeMillis()
                val checkAt = expectedAt.takeIf { it > 0L } ?: EmergencyCheckScheduler.triggerAt(session.scheduledDate, session.scheduledTime)
                Log.i("MediCareEmergency", "Session check delivery session=$sessionId targetAt=$checkAt deliveredAt=$now delayMs=${AlarmTiming.deliveryDelayMillis(checkAt, now)}")
                if (now < checkAt) {
                    EmergencyCheckScheduler.schedule(context, session)
                    return@launch
                }
                val doses = db.doses().forSession(sessionId)
                if (!EmergencyTiming.isIncomplete(doses)) {
                    EmergencyCheckScheduler.cancel(context, sessionId)
                    return@launch
                }

                val prefs = PreferencesManager(context)
                val contacts = prefs.emergencyContactsFlow.first()
                    .filter { it.phone.isNotBlank() }.distinctBy { it.phone.filter(Char::isDigit) }
                val patient = prefs.userNameFlow.first().ifBlank { "Patient" }
                val autoSms = prefs.autoSmsEnabledFlow.first()
                val phonesSupported = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).isSmsCapable
                val smsPermission = Build.VERSION.SDK_INT < 23 || ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                val message = "$patient has an incomplete medication session scheduled for ${session.scheduledTime}. Please check in."
                contacts.forEach { contact ->
                    val recipient = contact.phone.trim()
                    val id = sha256("$sessionId|${recipient.filter(Char::isDigit)}")
                    val row = EmergencyAlert(id, sessionId, recipient, message, now, null, "PENDING")
                    if (db.emergencyAlerts().insertIfNew(row) == -1L) return@forEach
                    val outcome = when {
                        !autoSms -> "AUTO_SMS_DISABLED"
                        !phonesSupported -> "TELEPHONY_UNAVAILABLE"
                        !smsPermission -> "PERMISSION_BLOCKED"
                        else -> try {
                            @Suppress("DEPRECATION")
                            SmsManager.getDefault().sendTextMessage(recipient, null, message, null, null)
                            "SENT"
                        } catch (error: Exception) {
                            Log.e("MediCareEmergency", "Automatic SMS failed for session=$sessionId", error)
                            "SEND_FAILED:${error.javaClass.simpleName}"
                        }
                    }
                    db.emergencyAlerts().updateOutcome(id, outcome, if (outcome == "SENT") System.currentTimeMillis() else null)
                    Log.i("MediCareEmergency", "Emergency alert session=$sessionId recipientConfigured=true outcome=$outcome")
                    if (outcome != "SENT") showManualFallback(context, recipient, message, id)
                }
                if (contacts.isEmpty()) {
                    Log.w("MediCareEmergency", "Incomplete session has no configured emergency contacts; no SMS sent")
                    val id = sha256("$sessionId|no-contact")
                    db.emergencyAlerts().insertIfNew(EmergencyAlert(id, sessionId, "", message, now, null, "NO_CONTACT_CONFIGURED"))
                }
            } catch (error: Exception) {
                Log.e("MediCareEmergency", "Emergency check failed for session=$sessionId", error)
            } finally {
                pending.finish()
            }
        }
    }

    private fun showManualFallback(context: Context, phone: String, message: String, id: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "emergency_sms_fallback"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(
            NotificationChannel(channelId, "Emergency SMS fallback", NotificationManager.IMPORTANCE_HIGH)
        )
        val compose = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(context, id.hashCode(), compose, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id.hashCode(), NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MediCare could not send an emergency SMS")
            .setContentText("Tap to review and send the message manually")
            .setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
