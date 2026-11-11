package com.medicare.app.ui.camera

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medicare.app.alarms.AlarmScheduler
import com.medicare.app.alarms.AlarmTiming
import com.medicare.app.data.models.DoseEvent
import com.medicare.app.data.models.DoseStatus
import com.medicare.app.data.models.Medication
import com.medicare.app.data.repositories.MedicationRepository
import com.medicare.app.data.repositories.SessionRepository
import com.medicare.app.data.storage.AppDatabase
import com.medicare.app.data.storage.MediaStorePhotoStorage
import com.medicare.app.data.storage.PreferencesManager
import com.medicare.app.util.WatermarkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

sealed interface CameraUiState {
    data object Loading : CameraUiState
    data object LivePreview : CameraUiState
    data class CapturedPreview(val bitmap: Bitmap, val photoUri: String, val fileName: String) : CameraUiState
    data class Confirming(val bitmap: Bitmap, val photoUri: String, val fileName: String) : CameraUiState
    data class Submitted(val confirmationTimestamp: Long, val galleryPath: String, val fileName: String) : CameraUiState
    data class Error(val message: String) : CameraUiState
}

class CameraViewModel(
    application: Application,
    private val doseEventId: String,
    private val sessionId: String
) : AndroidViewModel(application) {

    private val database = AppDatabase.get(application)
    private val sessionRepository = SessionRepository(database)
    private val medicationRepository = MedicationRepository(database)
    private val mediaStoreStorage = MediaStorePhotoStorage(application)
    private val preferencesManager = PreferencesManager(application)
    private val submissionMutex = Mutex()

    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Loading)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    var doseEvent: DoseEvent? = null
        private set
    var medication: Medication? = null
        private set
    var captureTimeMillis: Long = 0
        private set
    private var sourceCapture: Bitmap? = null

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
        sourceCapture?.recycle()
        sourceCapture = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        Log.d("MEDICARE_CAMERA", "Photo captured successfully at timestamp $captureTimeMillis. Temp path: $tempPath")
        viewModelScope.launch {
            val event = doseEvent ?: return@launch
            val med = medication ?: return@launch
            val patientName = preferencesManager.userNameFlow.first()
            val marked = WatermarkUtils.applyWatermark(bitmap, med.name, event.scheduledTime, captureTimeMillis, null, event.scheduledDate, patientName)
            val (uri, name) = mediaStoreStorage.saveBitmapToGallery(marked, med.name)
            if (uri == null || name == null) {
                _uiState.value = CameraUiState.Error("Could not save the photo to Pictures/MediCare. The dose remains pending.")
                return@launch
            }
            if (!sessionRepository.saveCapturedPhoto(event.eventId, uri, captureTimeMillis)) {
                _uiState.value = CameraUiState.Error("This dose is no longer pending. The photo was saved, but the dose was not completed.")
                return@launch
            }
            runCatching { File(tempPath).delete() }
            _uiState.value = CameraUiState.CapturedPreview(mediaStoreStorage.loadBitmap(uri) ?: marked, uri, name)
        }
    }

    fun retakePhoto(photoUri: String?) {
        Log.d("MEDICARE_CAMERA", "Retaking photo; saved Gallery image is retained: $photoUri")
        _uiState.value = CameraUiState.LivePreview
    }

    fun requestConfirmation(bitmap: Bitmap, photoUri: String, fileName: String) {
        Log.d("MEDICARE_CAMERA", "Requesting confirmation for captured photo")
        _uiState.value = CameraUiState.Confirming(bitmap, photoUri, fileName)
    }

    fun dismissConfirmation(bitmap: Bitmap, photoUri: String, fileName: String) {
        Log.d("MEDICARE_CAMERA", "Confirmation dismissed (No selected). Returning to captured preview.")
        doseEvent?.let { event ->
            viewModelScope.launch {
                val retryAt = AlarmTiming.snoozeTriggerAt(System.currentTimeMillis())
                if (sessionRepository.scheduleRetry(event.eventId, retryAt)) {
                    AlarmScheduler.scheduleDoseAlarm(getApplication(), event, AlarmScheduler.AlarmKind.RETRY, retryAt)
                }
            }
            Log.d("MEDICARE_SCHEDULER", "User selected NO / dismissed confirmation. Scheduled 5-min repeat reminder.")
        }
        _uiState.value = CameraUiState.CapturedPreview(bitmap, photoUri, fileName)
    }

    fun confirmAndSubmit(photoUri: String, fileName: String) {
        viewModelScope.launch {
            submissionMutex.withLock {
            if (_uiState.value is CameraUiState.Submitted) return@withLock
            val event = doseEvent ?: return@launch
            val med = medication ?: return@launch
            val submissionTime = System.currentTimeMillis()

            Log.d("MEDICARE_SUBMISSION", "Starting confirmation and submission for medication: ${med.name}, doseEventId=${event.eventId}")
            val original = sourceCapture ?: runCatching { mediaStoreStorage.loadBitmap(photoUri) }.getOrNull()
            if (original == null) {
                _uiState.value = CameraUiState.Error("Could not load the captured image to finish its proof watermark.")
                return@launch
            }
            val finalProof = WatermarkUtils.applyWatermark(original, med.name, event.scheduledTime, captureTimeMillis, submissionTime, event.scheduledDate, preferencesManager.userNameFlow.first())
            if (!mediaStoreStorage.overwriteBitmap(photoUri, finalProof)) {
                finalProof.recycle()
                _uiState.value = CameraUiState.Error("Could not update the Gallery proof. The dose remains pending.")
                return@launch
            }
            finalProof.recycle()
            if (!sessionRepository.completeDose(event.eventId, photoUri, captureTimeMillis, event.quantityRecorded ?: 1, submissionTime)) {
                val pendingProof = WatermarkUtils.applyWatermark(original, med.name, event.scheduledTime, captureTimeMillis, null, event.scheduledDate, preferencesManager.userNameFlow.first())
                mediaStoreStorage.overwriteBitmap(photoUri, pendingProof)
                pendingProof.recycle()
                _uiState.value = CameraUiState.Error("This dose changed before confirmation. It was not marked complete.")
                return@launch
            }

            // 5. Check if session is fully completed; if so, cancel alarm
            AlarmScheduler.cancelDoseAlarms(getApplication(), event.eventId)
            Log.d("MEDICARE_SUBMISSION", "Dose ${event.eventId} completed and inventory deducted atomically")

            _uiState.value = CameraUiState.Submitted(submissionTime, photoUri, fileName)
            }
        }
    }

    override fun onCleared() {
        sourceCapture?.recycle()
        sourceCapture = null
        super.onCleared()
    }

    fun handleCameraExitWithoutSubmission() {
        viewModelScope.launch {
            val event = sessionRepository.getDoseEvent(doseEventId) ?: return@launch
            if (event.status == DoseStatus.SCHEDULED || event.status == DoseStatus.PENDING_VERIFICATION) {
                val retryAt = AlarmTiming.snoozeTriggerAt(System.currentTimeMillis())
                if (sessionRepository.scheduleRetry(event.eventId, retryAt)) {
                    AlarmScheduler.scheduleDoseAlarm(getApplication(), event, AlarmScheduler.AlarmKind.RETRY, retryAt)
                    Log.d("MEDICARE_SCHEDULER", "Camera closed before confirmation. Scheduled retry for dose=${event.eventId}")
                }
            }
        }
    }
}
