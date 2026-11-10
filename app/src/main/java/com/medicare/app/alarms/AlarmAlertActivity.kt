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
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.JsonStorage
import com.medicare.app.ui.theme.MediCareTheme

class AlarmAlertActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
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
        val medName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "Scheduled Medication"
        val scheduledTime = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: "08:00"

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
                            text = "⏰ TIME TO TAKE YOUR MEDICINE",
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
                                val jsonStorage = JsonStorage(applicationContext)
                                val sessionRepo = SessionRepository(jsonStorage)
                                val events = sessionRepo.getDoseEventsForSession(sessionId)
                                val pendingEvent = events.find { it.status == DoseStatus.SCHEDULED } ?: events.firstOrNull()

                                val mainIntent = Intent(this@AlarmAlertActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    putExtra("extra_session_id", sessionId)
                                    if (pendingEvent != null) {
                                        putExtra("extra_dose_event_id", pendingEvent.eventId)
                                    }
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
                                AlarmScheduler.scheduleSnoozeById(applicationContext, sessionId, scheduledTime, 5)
                                finish()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            Text("SNOOZE 5 MINUTES", style = MaterialTheme.typography.titleMedium)
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
