package com.medicare.app.data.storage

import android.app.NotificationManager
import android.content.Context
import androidx.room.withTransaction
import com.medicare.app.alarms.AlarmRingService
import com.medicare.app.alarms.AlarmScheduler
import com.medicare.app.alarms.EmergencyCheckScheduler
import com.medicare.app.alarms.AlarmTestManager
import java.io.File

class DataResetManager(private val context: Context) {
    suspend fun clearAllData(deleteGalleryPhotos: Boolean): Int? {
        val db = AppDatabase.get(context)
        AlarmTestManager.cleanupAll(context)
        val doses = db.doses().all()
        val sessions = db.sessions().all()
        AlarmScheduler.cancelAllTestAlarms(context)
        doses.forEach { AlarmScheduler.cancelDoseAlarms(context, it.eventId) }
        sessions.forEach {
            EmergencyCheckScheduler.cancel(context, it.sessionId)
            AlarmScheduler.cancelAlarm(context, it.sessionId)
        }
        AlarmRingService.stop(context)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        db.withTransaction {
            db.doses().deleteAll()
            db.asNeededUsage().deleteAll()
            db.emergencyAlerts().deleteAll()
            db.sessions().deleteAll()
            db.medications().deleteAll()
        }
        listOf(
            "medications.json", "medicine_sessions.json", "dose_events.json",
            "medications.json.bak", "medicine_sessions.json.bak", "dose_events.json.bak",
            "medications.json.pre-room.bak", "medicine_sessions.json.pre-room.bak", "dose_events.json.pre-room.bak",
            "room_import_v1.complete", "room_import_report.txt"
        ).forEach { File(context.filesDir, it).delete() }
        File(context.filesDir, "medication_photos").deleteRecursively()
        PreferencesManager(context).clearUserPreferences()
        context.contentResolver.persistedUriPermissions.forEach { permission ->
            var flags = 0
            if (permission.isReadPermission) flags = flags or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (permission.isWritePermission) flags = flags or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            if (flags != 0) runCatching { context.contentResolver.releasePersistableUriPermission(permission.uri, flags) }
        }
        return if (deleteGalleryPhotos) MediaStorePhotoStorage(context).deleteOwnedGalleryPhotos() else 0
    }
}
