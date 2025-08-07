package com.developer_rahul.docunova.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// Request models
data class SignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String>? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

// Response models
data class SupabaseUser(
    val id: String?,
    val email: String?,
    val user_metadata: Map<String, Any>?
)

data class SignUpResponse(
    val user: SupabaseUser?,
    val access_token: String?,
    val token_type: String?,
    val expires_in: Int?
)

data class LoginResponse(
    val access_token: String?,
    val token_type: String?,
    val expires_in: Int?,
    val refresh_token: String?,
    val user: SupabaseUser?
)

interface SupabaseAuthApi {
    @Headers(
        "apikey: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdydHp2d3VuYXhsY2JocW5jaXpvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njg4ODU3NDEsImV4cCI6MjA4NDQ2MTc0MX0.-W7GdFb_r8hFIFXEFUIEXHhqb01e-nDjBN_ZZl75dQ0",
        "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdydHp2d3VuYXhsY2JocW5jaXpvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njg4ODU3NDEsImV4cCI6MjA4NDQ2MTc0MX0.-W7GdFb_r8hFIFXEFUIEXHhqb01e-nDjBN_ZZl75dQ0",
        "Content-Type: application/json"
    )
    @POST("auth/v1/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<SignUpResponse>

    @Headers(
        "apikey: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdydHp2d3VuYXhsY2JocW5jaXpvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njg4ODU3NDEsImV4cCI6MjA4NDQ2MTc0MX0.-W7GdFb_r8hFIFXEFUIEXHhqb01e-nDjBN_ZZl75dQ0",
        "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdydHp2d3VuYXhsY2JocW5jaXpvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njg4ODU3NDEsImV4cCI6MjA4NDQ2MTc0MX0.-W7GdFb_r8hFIFXEFUIEXHhqb01e-nDjBN_ZZl75dQ0",
        "Content-Type: application/json"
    )
    @POST("auth/v1/token?grant_type=password")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}
