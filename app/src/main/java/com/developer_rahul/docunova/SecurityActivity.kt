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
            Security is a top priority in the design and development of DocuScan. The application implements multiple layers of protection to ensure the confidentiality, integrity, and availability of user data.

            1. Authentication & Access Control
            - User authentication is handled via Supabase Authentication (Google Sign-In / Email).
            - Each user account is protected with JWT (JSON Web Token)–based sessions.
            - Role-based access control (RBAC) ensures that only authenticated users can access their own documents.

            2. Data Storage Security
            - On-device storage: Documents remain on the device until explicitly uploaded.
            - Supabase Storage: Uploaded documents are stored in secure, private buckets accessible only to the authenticated user.
            - Google Drive: When users choose cloud backup, files are stored in an app-specific folder within the user’s Google Drive. The app cannot access other user files.

            3. Data Transmission Security
            - All data transferred between the app, Supabase, and Google services is protected using HTTPS (TLS 1.2/1.3 encryption).
            - Tokens and sensitive information are never transmitted in plain text.

            4. Encryption & Protection
            - Supabase uses AES-256 encryption at rest and TLS encryption in transit to secure stored data.
            - Google Drive also provides end-to-end encryption for all uploaded files.
            - Sensitive user identifiers (name, email) are stored securely in Supabase Database.

            5. API & Service Security
            - API keys for Supabase, Google Drive API, and Translation API are stored securely and are not exposed in the client code.
            - Rate limiting and validation mechanisms are applied to prevent misuse of APIs.

            6. User Privacy & Control
            - Users have full control over their documents and can delete files from Supabase or Google Drive at any time.
            - Profile details (name, email, profile photo) are used only for account identification and in-app display.
            - No personal data is shared or sold to third parties.

            7. Regular Monitoring & Updates
            - Supabase and Google Cloud provide built-in logging and monitoring to detect unusual activity.
            - The app is updated regularly to patch vulnerabilities and ensure compliance with the latest security standards.
        """.trimIndent()

        binding.tvSecurityInfo.text = securityText
    }
}
