package com.developer_rahul.docunova

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class Privacy_Policy_Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbarPrivacy)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val tvPrivacyContent: TextView = findViewById(R.id.tvPrivacyContent)
        //val btnAgree: Button = findViewById(R.id.btnAgree)

        // Sample Privacy Policy Text (replace with real one)
        val privacyText = """
    Privacy Policy for DocuScan

At DocuScan, we respect your privacy and are committed to protecting your personal data. This Privacy Policy explains what information we collect, how we use it, and how we keep it safe.

1. Data Collection

When you use DocuScan, we may collect the following information:

Account Information: Name, email address, and profile photo when you sign in using Google or email.

Scanned Documents: Images, PDFs, Word files, or text that you scan and process within the app.

Storage Data: Documents you choose to back up are stored securely in Supabase Storage and/or your Google Drive (app-specific folder).

App Usage Data: Non-personal technical data such as device type and operating system, used to improve performance and troubleshoot issues.

2. Data Usage

The collected data is used only for providing app features, such as:

Scanning, cropping, and enhancing documents.

Saving files as PDF or Word format.

Extracting text using OCR and translating it.

Backing up and restoring your documents using Supabase Storage or Google Drive.

Displaying your profile name, email, and photo within the app.

We do not use your data for advertising or sell it to third parties.

3. Data Security

All documents remain on your device unless you choose to upload them to Supabase or Google Drive.

Files stored in Supabase or Google Drive are protected by secure authentication and encryption.

We implement industry-standard security practices to safeguard your data.

4. Third-Party Services

DocuScan integrates with trusted third-party services:

Supabase (Authentication & Storage) – for managing your account and securely storing your documents.

Google Drive API – for backing up documents in your personal Drive account.

Google Cloud Translation API – for translating extracted text.

ML Kit / OpenCV – for document scanning, cropping, and text recognition.

You should also review their privacy policies:

Supabase Privacy Policy

Google Privacy Policy

5. User Rights

You have full control over your data:

You can view, edit, or delete your documents at any time.

You can disconnect your Google account at any time.

You may request complete deletion of your account and all associated data by contacting us at [Your Support Email].

6. Children’s Privacy

DocuScan is not designed for children under 13, and we do not knowingly collect personal data from them. If such data is found, we will delete it immediately.

7. Changes to This Policy

We may update this Privacy Policy occasionally. Any changes will be reflected in the app, along with the "Last Updated" date.

8. Contact Us

For any questions or concerns about this Privacy Policy, you may contact us at:

📧 developer.rahul2708@gmail.com
        """.trimIndent()

        tvPrivacyContent.text = privacyText


    }
}
