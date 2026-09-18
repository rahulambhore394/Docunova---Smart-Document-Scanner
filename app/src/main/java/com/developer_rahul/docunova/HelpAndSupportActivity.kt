package com.developer_rahul.docunova

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
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
    }

    private fun setHelpContent() {
        val tipsHtml = """
            <b>Pro Tips for Scanning Excellence:</b><br/>
            • <b>Lighting:</b> Position documents in even, natural light to minimize reflections.<br/>
            • <b>Contrast:</b> Place papers on a contrasting surface (e.g. dark desk) for automated edge detection.<br/>
            • <b>OCR Accuracy:</b> Hold device level and parallel to document for 99%+ text recognition accuracy.<br/>
            <br/>
            <b>App Version:</b> DocuNova v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
        """.trimIndent()

        binding.tvFaq.text = HtmlCompat.fromHtml(tipsHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
