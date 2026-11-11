package com.medicare.app.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_alerts", indices = [Index(value = ["sessionId", "recipient"], unique = true)])
data class EmergencyAlert(
    @PrimaryKey val id: String,
    val sessionId: String,
    val recipient: String,
    val message: String,
    val checkedAt: Long,
    val sentAt: Long?,
    val outcome: String
)

@Entity(
    tableName = "as_needed_usage",
    foreignKeys = [ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medicineId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index("medicineId")]
)
data class AsNeededUsage(
    @PrimaryKey val id: String,
    val medicineId: String,
    val quantity: Int,
    val usedAt: Long
)
