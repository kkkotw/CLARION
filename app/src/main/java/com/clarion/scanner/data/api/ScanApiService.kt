// ScanApiService - endpointy Retrofit zgodne ze specyfikacją serwera | 2026-03-04
package com.clarion.scanner.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

// --- Auth ---

data class MobileLoginRequest(
    val email: String,
    val password: String
)

data class UserInfo(
    val id: Int,
    val email: String,
    val name: String
)

data class MobileLoginResponse(
    val token: String,
    val user: UserInfo
)

// --- Scan ---

data class UploadResponse(
    val scan_id: Int?,
    val image_url: String?,
    val created_at: String?
)

data class QueueItem(
    val id: Int,
    val image_url: String,
    val status: String,
    val created_at: String
)

data class UpdateStatusRequest(val status: String)

data class UpdateStatusResponse(val success: Boolean)

// --- Service ---

interface ScanApiService {

    @POST("api/auth/mobile-login")
    suspend fun mobileLogin(
        @Body request: MobileLoginRequest
    ): Response<MobileLoginResponse>

    @Multipart
    @POST("api/scan/upload")
    suspend fun uploadScan(
        @Header("Authorization") token: String,
        @Part image: MultipartBody.Part,
        @Part("timestamp") timestamp: RequestBody
    ): Response<UploadResponse>

    @GET("api/scan/queue")
    suspend fun getQueue(
        @Header("Authorization") token: String
    ): Response<List<QueueItem>>

    @PATCH("api/scan/{scan_id}/status")
    suspend fun updateStatus(
        @Header("Authorization") token: String,
        @Path("scan_id") scanId: Int,
        @Body request: UpdateStatusRequest
    ): Response<UpdateStatusResponse>
}
