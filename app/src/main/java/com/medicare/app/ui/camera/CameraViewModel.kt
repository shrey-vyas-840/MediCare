package com.medicare.app.ui.camera

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medicare.app.alarms.AlarmScheduler
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.models.Medication
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.JsonStorage
import com.medicare.app.data.storage.MediaStorePhotoStorage
import com.medicare.app.util.WatermarkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface CameraUiState {
    data object Loading : CameraUiState
    data object LivePreview : CameraUiState
    data class CapturedPreview(val bitmap: Bitmap, val tempPath: String) : CameraUiState
    data class Confirming(val bitmap: Bitmap, val tempPath: String) : CameraUiState
    data class Submitted(val confirmationTimestamp: Long, val galleryPath: String, val fileName: String) : CameraUiState
    data class Error(val message: String) : CameraUiState
}

class CameraViewModel(
    application: Application,
    private val doseEventId: String,
    private val sessionId: String
) : AndroidViewModel(application) {

    private val jsonStorage = JsonStorage(application)
    private val sessionRepository = SessionRepository(jsonStorage)
    private val medicationRepository = MedicationRepository(jsonStorage)
    private val mediaStoreStorage = MediaStorePhotoStorage(application)

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Loading)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    var doseEvent: DoseEvent? = null
        private set
    var medication: Medication? = null
        private set
    var captureTimeMillis: Long = 0
        private set

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            doseEvent = sessionRepository.getDoseEvent(doseEventId)
            if (doseEvent != null) {
                medication = medicationRepository.getMedicationById(doseEvent!!.medicineId)
            }
            if (doseEvent == null || medication == null) {
                _uiState.value = CameraUiState.Error("Dose event or medication not found")
                Log.e("MEDICARE_CAMERA", "Dose event ($doseEventId) or medication not found")
            } else {
                _uiState.value = CameraUiState.LivePreview
                Log.d("MEDICARE_CAMERA", "Camera ready for doseEventId=$doseEventId, medication=${medication?.name}")
            }
        }
    }

    fun onPhotoCaptured(bitmap: Bitmap, tempPath: String) {
        captureTimeMillis = System.currentTimeMillis()
        Log.d("MEDICARE_CAMERA", "Photo captured successfully at timestamp $captureTimeMillis. Temp path: $tempPath")
        _uiState.value = CameraUiState.CapturedPreview(bitmap, tempPath)
    }

    fun retakePhoto(tempPath: String?) {
        tempPath?.let { File(it).delete() }
        Log.d("MEDICARE_CAMERA", "Retaking photo, deleted temp path: $tempPath")
        _uiState.value = CameraUiState.LivePreview
    }

    fun requestConfirmation(bitmap: Bitmap, tempPath: String) {
        Log.d("MEDICARE_CAMERA", "Requesting confirmation for captured photo")
        _uiState.value = CameraUiState.Confirming(bitmap, tempPath)
    }

    fun dismissConfirmation(bitmap: Bitmap, tempPath: String) {
        Log.d("MEDICARE_CAMERA", "Confirmation dismissed (No selected). Returning to captured preview.")
        doseEvent?.let { event ->
            AlarmScheduler.scheduleSnoozeById(getApplication(), event.sessionId, event.scheduledTime, 5)
            Log.d("MEDICARE_SCHEDULER", "User selected NO / dismissed confirmation. Scheduled 5-min repeat reminder.")
        }
        _uiState.value = CameraUiState.CapturedPreview(bitmap, tempPath)
    }

    fun confirmAndSubmit(tempPath: String, originalBitmap: Bitmap, onFinished: () -> Unit) {
        viewModelScope.launch {
            val event = doseEvent ?: return@launch
            val med = medication ?: return@launch
            val submissionTime = System.currentTimeMillis()

            Log.d("MEDICARE_SUBMISSION", "Starting confirmation and submission for medication: ${med.name}, doseEventId=${event.eventId}")

            // 1. Apply watermark
            val watermarkedBitmap = WatermarkUtils.applyWatermark(
                originalBitmap = originalBitmap,
                medicineName = med.name,
                scheduledTime = event.scheduledTime,
                captureTimeMillis = captureTimeMillis,
                submissionTimeMillis = submissionTime,
                dateStr = event.scheduledDate
            )

            // 2. Save watermarked photo to MediaStore Gallery (Pictures/MediCare/)
            val (mediaStoreUri, fileName) = mediaStoreStorage.saveBitmapToGallery(watermarkedBitmap, med.name)
            if (mediaStoreUri == null || fileName == null) {
                Log.e("MEDICARE_PHOTO_SAVE", "Failed to save photo to MediaStore Gallery")
                _uiState.value = CameraUiState.Error("Failed to save photo to shared Gallery storage")
                return@launch
            }
            Log.d("MEDICARE_PHOTO_SAVE", "Successfully saved to MediaStore: uri=$mediaStoreUri, fileName=$fileName")

            // Clean up temp file
            try {
                File(tempPath).delete()
            } catch (_: Exception) {}

            // 3. Commit dose record persistence
            sessionRepository.updateDoseEventStatus(
                eventId = event.eventId,
                status = DoseStatus.TAKEN,
                photoPath = mediaStoreUri,
                quantityRecorded = 1,
                userAcknowledged = true
            )
            Log.d("MEDICARE_SUBMISSION", "Updated dose event status to TAKEN with photoPath=$mediaStoreUri")

            // 4. Update inventory exactly once
            medicationRepository.decrementInventory(med.id, 1)
            Log.d("MEDICARE_SESSION", "Decremented inventory for medication ${med.name}")

            // 5. Check if session is fully completed; if so, cancel alarm
            val sessionEvents = sessionRepository.getDoseEventsForSession(event.sessionId)
            val allTakenOrSkipped = sessionEvents.all { it.status == DoseStatus.TAKEN || it.status == DoseStatus.SKIPPED }
            if (allTakenOrSkipped) {
                AlarmScheduler.cancelAlarm(getApplication(), event.sessionId)
                Log.d("MEDICARE_SCHEDULER", "All medicines in session ${event.sessionId} completed. Cancelled alarm.")
            }

            _uiState.value = CameraUiState.Submitted(submissionTime, mediaStoreUri, fileName)
        }
    }

    fun handleCameraExitWithoutSubmission() {
        viewModelScope.launch {
            val event = doseEvent ?: return@launch
            if (event.status == DoseStatus.SCHEDULED) {
                AlarmScheduler.scheduleSnoozeById(getApplication(), event.sessionId, event.scheduledTime, 5)
                Log.d("MEDICARE_SCHEDULER", "Camera closed without submission for session ${event.sessionId}. Scheduled 5-min repeat reminder.")
            }
        }
    }
}
