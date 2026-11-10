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
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "medicare_preferences")

class PreferencesManager(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        val REMINDER_OFFSET_MINUTES_KEY = intPreferencesKey("reminder_offset_minutes")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
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
}
