package com.developer_rahul.docunova

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class EmailConfirmationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conform_email)

        val data: Uri? = intent?.data
        if (data != null) {
            val accessToken = data.getQueryParameter("access_token")
            val refreshToken = data.getQueryParameter("refresh_token")

            if (!accessToken.isNullOrEmpty()) {
                // Save tokens
                getSharedPreferences("auth", MODE_PRIVATE)
                    .edit()
                    .putString("access_token", accessToken)
                    .putString("refresh_token", refreshToken)
                    .apply()

                Toast.makeText(this, "✅ Email confirmed! Logged in", Toast.LENGTH_LONG).show()

                // Redirect to MainActivity
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
               // Toast.makeText(this, "❌ Invalid or expired confirmation link", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "⚠️ No confirmation data found", Toast.LENGTH_LONG).show()
        }
    }
}
