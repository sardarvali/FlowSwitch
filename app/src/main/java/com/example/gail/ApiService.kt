package com.example.gail

import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("api/generate-otp")
    suspend fun generateOTP(@Body requestBody: Map<String, String>): String

    @POST("api/verify-otp")
    suspend fun verifyOTP(@Body requestBody: Map<String, String>): String
}