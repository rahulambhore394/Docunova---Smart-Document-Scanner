package com.developer_rahul.docunova

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat

class Privacy_Policy_Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbarPrivacy)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val tvPrivacyContent: TextView = findViewById(R.id.tvPrivacyContent)

        val privacyHtml = """
            <p>At <b>DocuNova</b>, we respect your privacy and are deeply committed to protecting your personal data and scanned documents. This Privacy Policy explains what information we collect, how it is used, and how it is secured.</p>

            <br/>
            <b>1. Information We Collect</b>
            <p>• <b>Account Information:</b> Your name, email address, and profile photo when signing in via Google Sign-In.</p>
            <p>• <b>Scanned Documents:</b> Images, PDFs, Word files, or extracted text that you capture and process within the app.</p>
            <p>• <b>Cloud Storage Data:</b> Backed-up documents are stored strictly in an isolated folder within your personal Google Drive account.</p>
            <p>• <b>Device Diagnostics:</b> Non-identifiable technical data (such as OS version and device architecture) solely used for crash diagnostics and performance enhancement.</p>

            <br/>
            <b>2. How Your Information Is Used</b>
            <p>Collected data is used exclusively to deliver document scanning and productivity features:</p>
            <p>• Scanning, cropping, filtering, and enhancing physical document captures.</p>
            <p>• Converting scanned pages to standard PDF and Word (.docx) formats.</p>
            <p>• Extracting text via on-device ML Kit OCR and text translation.</p>
            <p>• Synchronizing documents securely with your personal Google Drive.</p>
            <p>• We do <b>not</b> serve third-party ads and never monetize or sell your document data.</p>

            <br/>
            <b>3. Data Storage &amp; Security</b>
            <p>• <b>Local Isolation:</b> All documents are stored in the app's protected sandbox storage on your device.</p>
            <p>• <b>Encryption:</b> Google Drive storage is protected by enterprise AES-256 encryption at rest and TLS 1.3 encryption in transit.</p>
            <p>• <b>Minimal Privileges:</b> We use the restricted <code>drive.file</code> scope, preventing DocuNova from seeing or touching any files outside its own dedicated folder.</p>

            <br/>
            <b>4. Third-Party Integrations</b>
            <p>DocuNova integrates only with certified, privacy-compliant Google APIs:</p>
            <p>• <b>Google Sign-In:</b> Industry-standard OAuth 2.0 authentication.</p>
            <p>• <b>Google Drive API:</b> Cloud backup into the user's personal storage.</p>
            <p>• <b>Google ML Kit:</b> On-device machine learning for edge detection and OCR recognition.</p>

            <br/>
            <b>5. User Rights &amp; Data Control</b>
            <p>• You maintain 100% sovereignty over your documents and metadata.</p>
            <p>• You can delete local or cloud-backed files at any time directly from the app.</p>
            <p>• You may disconnect your Google account or revoke access via Google Account Settings at any time.</p>
            <p>• To request permanent removal of account records, contact us directly at our support email below.</p>

            <br/>
            <b>6. Children's Privacy</b>
            <p>DocuNova is not directed at children under the age of 13. We do not knowingly collect personal data from minors.</p>

            <br/>
            <b>7. Contact &amp; Inquiries</b>
            <p>If you have any questions or feedback regarding our privacy practices, please contact us at:</p>
            <p>📧 <b>developer.rahul2708@gmail.com</b></p>
        """.trimIndent()

        tvPrivacyContent.text = HtmlCompat.fromHtml(privacyHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
