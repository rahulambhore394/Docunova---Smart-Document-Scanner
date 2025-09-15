package com.developer_rahul.docunova

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.developer_rahul.docunova.databinding.ActivityHelpAndSupportBinding

class HelpSupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpAndSupportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpAndSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar back button
        binding.toolbarSupport.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setHelpContent()

        // Email Support
        binding.btnEmailSupport.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:developer.rahul2708@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "DocuNova - Help & Support")
            }
            startActivity(intent)
        }

        // WhatsApp Support
        binding.btnWhatsAppSupport.setOnClickListener {
            val phone = "+917385937358" // replace with your support number
            val url = "https://wa.me/$phone?text=Hello%20I%20need%20help%20with%20DocuNova"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun setHelpContent() {
        val faqText = """
            📌 Frequently Asked Questions (FAQ)
            
            1. How do I scan a new document?
            → Go to Home > Tap on + button > Select Scan.
            
            2. Where are my files stored?
            → By default, files are stored on your device. You can enable cloud backup in settings.
            
            3. Can I share my documents?
            → Yes! Open any file and tap on the Share icon.
            
            4. I cannot open a PDF file.
            → Ensure you have a PDF reader installed on your phone.
            
            5. How do I contact support?
            → Use the Email or WhatsApp buttons below.
            
            ℹ️ App Version: ${BuildConfig.VERSION_NAME}
        """.trimIndent()

        binding.tvFaq.text = faqText
    }
}
