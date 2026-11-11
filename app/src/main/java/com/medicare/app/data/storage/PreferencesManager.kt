package com.medicare.app.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "medicare_preferences")

class PreferencesManager(private val dataStore: DataStore<Preferences>) {
    data class EmergencyContact(val name: String, val phone: String)
    constructor(context: Context) : this(context.dataStore)

    companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        val REMINDER_OFFSET_MINUTES_KEY = intPreferencesKey("reminder_offset_minutes")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val EMERGENCY_1_NAME = stringPreferencesKey("emergency_1_name")
        val EMERGENCY_1_PHONE = stringPreferencesKey("emergency_1_phone")
        val EMERGENCY_2_NAME = stringPreferencesKey("emergency_2_name")
        val EMERGENCY_2_PHONE = stringPreferencesKey("emergency_2_phone")
        val AUTO_SMS_ENABLED = booleanPreferencesKey("emergency_auto_sms_enabled")
        val RINGTONE_URI = stringPreferencesKey("alarm_ringtone_uri")
    }

    val darkModeFlow: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { preferences -> preferences[DARK_MODE_KEY] ?: false }

    val notificationsEnabledFlow: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { preferences -> preferences[NOTIFICATIONS_ENABLED_KEY] ?: true }

    val reminderOffsetMinutesFlow: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { preferences -> preferences[REMINDER_OFFSET_MINUTES_KEY] ?: 10 }

    val userNameFlow: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { preferences -> preferences[USER_NAME_KEY] ?: "Patient" }

    val emergencyContactsFlow: Flow<List<EmergencyContact>> = dataStore.data.map { p ->
        listOf(
            EmergencyContact(p[EMERGENCY_1_NAME].orEmpty(), p[EMERGENCY_1_PHONE].orEmpty()),
            EmergencyContact(p[EMERGENCY_2_NAME].orEmpty(), p[EMERGENCY_2_PHONE].orEmpty())
        )
    }
    val autoSmsEnabledFlow: Flow<Boolean> = dataStore.data.map { it[AUTO_SMS_ENABLED] ?: false }
    val ringtoneUriFlow: Flow<String?> = dataStore.data.map { it[RINGTONE_URI] }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[DARK_MODE_KEY] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[NOTIFICATIONS_ENABLED_KEY] = enabled }
    }

    suspend fun setReminderOffsetMinutes(minutes: Int) {
        dataStore.edit { preferences -> preferences[REMINDER_OFFSET_MINUTES_KEY] = minutes }
    }

    suspend fun setUserName(name: String) {
        dataStore.edit { preferences -> preferences[USER_NAME_KEY] = name }
    }

    suspend fun setEmergencyContact(index: Int, name: String, phone: String) {
        require(index in 0..1)
        val current = emergencyContactsFlow.first()
        setEmergencyContacts(current.toMutableList().also { it[index] = EmergencyContact(name, phone) })
    }

    suspend fun setEmergencyContacts(contacts: List<EmergencyContact>) {
        require(contacts.size == 2)
        dataStore.edit { p ->
            p[EMERGENCY_1_NAME] = contacts[0].name
            p[EMERGENCY_1_PHONE] = contacts[0].phone
            p[EMERGENCY_2_NAME] = contacts[1].name
            p[EMERGENCY_2_PHONE] = contacts[1].phone
        }
    }

    suspend fun setAutoSmsEnabled(enabled: Boolean) { dataStore.edit { it[AUTO_SMS_ENABLED] = enabled } }
    suspend fun setRingtoneUri(uri: String?) { dataStore.edit { if (uri == null) it.remove(RINGTONE_URI) else it[RINGTONE_URI] = uri } }

    suspend fun clearUserPreferences() { dataStore.edit { it.clear() } }
}
