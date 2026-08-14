package com.smartvendor.ai.network

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Current active Laptop IP address on Wi-Fi network: 10.88.240.180
    // (Hotspot IP: 192.168.137.1, Emulator: 10.0.2.2)
    private const val BASE_URL = "http://10.88.240.180:8000/"

    private val firebaseTokenInterceptor = Interceptor { chain ->
        val request = chain.request()
        val token = runBlocking {
            try {
                FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(true)
                    ?.await()
                    ?.token
            } catch (e: Exception) {
                Log.e("ApiClient", "Failed to retrieve Firebase ID token", e)
                null
            }
        }

        val newRequest = if (token != null) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }

        chain.proceed(newRequest)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(firebaseTokenInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
