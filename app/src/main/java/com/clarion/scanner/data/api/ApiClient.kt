// ApiClient - Retrofit singleton z OkHttp | 2026-03-04
package com.clarion.scanner.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String = ""

    fun getService(baseUrl: String): ScanApiService {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (retrofit == null || currentBaseUrl != normalizedUrl) {
            currentBaseUrl = normalizedUrl
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
            retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(ScanApiService::class.java)
    }
}
