package com.medicare.app

import android.app.Application
import android.util.Log
import com.medicare.app.data.storage.AppDatabase
import com.medicare.app.data.storage.LegacyDataImporter
import com.medicare.app.alarms.AlarmReconciliation

class MediCareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            LegacyDataImporter(AppDatabase.get(this), filesDir).importIfNeeded()
            AlarmReconciliation.reconcileAlarms(this)
        } catch (error: Exception) {
            Log.e("MediCareMigration", "Legacy JSON import failed. Original files were preserved.", error)
        }
    }
}
