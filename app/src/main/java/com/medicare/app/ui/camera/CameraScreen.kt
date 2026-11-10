package com.medicare.app.ui.camera

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.util.concurrent.Executor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    val uiState by viewModel.uiState.collectAsState()
    val medication = viewModel.medication

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Verify: ${medication?.name ?: "Medication"}") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.handleCameraExitWithoutSubmission()
                        onNavigateBack()
                    }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!cameraPermissionState.status.isGranted) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Camera permission is required for photo verification of your medication.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Grant Camera Permission")
                    }
                }
            } else {
                when (val state = uiState) {
                    is CameraUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is CameraUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is CameraUiState.LivePreview -> {
                        var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
                        val executor = remember { ContextCompat.getMainExecutor(context) }

                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        imageCapture = ImageCapture.Builder().build()
                                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                imageCapture
                                            )
                                        } catch (exc: Exception) {
                                            Log.e("CameraScreen", "Use case binding failed", exc)
                                        }
                                    }, executor)
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Capture Button
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 32.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                FloatingActionButton(
                                    onClick = {
                                        val capture = imageCapture ?: return@FloatingActionButton
                                        val photoFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
                                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                        capture.takePicture(
                                            outputOptions,
                                            executor,
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                                    viewModel.onPhotoCaptured(bitmap, photoFile.absolutePath)
                                                }
                                                override fun onError(exc: ImageCaptureException) {
                                                    Log.e("CameraScreen", "Photo capture failed: ${exc.message}", exc)
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier.size(80.dp),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        Icons.Rounded.Camera,
                                        contentDescription = "Capture Photo",
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                    is CameraUiState.CapturedPreview -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = state.bitmap.asImageBitmap(),
                                contentDescription = "Captured Photo",
                                modifier = Modifier.fillMaxSize()
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(24.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.retakePhoto(state.tempPath) },
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retake")
                                }

                                Button(
                                    onClick = { viewModel.requestConfirmation(state.bitmap, state.tempPath) }
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verify & Take")
                                }
                            }
                        }
                    }
                    is CameraUiState.Confirming -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = state.bitmap.asImageBitmap(),
                                contentDescription = "Captured Photo",
                                modifier = Modifier.fillMaxSize()
                            )

                            // Semi-transparent overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .padding(32.dp)
                                        .fillMaxWidth(),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Verification Check",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "DID YOU TAKE THIS MEDICINE?",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Medicine: ${medication?.name}\nDosage: ${medication?.dosage} ${medication?.unit}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.dismissConfirmation(state.bitmap, state.tempPath) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("NO, NOT YET")
                                            }
                                            Button(
                                                onClick = {
                                                    viewModel.confirmAndSubmit(state.tempPath, state.bitmap) {}
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("YES, CONFIRM")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is CameraUiState.Submitted -> {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "MEDICINE SUBMITTED SUCCESSFULLY",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                val bitmap = remember(state.galleryPath) {
                                    try {
                                        android.graphics.BitmapFactory.decodeStream(
                                            context.contentResolver.openInputStream(android.net.Uri.parse(state.galleryPath))
                                        )
                                    } catch (_: Exception) {
                                        null
                                    }
                                }

                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Submitted Photo",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(240.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Medicine: ${medication?.name}")
                                        Text("Dose: ${medication?.dosage} ${medication?.unit}")
                                        Text("Scheduled Time: ${viewModel.doseEvent?.scheduledTime}")
                                        Text("Submission Status: SUBMITTED", color = MaterialTheme.colorScheme.primary)
                                        Text("Gallery Location: Pictures/MediCare/${state.fileName}")
                                        val captureFormatter = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()) }
                                        Text("Capture Time: ${captureFormatter.format(java.util.Date(viewModel.captureTimeMillis))}")
                                        Text("Confirmation Time: ${captureFormatter.format(java.util.Date(state.confirmationTimestamp))}")
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Return to Sessions")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
