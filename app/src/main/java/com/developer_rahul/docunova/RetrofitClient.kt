package com.developer_rahul.docunova.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Replace with your Supabase project URL
    private const val BASE_URL = "https://grtzvwunaxlcbhqncizo.supabase.co"


    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val instance: SupabaseAuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseAuthApi::class.java)
    }
}
//
//object RetrofitClient {
//    private const val BASE_URL = "https://biudcywgygbacfxfpuva.supabase.co"
//
//    val instance: Retrofit by lazy {
//        Retrofit.Builder()
//            .baseUrl(BASE_URL)
//            .addConverterFactory(GsonConverterFactory.create())
//            .client(
//                OkHttpClient.Builder().addInterceptor { chain ->
//                    val request = chain.request().newBuilder()
//                        .addHeader("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJpdWRjeXdneWdiYWNmeGZwdXZhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM1Mzg1NDQsImV4cCI6MjA2OTExNDU0NH0.enJZKTQjKtOyB6VU5pYo_vf4p7ZLv2ayYuyqBWvLqbA")
//                        .addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJpdWRjeXdneWdiYWNmeGZwdXZhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTM1Mzg1NDQsImV4cCI6MjA2OTExNDU0NH0.enJZKTQjKtOyB6VU5pYo_vf4p7ZLv2ayYuyqBWvLqbA")
//                        .build()
//                    chain.proceed(request)
//                }.build()
//            )
//            .build()
//    }
//}
