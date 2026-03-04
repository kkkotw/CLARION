// UploadWorker - asynchroniczne wysyłanie zdjęć z kompresją | 2026-03-04
package com.clarion.scanner.data.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.work.*
import com.clarion.scanner.data.api.ApiClient
import com.clarion.scanner.data.local.AppDatabase
import com.clarion.scanner.data.local.ImageQualitySetting
import com.clarion.scanner.data.local.PreferencesManager
import com.clarion.scanner.data.local.UploadStatus
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uploadId = inputData.getString(KEY_UPLOAD_ID) ?: return Result.failure()

        val prefs = PreferencesManager(applicationContext)
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.scanUploadDao()

        val serverUrl = prefs.serverUrl.first()
        val authToken = prefs.authToken.first()
        val qualityName = prefs.imageQuality.first()

        if (serverUrl.isBlank() || authToken.isBlank()) return Result.failure()

        val upload = dao.getById(uploadId) ?: return Result.failure()
        dao.update(upload.copy(status = UploadStatus.UPLOADING, errorMessage = null))

        var tempFile: File? = null

        return try {
            val quality = runCatching { ImageQualitySetting.valueOf(qualityName) }
                .getOrDefault(ImageQualitySetting.STANDARD)

            tempFile = compressImage(upload.imagePath, quality)

            val requestFile = tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData(
                "image", "scan_${uploadId}.jpg", requestFile
            )
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .format(Date())
                .toRequestBody("text/plain".toMediaTypeOrNull())

            val response = ApiClient.getService(serverUrl).uploadScan(
                token = "Bearer $authToken",
                image = imagePart,
                timestamp = timestamp
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                dao.update(
                    upload.copy(
                        status = UploadStatus.SUCCESS,
                        scanId = body.scan_id?.toString()
                    )
                )
                Result.success()
            } else {
                val error = when (response.code()) {
                    401 -> "Brak autoryzacji – zaloguj się ponownie"
                    400 -> "Nieprawidłowy plik (JPEG/PNG, max 8MB)"
                    else -> "HTTP ${response.code()}"
                }
                dao.update(upload.copy(status = UploadStatus.FAILED, errorMessage = error))
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            dao.update(
                upload.copy(
                    status = UploadStatus.FAILED,
                    errorMessage = e.message ?: "Nieznany błąd"
                )
            )
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } finally {
            tempFile?.let {
                if (it.absolutePath != upload.imagePath) it.delete()
            }
        }
    }

    private fun compressImage(imagePath: String, quality: ImageQualitySetting): File {
        val originalFile = File(imagePath)
        if (!originalFile.exists()) return originalFile

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, options)

        val sampleSize = if (quality.maxDimension > 0) {
            val maxSide = maxOf(options.outWidth, options.outHeight)
            var size = 1
            while (maxSide / (size * 2) > quality.maxDimension) size *= 2
            size
        } else 1

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        var bitmap = BitmapFactory.decodeFile(imagePath, decodeOptions) ?: return originalFile

        bitmap = correctOrientation(bitmap, imagePath)

        if (quality.maxDimension > 0) {
            val ratio = minOf(
                quality.maxDimension.toFloat() / bitmap.width,
                quality.maxDimension.toFloat() / bitmap.height,
                1f
            )
            if (ratio < 1f) {
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true
                )
                bitmap.recycle()
                bitmap = scaled
            }
        }

        val outputFile = File(applicationContext.cacheDir, "upload_${UUID.randomUUID()}.jpg")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, out)
        }
        bitmap.recycle()
        return outputFile
    }

    private fun correctOrientation(bitmap: Bitmap, imagePath: String): Bitmap {
        return try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return bitmap
            }
            val matrix = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            rotated
        } catch (e: Exception) {
            bitmap
        }
    }

    companion object {
        const val KEY_UPLOAD_ID = "upload_id"
        const val MAX_RETRIES = 3
    }
}
