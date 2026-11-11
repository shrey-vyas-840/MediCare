package com.medicare.app.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class Frequency {
    DAILY,
    WEEKLY,
    AS_NEEDED,
    @Deprecated("Legacy records are migrated to Daily while preserving their times")
    CUSTOM
}

@Serializable
enum class DoseStatus {
    SCHEDULED,
    TAKEN,
    MISSED,
    SKIPPED,
    PENDING_VERIFICATION,
    CANCELLED
}

@Serializable
enum class SessionStatus {
    PENDING,
    COMPLETED,
    MISSED,
    PARTIAL,
    CANCELLED
}
