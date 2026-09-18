package com.developer_rahul.docunova

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.android.material.button.MaterialButton
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DocumentViewerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DocumentViewerActivity"
        const val EXTRA_FILE_ID = "extra_file_id"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_MIME_TYPE = "extra_mime_type"
        const val EXTRA_LOCAL_PATH = "extra_local_path"

        fun createIntent(
            context: Context,
            fileId: String?,
            fileName: String,
            mimeType: String?,
            localPath: String? = null
        ): Intent {
            return Intent(context, DocumentViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_ID, fileId)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_MIME_TYPE, mimeType)
                putExtra(EXTRA_LOCAL_PATH, localPath)
            }
        }
    }

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnDownload: MaterialButton
    private lateinit var rvPdfPages: RecyclerView
    private lateinit var ivSingleImage: ImageView
    private lateinit var tvPageIndicator: TextView
    private lateinit var layoutTextViewer: View
    private lateinit var tvDocumentContent: TextView
    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutError: LinearLayout
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: MaterialButton

    private var fileId: String? = null
    private var fileName: String = "Document"
    private var mimeType: String = "application/pdf"
    private var localPath: String? = null

    private var currentActiveFile: File? = null
    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_viewer)

        fileId = intent.getStringExtra(EXTRA_FILE_ID)
        fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Document"
        mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: guessMimeType(fileName)
        localPath = intent.getStringExtra(EXTRA_LOCAL_PATH)

        initViews()
        loadDocument()
    }

    private fun guessMimeType(name: String): String {
        return when {
            name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            name.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            name.endsWith(".txt", ignoreCase = true) -> "text/plain"
            name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            name.endsWith(".png", ignoreCase = true) -> "image/png"
            else -> "application/octet-stream"
        }
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvViewerTitle)
        tvSubtitle = findViewById(R.id.tvViewerSubtitle)
        btnBack = findViewById(R.id.btnBack)
        btnDownload = findViewById(R.id.btnDownload)
        rvPdfPages = findViewById(R.id.rvPdfPages)
        ivSingleImage = findViewById(R.id.ivSingleImage)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        layoutTextViewer = findViewById(R.id.layoutTextViewer)
        tvDocumentContent = findViewById(R.id.tvDocumentContent)
        layoutLoading = findViewById(R.id.layoutLoading)
        layoutError = findViewById(R.id.layoutError)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        btnRetry = findViewById(R.id.btnRetry)

        tvTitle.text = fileName
        tvSubtitle.text = when {
            isPdf(mimeType, fileName) -> "PDF Document"
            isDocx(mimeType, fileName) -> "Word Document (DOCX)"
            isText(mimeType, fileName) -> "Text Document"
            isImage(mimeType, fileName) -> "Image File"
            else -> "Document"
        }

        btnBack.setOnClickListener { finish() }

        btnDownload.setOnClickListener {
            performExplicitDownload()
        }

        btnRetry.setOnClickListener {
            loadDocument()
        }
    }

    private fun isPdf(mime: String, name: String): Boolean {
        return mime.equals("application/pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)
    }

    private fun isDocx(mime: String, name: String): Boolean {
        return name.endsWith(".docx", ignoreCase = true) ||
                mime.contains("wordprocessingml", ignoreCase = true) ||
                mime.contains("msword", ignoreCase = true)
    }

    private fun isText(mime: String, name: String): Boolean {
        return name.endsWith(".txt", ignoreCase = true) ||
                mime.equals("text/plain", ignoreCase = true)
    }

    private fun isImage(mime: String, name: String): Boolean {
        return mime.startsWith("image/", ignoreCase = true) ||
                name.endsWith(".jpg", ignoreCase = true) ||
                name.endsWith(".jpeg", ignoreCase = true) ||
                name.endsWith(".png", ignoreCase = true) ||
                name.endsWith(".webp", ignoreCase = true)
    }

    private fun loadDocument() {
        showLoading(true)
        showError(false)

        // 1. If local copy exists, load immediately
        if (!localPath.isNullOrEmpty()) {
            val localFile = File(localPath!!)
            if (localFile.exists() && localFile.length() > 0) {
                currentActiveFile = localFile
                displayFile(localFile)
                return
            }
        }

        // 2. Otherwise, stream from Google Drive to private viewer cache
        if (!fileId.isNullOrEmpty()) {
            streamFromDrive(fileId!!)
        } else {
            showError(true, "Document file not found locally or on Google Drive.")
        }
    }

    private fun streamFromDrive(driveFileId: String) {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null || !GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            showError(true, "Google Drive authorization is required to view this document.")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val drive = DriveServiceHelper.buildService(this@DocumentViewerActivity, account.email!!)
                val cacheDir = File(this@DocumentViewerActivity.cacheDir, "viewer_cache")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                // Generate deterministic cache file name
                val sanitizedName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val tempCacheFile = File(cacheDir, "view_${driveFileId}_$sanitizedName")

                DriveServiceHelper.streamFileForViewing(drive, driveFileId, tempCacheFile)
                currentActiveFile = tempCacheFile

                withContext(Dispatchers.Main) {
                    displayFile(tempCacheFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error streaming file from Drive", e)
                withContext(Dispatchers.Main) {
                    val msg = when {
                        e.message?.contains("404") == true -> "This document is no longer available in Google Drive."
                        e.message?.contains("Unable to resolve host") == true -> "Unable to load document. Check your internet connection."
                        else -> "Unable to load document: ${e.localizedMessage ?: "Unknown error"}"
                    }
                    showError(true, msg)
                }
            }
        }
    }

    private fun displayFile(file: File) {
        showLoading(false)
        showError(false)

        when {
            isPdf(mimeType, fileName) -> {
                displayPdf(file)
            }
            isDocx(mimeType, fileName) -> {
                displayDocx(file)
            }
            isText(mimeType, fileName) -> {
                displayText(file)
            }
            isImage(mimeType, fileName) -> {
                displayImage(file)
            }
            else -> {
                showUnsupportedFileType()
            }
        }
    }

    private fun displayDocx(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resultText = DocxHelper.extractText(file)
                val finalText = if (resultText.isNotBlank()) resultText else "(Empty Document)"

                withContext(Dispatchers.Main) {
                    rvPdfPages.visibility = View.GONE
                    ivSingleImage.visibility = View.GONE
                    tvPageIndicator.visibility = View.GONE
                    layoutTextViewer.visibility = View.VISIBLE
                    tvDocumentContent.text = finalText
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error reading docx: ${t.message}", t)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DocumentViewerActivity, "Error reading Word document: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displayText(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val text = file.readText(Charsets.UTF_8)
                val displayText = if (text.isNotBlank()) text else "(Empty Text File)"

                withContext(Dispatchers.Main) {
                    rvPdfPages.visibility = View.GONE
                    ivSingleImage.visibility = View.GONE
                    tvPageIndicator.visibility = View.GONE
                    layoutTextViewer.visibility = View.VISIBLE
                    tvDocumentContent.text = displayText
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading text file", e)
                withContext(Dispatchers.Main) {
                    showError(true, "Unable to read text file: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun showUnsupportedFileType() {
        rvPdfPages.visibility = View.GONE
        tvPageIndicator.visibility = View.GONE
        ivSingleImage.visibility = View.GONE
        layoutTextViewer.visibility = View.GONE

        showError(
            true,
            "In-app viewing is supported for PDF, Word (.docx), Text (.txt), and Images (JPEG, PNG).\n\nTap [Download] above to save \"$fileName\" to your device."
        )
        btnRetry.text = "Download"
        btnRetry.setOnClickListener {
            performExplicitDownload()
        }
    }

    private fun displayPdf(file: File) {
        try {
            closePdfRenderer()
            parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(parcelFileDescriptor!!)
            pdfRenderer = renderer

            val pageCount = renderer.pageCount
            tvPageIndicator.visibility = View.VISIBLE
            tvPageIndicator.text = "Page 1 of $pageCount"

            rvPdfPages.visibility = View.VISIBLE
            ivSingleImage.visibility = View.GONE
            layoutTextViewer.visibility = View.GONE

            val layoutManager = LinearLayoutManager(this)
            rvPdfPages.layoutManager = layoutManager
            val adapter = PdfPagesAdapter(renderer)
            rvPdfPages.adapter = adapter

            rvPdfPages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val firstPos = layoutManager.findFirstVisibleItemPosition()
                    if (firstPos != RecyclerView.NO_POSITION) {
                        tvPageIndicator.text = "Page ${firstPos + 1} of $pageCount"
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering PDF", e)
            showError(true, "Failed to render PDF: ${e.localizedMessage}")
        }
    }

    private fun displayImage(file: File) {
        rvPdfPages.visibility = View.GONE
        tvPageIndicator.visibility = View.GONE
        layoutTextViewer.visibility = View.GONE
        ivSingleImage.visibility = View.VISIBLE

        Glide.with(this)
            .load(file)
            .into(ivSingleImage)
    }

    /**
     * Explicit Download: Executed ONLY when the user taps [Download].
     * Saves original file to user-visible Downloads directory.
     */
    private fun performExplicitDownload() {
        val file = currentActiveFile
        if (file != null && file.exists()) {
            saveFileToPublicDownloads(file)
            return
        }

        if (!fileId.isNullOrEmpty()) {
            val account = GoogleSignIn.getLastSignedInAccount(this) ?: return
            Toast.makeText(this, "Downloading document...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val drive = DriveServiceHelper.buildService(this@DocumentViewerActivity, account.email!!)
                    val uri = DriveServiceHelper.downloadFileToPublicDestination(
                        this@DocumentViewerActivity,
                        drive,
                        fileId!!,
                        fileName,
                        mimeType
                    )
                    withContext(Dispatchers.Main) {
                        if (uri != null) {
                            Toast.makeText(this@DocumentViewerActivity, "Document downloaded successfully.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@DocumentViewerActivity, "Failed to save file.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DocumentViewerActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, "File not ready for download", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveFileToPublicDownloads(sourceFile: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocuNova")
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { out ->
                            FileInputStream(sourceFile).use { input ->
                                input.copyTo(out)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@DocumentViewerActivity, "Document downloaded successfully.", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }

                // Legacy storage for API < 29
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloads, "DocuNova").apply { if (!exists()) mkdirs() }
                val targetFile = File(targetDir, fileName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DocumentViewerActivity, "Document downloaded successfully.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy file to Downloads", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DocumentViewerActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        layoutLoading.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            rvPdfPages.visibility = View.GONE
            ivSingleImage.visibility = View.GONE
            tvPageIndicator.visibility = View.GONE
            layoutTextViewer.visibility = View.GONE
            layoutError.visibility = View.GONE
        }
    }

    private fun showError(show: Boolean, message: String = "") {
        layoutError.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            tvErrorMessage.text = message
            layoutLoading.visibility = View.GONE
            rvPdfPages.visibility = View.GONE
            ivSingleImage.visibility = View.GONE
            tvPageIndicator.visibility = View.GONE
            layoutTextViewer.visibility = View.GONE
        }
    }

    private fun closePdfRenderer() {
        try {
            pdfRenderer?.close()
        } catch (ignored: Exception) {}
        pdfRenderer = null

        try {
            parcelFileDescriptor?.close()
        } catch (ignored: Exception) {}
        parcelFileDescriptor = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closePdfRenderer()

        // Clean up temporary viewer cache if it was created just for viewing
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(cacheDir, "viewer_cache")
                if (cacheDir.exists()) {
                    val files = cacheDir.listFiles()
                    // Keep cache bounded (clean files older than 1 hour or if more than 10 files)
                    if (files != null && files.size > 10) {
                        files.sortBy { it.lastModified() }
                        files.take(files.size - 5).forEach { it.delete() }
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    /**
     * RecyclerView Adapter to render each PDF page into a Bitmap using native PdfRenderer.
     */
    private inner class PdfPagesAdapter(private val renderer: PdfRenderer) :
        RecyclerView.Adapter<PdfPagesAdapter.PageViewHolder>() {

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivPage: ImageView = view.findViewById(R.id.ivPdfPage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            return PageViewHolder(view)
        }

        override fun getItemCount(): Int = renderer.pageCount

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            lifecycleScope.launch(Dispatchers.IO) {
                var bitmap: Bitmap?
                try {
                    synchronized(renderer) {
                        val page = renderer.openPage(position)
                        // Scale page nicely for phone screen width (target ~1080px wide)
                        val targetWidth = 1080
                        val scale = targetWidth.toFloat() / page.width.toFloat()
                        val targetHeight = (page.height * scale).toInt()

                        val renderedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        page.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        bitmap = renderedBitmap
                    }

                    withContext(Dispatchers.Main) {
                        bitmap?.let { holder.ivPage.setImageBitmap(it) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error rendering page $position", e)
                }
            }
        }
    }
}
