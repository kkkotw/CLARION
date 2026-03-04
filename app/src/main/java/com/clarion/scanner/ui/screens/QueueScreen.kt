// QueueScreen - lista uploadów z możliwością ponowienia i usunięcia | 2026-03-04
package com.clarion.scanner.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.clarion.scanner.data.local.AppDatabase
import com.clarion.scanner.data.local.ScanUploadEntity
import com.clarion.scanner.data.local.UploadStatus
import com.clarion.scanner.data.worker.UploadWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val uploads by db.scanUploadDao().getAllUploads().collectAsState(initial = emptyList())

    val pendingCount = uploads.count {
        it.status == UploadStatus.PENDING || it.status == UploadStatus.UPLOADING
    }
    val successCount = uploads.count { it.status == UploadStatus.SUCCESS }
    val failedCount = uploads.count { it.status == UploadStatus.FAILED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kolejka uploadów") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    if (successCount > 0) {
                        IconButton(
                            onClick = { scope.launch { db.scanUploadDao().deleteSuccessful() } }
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Usuń wysłane"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->

        if (uploads.isEmpty()) {
            EmptyQueueContent(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding)) {
            if (pendingCount > 0 || failedCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pendingCount > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("$pendingCount oczekuje") },
                            leadingIcon = {
                                Icon(Icons.Default.CloudUpload, null, Modifier.size(16.dp))
                            }
                        )
                    }
                    if (failedCount > 0) {
                        AssistChip(
                            onClick = {
                                scope.launch {
                                    val failed = uploads.filter { it.status == UploadStatus.FAILED }
                                    failed.forEach { upload ->
                                        db.scanUploadDao().update(
                                            upload.copy(
                                                status = UploadStatus.PENDING,
                                                errorMessage = null
                                            )
                                        )
                                        scheduleUploadRetry(context, upload.id)
                                    }
                                }
                            },
                            label = { Text("Ponów $failedCount błędnych") },
                            leadingIcon = {
                                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uploads, key = { it.id }) { upload ->
                    UploadItemCard(
                        upload = upload,
                        onRetry = {
                            scope.launch {
                                db.scanUploadDao().update(
                                    upload.copy(status = UploadStatus.PENDING, errorMessage = null)
                                )
                                scheduleUploadRetry(context, upload.id)
                            }
                        },
                        onDelete = {
                            scope.launch { db.scanUploadDao().delete(upload) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyQueueContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CloudDone,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Kolejka jest pusta", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Wszystkie zdjęcia zostały wysłane",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UploadItemCard(
    upload: ScanUploadEntity,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(upload.createdAt) {
        SimpleDateFormat("dd.MM.yyyy  HH:mm:ss", Locale.getDefault())
            .format(Date(upload.createdAt))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (upload.status) {
                UploadStatus.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
                UploadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (upload.status) {
                UploadStatus.PENDING -> Icon(
                    Icons.Default.Schedule,
                    contentDescription = "Oczekuje",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                UploadStatus.UPLOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
                UploadStatus.SUCCESS -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Wysłano",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
                UploadStatus.FAILED -> Icon(
                    Icons.Default.Error,
                    contentDescription = "Błąd",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (upload.status) {
                        UploadStatus.PENDING -> "Oczekuje na wysłanie"
                        UploadStatus.UPLOADING -> "Wysyłanie..."
                        UploadStatus.SUCCESS -> "Wysłano pomyślnie"
                        UploadStatus.FAILED -> "Błąd wysyłania"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!upload.errorMessage.isNullOrBlank()) {
                    Text(
                        text = upload.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (!upload.scanId.isNullOrBlank()) {
                    Text(
                        text = "ID: ${upload.scanId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (upload.status == UploadStatus.FAILED) {
                IconButton(onClick = onRetry) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Ponów",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (upload.status == UploadStatus.SUCCESS || upload.status == UploadStatus.FAILED) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Usuń",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun scheduleUploadRetry(context: Context, uploadId: String) {
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
        .build()
    WorkManager.getInstance(context).enqueue(request)
}
