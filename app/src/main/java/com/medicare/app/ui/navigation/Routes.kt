package com.medicare.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface NavRoute {
    @Serializable
    data object Home : NavRoute

    @Serializable
    data object MedicationList : NavRoute

    @Serializable
    data class AddEditMedication(val medicationId: String? = null) : NavRoute

    @Serializable
    data object Sessions : NavRoute
}
