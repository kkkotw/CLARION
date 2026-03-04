// CameraScreen - podgląd aparatu, zdjęcia, kolejkowanie uploadów | 2026-03-04
package com.clarion.scanner.ui.screens

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.work.*
import com.clarion.scanner.data.local.AppDatabase
import com.clarion.scanner.data.local.ScanUploadEntity
import com.clarion.scanner.data.local.UploadStatus
import com.clarion.scanner.data.worker.UploadWorker
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var pendingCount by remember { mutableIntStateOf(0) }
    var successCount by remember { mutableIntStateOf(0) }

    val db = remember { AppDatabase.getInstance(context) }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    LaunchedEffect(Unit) {
        db.scanUploadDao().getAllUploads().collect { uploads ->
            pendingCount = uploads.count {
                it.status == UploadStatus.PENDING || it.status == UploadStatus.UPLOADING
            }
            successCount = uploads.count { it.status == UploadStatus.SUCCESS }
        }
    }

    if (!cameraPermission.status.isGranted) {
        NoPermissionContent(onRequest = { cameraPermission.launchPermissionRequest() })
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture
                        )
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Błąd inicjalizacji aparatu", Toast.LENGTH_SHORT).show()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Clarion Scanner",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Row {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Ustawienia",
                            tint = Color.White
                        )
                    }
                    BadgedBox(
                        badge = {
                            if (pendingCount > 0) {
                                Badge { Text(pendingCount.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = onOpenQueue) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "Kolejka",
                                tint = if (pendingCount > 0) Color(0xFF64B5F6) else Color.White
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(72.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            } else {
                Button(
                    onClick = {
                        isCapturing = true
                        val capture = imageCapture
                        if (capture == null) {
                            isCapturing = false
                            Toast.makeText(context, "Aparat nie jest gotowy", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        capturePhoto(
                            context = context,
                            imageCapture = capture,
                            onSuccess = { filePath ->
                                scope.launch {
                                    val entity = ScanUploadEntity(
                                        id = UUID.randomUUID().toString(),
                                        imagePath = filePath,
                                        status = UploadStatus.PENDING
                                    )
                                    db.scanUploadDao().insert(entity)
                                    scheduleUpload(context, entity.id)
                                    Toast.makeText(
                                        context,
                                        "Zdjęcie dodane do kolejki",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    isCapturing = false
                                }
                            },
                            onError = { isCapturing = false }
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Zrób zdjęcie",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        if (successCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 52.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun NoPermissionContent(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Wymagane uprawnienie do aparatu",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Aplikacja potrzebuje dostępu do aparatu, aby skanować dokumenty",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequest) {
                Text("Zezwól na dostęp do aparatu")
            }
        }
    }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onSuccess: (String) -> Unit,
    onError: () -> Unit
) {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val file = File(context.filesDir, "scan_${timestamp}.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onSuccess(file.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(
                    context,
                    "Błąd aparatu: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
                file.delete()
                onError()
            }
        }
    )
}

private fun scheduleUpload(context: Context, uploadId: String) {
    val inputData = Data.Builder()
        .putString(UploadWorker.KEY_UPLOAD_ID, uploadId)
        .build()

    val request = OneTimeWorkRequestBuilder<UploadWorker>()
        .setInputData(inputData)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .addTag("scan_upload")
        .build()

    WorkManager.getInstance(context).enqueue(request)
}
