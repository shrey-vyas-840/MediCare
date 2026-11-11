package com.medicare.app.ui.settings

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medicare.app.alarms.AlarmScheduler
import com.medicare.app.alarms.AlarmTestManager
import com.medicare.app.data.storage.PreferencesManager
import com.medicare.app.data.storage.DataResetManager
import com.medicare.app.data.storage.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)
    val emergencyAlerts = AppDatabase.get(application).emergencyAlerts().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val darkMode: StateFlow<Boolean> = preferencesManager.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val notificationsEnabled: StateFlow<Boolean> = preferencesManager.notificationsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val userName: StateFlow<String> = preferencesManager.userNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Patient")
    val emergencyContacts = preferencesManager.emergencyContactsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val autoSmsEnabled = preferencesManager.autoSmsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val ringtoneUri = preferencesManager.ringtoneUriFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setDarkMode(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setNotificationsEnabled(enabled) }
    }

    fun setUserName(name: String) {
        viewModelScope.launch { preferencesManager.setUserName(name) }
    }

    fun setEmergencyContact(index: Int, name: String, phone: String) {
        viewModelScope.launch { preferencesManager.setEmergencyContact(index, name, phone) }
    }

    fun saveEmergencyContacts(contacts: List<PreferencesManager.EmergencyContact>) {
        viewModelScope.launch { preferencesManager.setEmergencyContacts(contacts) }
    }

    fun setAutoSmsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAutoSmsEnabled(enabled) }
    }

    fun setRingtoneUri(uri: String?) {
        viewModelScope.launch { preferencesManager.setRingtoneUri(uri) }
    }

    fun hasTelephonyMessaging(): Boolean = (getApplication<Application>()
        .getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager).isSmsCapable

    fun scheduleTestAlarm(context: Context, delayMinutes: Int) {
        AlarmScheduler.scheduleTestAlarm(context, delayMinutes)
    }

    fun clearAlarmTests(onComplete: () -> Unit) {
        viewModelScope.launch { AlarmTestManager.cleanupAll(getApplication()); onComplete() }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun calculateStorageUsage(): String {
        val filesDir = getApplication<Application>().filesDir
        val totalBytes = getFolderSize(filesDir)
        val kb = totalBytes / 1024
        val mb = kb / 1024
        return if (mb > 0) "$mb MB" else "$kb KB"
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isDirectory) {
            var sum = 0L
            file.listFiles()?.forEach { sum += getFolderSize(it) }
            return sum
        }
        return file.length()
    }

    fun clearPhotoCache() {
        val photosDir = File(getApplication<Application>().filesDir, "medication_photos")
        if (photosDir.exists()) {
            photosDir.deleteRecursively()
        }
    }

    fun clearAllData(deleteGalleryPhotos: Boolean, onComplete: (Int?) -> Unit) {
        viewModelScope.launch {
            val deleted = DataResetManager(getApplication()).clearAllData(deleteGalleryPhotos)
            onComplete(deleted)
        }
    }
}
