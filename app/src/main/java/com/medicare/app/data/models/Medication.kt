package com.medicare.app.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dosage: Double,
    val unit: String,
    val times: List<String>, // e.g. ["08:00", "20:00"]
    val frequency: Frequency = Frequency.DAILY,
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val active: Boolean = true,
    val stripRows: Int = 0,
    val stripColumns: Int = 0,
    val initialQuantity: Int = 0,
    val currentInventory: Int = 0,
    val lowStockThreshold: Int = 5,
    val manualAdjustments: List<Long> = emptyList()
)
