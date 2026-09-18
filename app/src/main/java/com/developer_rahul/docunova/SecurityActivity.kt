package com.developer_rahul.docunova

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.developer_rahul.docunova.databinding.ActivitySecurityBinding

class SecurityActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecurityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar back button
        binding.toolbarSecurity.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setSecurityInfo()
    }

    private fun setSecurityInfo() {
        val securityText = """
            Security Architecture & Compliance Standards:

            DocuNova is engineered with a strict privacy-first foundation. The application implements multi-layered security measures to guarantee the confidentiality, integrity, and availability of all user documents.

            • Authentication & Access: Secure Google Sign-In with OAuth 2.0 PKCE flow. Each session token is cryptographically validated with Google Identity services.
            • Cloud Storage Isolation: Cloud backups are stored strictly in an app-isolated folder within your personal Google Drive. DocuNova cannot read, modify, or list any other files in your Drive.
            • Cryptography: Documents are protected at rest via AES-256 bit encryption and in transit via TLS 1.3 encrypted HTTPS channels.
            • Machine Learning: Document edge detection, enhancement, and OCR processing run on-device using ML Kit edge models.
            • Zero Commercial Exploitation: Your documents are never indexed, analyzed for telemetry, or shared with third parties.
        """.trimIndent()

        binding.tvSecurityInfo.text = securityText
    }
}
