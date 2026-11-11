package com.medicare.app.alarms

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medicare.app.MainActivity
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.storage.AppDatabase
import com.medicare.app.alarms.AlarmTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.medicare.app.ui.theme.MediCareTheme

class AlarmAlertActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_DOSE_ID = "extra_dose_id"
        const val EXTRA_MEDICATION_NAME = "extra_medication_name"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""
        val doseId = intent.getStringExtra(EXTRA_DOSE_ID) ?: ""
        val medName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "Scheduled Medication"
        val scheduledTime = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: "08:00"
        val patientName = intent.getStringExtra(AlarmRingService.EXTRA_PATIENT_NAME).orEmpty().ifBlank { "Patient" }

        setContent {
            MediCareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$patientName · TIME TO TAKE YOUR MEDICINE",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = medName,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scheduled Time: $scheduledTime",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(48.dp))

                        // BUTTON 1: VERIFY AND TAKE (Navigates directly to camera for pending dose)
                        Button(
                            onClick = {
                                AlarmRingService.stop(applicationContext)
                                val mainIntent = Intent(this@AlarmAlertActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    putExtra("extra_session_id", sessionId)
                                    putExtra("extra_dose_event_id", doseId)
                                }
                                startActivity(mainIntent)
                                finish()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("VERIFY AND TAKE", style = MaterialTheme.typography.titleMedium)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // BUTTON 2: SNOOZE 5 MINUTES
                        OutlinedButton(
                            onClick = {
                                AlarmRingService.stop(applicationContext)
                                if (doseId.isNotBlank()) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        val db = AppDatabase.get(applicationContext)
                                        val dose = db.doses().byId(doseId)
                                        val retryAt = AlarmTiming.snoozeTriggerAt(System.currentTimeMillis())
                                        if (dose != null && db.doses().retry(doseId, retryAt) == 1) {
                                            AlarmScheduler.scheduleDoseAlarm(applicationContext, dose.copy(nextReminderAt = retryAt), AlarmScheduler.AlarmKind.SNOOZE, retryAt)
                                        }
                                        finish()
                                    }
                                } else finish()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            Text("SNOOZE 5 MINUTES", style = MaterialTheme.typography.titleMedium)
                        }

                        if (AlarmTestManager.isTestDose(doseId)) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = {
                                AlarmRingService.stop(applicationContext)
                                CoroutineScope(Dispatchers.IO).launch {
                                    AlarmTestManager.cleanup(applicationContext, doseId)
                                    runOnUiThread { finish() }
                                }
                            }) { Text("END ALARM TEST") }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingSuperCall")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Restricted interaction: Back button cannot dismiss alarm ringing.
    }
}
