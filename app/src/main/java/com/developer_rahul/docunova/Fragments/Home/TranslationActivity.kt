package com.developer_rahul.docunova.Fragments.Home

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.developer_rahul.docunova.BuildConfig
import com.developer_rahul.docunova.DriveServiceHelper
import com.developer_rahul.docunova.MainActivity
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.ProcessingDialog
import com.developer_rahul.docunova.RoomDB.AppDatabase
import com.developer_rahul.docunova.RoomDB.RecentFile
import com.developer_rahul.docunova.utils.StorageUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.material.button.MaterialButton
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.developer_rahul.docunova.TranslationApiClient
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class TranslationActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private lateinit var tabOriginal: TextView
    private lateinit var tabTranslated: TextView
    private lateinit var spinnerLanguages: Spinner
    private lateinit var textViewTranslatedContent: EditText
    private lateinit var processingDialog: ProcessingDialog
    private lateinit var textRecognizer: com.google.mlkit.vision.text.TextRecognizer
    private lateinit var btnSaveFile: Button
    private val isProcessing = AtomicBoolean(false)
    private var isShowingTranslationContent = false

    private var extractedText = ""
    private var selectedFileUri: Uri? = null
    private var fileType: String = "unknown"
    private var currentPhotoPath: String? = null
    private var currentExtractionJob: Job? = null
    private var selectedFormat: String = "PDF"

    // File Saving & Cloud Sync
    private var pendingFileName: String? = null
    private var pendingFileBytes: ByteArray? = null
    private var pendingLocalFileId: Int? = null
    private lateinit var driveSignInOptions: GoogleSignInOptions
    private var driveService: Drive? = null

    companion object {
        private const val REQUEST_DOCUMENT = 101
        private const val REQUEST_DRIVE_SIGN_IN = 2001
        private const val MAX_IMAGE_DIMENSION = 2560
    }

    private val languages = listOf(
        "Select Language", "Hindi", "Marathi", "Spanish", "French",
        "German", "Tamil", "Gujarati", "Kannada", "Bengali", "Punjabi",
        "Telugu", "Malayalam", "Urdu", "Arabic", "Chinese", "Japanese", "Russian", "Portuguese", "Italian"
    )

    private val languageCodes = mapOf(
        "Hindi" to "hi",
        "Marathi" to "mr",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Tamil" to "ta",
        "Gujarati" to "gu",
        "Kannada" to "kn",
        "Bengali" to "bn",
        "Punjabi" to "pa",
        "Telugu" to "te",
        "Malayalam" to "ml",
        "Urdu" to "ur",
        "Arabic" to "ar",
        "Chinese" to "zh-CN",
        "Japanese" to "ja",
        "Russian" to "ru",
        "Portuguese" to "pt",
        "Italian" to "it"
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) openCamera()
        else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoPath != null) {
            selectedFileUri = Uri.fromFile(File(currentPhotoPath!!))
            startTextExtraction(selectedFileUri!!, "image/jpeg")
        }
    }

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedFileUri = uri
                startTextExtraction(uri, contentResolver.getType(uri))
            }
        }
    }

    private val driveSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            initializeDriveService()
            uploadFileToDrive()
        } else {
            Toast.makeText(this, "File saved locally!", Toast.LENGTH_SHORT).show()
            jumpToHome()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translation)

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        processingDialog = ProcessingDialog(this)
        container = findViewById(R.id.container)

        driveSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        initializeDriveService()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isShowingTranslationContent) {
                    showUploadLayout()
                } else {
                    finish()
                }
            }
        })

        if (intent.hasExtra("EXTRACTED_TEXT")) {
            extractedText = intent.getStringExtra("EXTRACTED_TEXT") ?: ""
            showTranslationContent()
        } else if (intent.hasExtra("FILE_URI")) {
            val uriString = intent.getStringExtra("FILE_URI")
            selectedFileUri = Uri.parse(uriString)
            val mimeType = contentResolver.getType(selectedFileUri!!)
            startTextExtraction(selectedFileUri!!, mimeType)
        } else {
            showUploadLayout()
        }
    }

    private fun initializeDriveService() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        account?.let {
            driveService = DriveServiceHelper.buildService(this, account.email ?: "")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentExtractionJob?.cancel()
        if (processingDialog.isShowing()) processingDialog.dismiss()
    }

    /** Show upload options */
    private fun showUploadLayout() {
        isShowingTranslationContent = false
        val uploadView = layoutInflater.inflate(R.layout.layout_upload_files_for_tranlate, null)
        container.removeAllViews()
        container.addView(uploadView)

        uploadView.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val editTextManualInput = uploadView.findViewById<EditText>(R.id.etInputText)
        val btnPaste = uploadView.findViewById<View>(R.id.btnPasteText)
        val btnClear = uploadView.findViewById<View>(R.id.btnClearText)
        val tvWordCharCount = uploadView.findViewById<TextView>(R.id.tvWordCharCount)

        btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)
            val pasteData = item?.text?.toString()
            if (!pasteData.isNullOrEmpty()) {
                editTextManualInput.setText(pasteData)
                editTextManualInput.setSelection(pasteData.length)
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        btnClear.setOnClickListener {
            editTextManualInput.setText("")
        }

        editTextManualInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: ""
                btnClear.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                val trimmed = text.trim()
                val words = if (trimmed.isEmpty()) 0 else trimmed.split(Regex("\\s+")).size
                tvWordCharCount.text = "$words words • ${text.length} chars"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        uploadView.findViewById<Button>(R.id.btnGallery).setOnClickListener { openDocumentPicker() }
        uploadView.findViewById<Button>(R.id.btnCamera)
            .setOnClickListener { checkCameraPermission() }
        uploadView.findViewById<Button>(R.id.btnProceed).setOnClickListener {
            val manualText = editTextManualInput.text.toString().trim()
            if (manualText.isNotEmpty()) {
                extractedText = manualText
                showTranslationContent()
            } else Toast.makeText(this, "Please enter text to translate", Toast.LENGTH_SHORT).show()
        }
    }

    /** Show translation UI */
    private fun showTranslationContent() {
        isShowingTranslationContent = true
        val contentView = layoutInflater.inflate(R.layout.layout_translation, null)
        container.removeAllViews()
        container.addView(contentView)

        contentView.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            showUploadLayout()
        }

        tabOriginal = contentView.findViewById(R.id.tabOriginal)
        tabTranslated = contentView.findViewById(R.id.tabTranslated)
        spinnerLanguages = contentView.findViewById(R.id.spinnerLanguage)
        textViewTranslatedContent = contentView.findViewById(R.id.editTextTranslatedText)
        btnSaveFile = contentView.findViewById(R.id.btnSaveFile)

        val btnCopy = contentView.findViewById<View>(R.id.btnCopyResult)
        btnCopy.setOnClickListener {
            val text = textViewTranslatedContent.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("DocuNova Translation", text))
                Toast.makeText(this, "Translation copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        val btnShare = contentView.findViewById<View>(R.id.btnShareResult)
        btnShare.setOnClickListener {
            val text = textViewTranslatedContent.text.toString()
            if (text.isNotEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Translation"))
            }
        }

        val pdfButton = contentView.findViewById<MaterialButton>(R.id.pdfDownload)
        val wordButton = contentView.findViewById<MaterialButton>(R.id.wordDownload)

        textViewTranslatedContent.setText(extractedText)

        setupTabs()
        setupSpinner()

        // Selection logic for export options
        pdfButton.setOnClickListener { setFormatSelection(pdfButton, wordButton, "PDF") }
        wordButton.setOnClickListener { setFormatSelection(wordButton, pdfButton, "WORD") }

        // Default selection
        setFormatSelection(pdfButton, wordButton, "PDF")

        btnSaveFile.setOnClickListener { promptFileNameAndSave() }
    }


    private fun setFormatSelection(selected: MaterialButton, other: MaterialButton, format: String) {
        selected.setBackgroundColor(Color.parseColor("#2563EB"))
        selected.setTextColor(Color.WHITE)
        selected.iconTint = ColorStateList.valueOf(Color.WHITE)

        other.setBackgroundColor(Color.parseColor("#F1F5F9"))
        other.setTextColor(Color.parseColor("#334155"))
        other.iconTint = ColorStateList.valueOf(Color.parseColor("#475569"))
        
        selectedFormat = format
    }


    /** Extract text from image or PDF */
    private fun startTextExtraction(uri: Uri, mimeType: String?) {
        if (isProcessing.get()) return

        fileType = when {
            mimeType?.contains("pdf") == true -> "pdf"
            mimeType?.contains("image") == true -> "image"
            else -> "unknown"
        }

        processingDialog.show(message = "Extracting text...")
        currentExtractionJob = lifecycleScope.launch(Dispatchers.IO) {
            isProcessing.set(true)
            try {
                when (fileType) {
                    "image" -> {
                        extractTextFromImage(uri)
                    }
                    "pdf" -> {
                        extractTextFromPdf(uri)
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            showError("Unsupported file type")
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    showTranslationContent()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showError("Error extracting text: ${e.message}")
                }
            } finally {
                isProcessing.set(false)
                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                }
            }
        }
    }


    private suspend fun extractTextFromPdf(uri: Uri) {
        var foundText = false
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val pdfReader = PdfReader(inputStream)
                val pdfDocument = PdfDocument(pdfReader)
                val pageCount = pdfDocument.numberOfPages
                val stringBuilder = StringBuilder()

                for (i in 1..pageCount) {
                    updateProgress(i, pageCount)
                    val text = PdfTextExtractor.getTextFromPage(pdfDocument.getPage(i))
                    stringBuilder.append(text).append("\n\n")
                }

                pdfDocument.close()
                pdfReader.close()

                val res = stringBuilder.toString().trim()
                if (res.length >= 30) {
                    extractedText = res
                    foundText = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "iText extraction failed, falling back to OCR: ${e.message}")
        }

        if (!foundText) {
            fallbackToPdfOcr(uri)
        }
    }

    private suspend fun fallbackToPdfOcr(uri: Uri) {
        val parcelFileDescriptor = try {
            contentResolver.openFileDescriptor(uri, "r") ?: return
        } catch (e: Exception) {
            return
        }

        try {
            val pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)
            val pageCount = pdfRenderer.pageCount
            val stringBuilder = StringBuilder()

            for (i in 0 until pageCount) {
                updateProgress(i + 1, pageCount)
                pdfRenderer.openPage(i).use { page ->
                    val scale = (2000f / page.width.toFloat()).coerceIn(1.5f, 3.5f)
                    val bitmap = try {
                        Bitmap.createBitmap(
                            (page.width * scale).toInt(),
                            (page.height * scale).toInt(),
                            Bitmap.Config.ARGB_8888
                        )
                    } catch (e: OutOfMemoryError) {
                        Bitmap.createBitmap(
                            (page.width * scale).toInt(),
                            (page.height * scale).toInt(),
                            Bitmap.Config.RGB_565
                        )
                    }

                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    val result = textRecognizer.process(inputImage).await()
                    for (block in result.textBlocks) {
                        for (line in block.lines) {
                            stringBuilder.append(line.text).append("\n")
                        }
                        stringBuilder.append("\n")
                    }
                    bitmap.recycle()
                }
            }
            pdfRenderer.close()
            extractedText = stringBuilder.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "PDF OCR fallback error: ${e.message}")
        } finally {
            try {
                parcelFileDescriptor.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing PDF descriptor", e)
            }
        }
    }

    private fun updateProgress(currentPage: Int, totalPages: Int) {
        runOnUiThread {
            processingDialog.updateText(message = "Processing page $currentPage of $totalPages")
        }
    }

    private suspend fun extractTextFromImage(uri: Uri) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
        options.inJustDecodeBounds = false

        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Failed to decode image")

        val rotatedBitmap = rotateBitmapIfRequired(bitmap, uri)
        val image = InputImage.fromBitmap(rotatedBitmap, 0)
        val result = textRecognizer.process(image).await()

        val sb = StringBuilder()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                sb.append(line.text).append("\n")
            }
            sb.append("\n")
        }
        extractedText = sb.toString().trim()
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val orientation = if (uri.scheme == "file" && uri.path != null) {
                ExifInterface(uri.path!!).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } else {
                contentResolver.openInputStream(uri)?.use { input ->
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            }

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Tabs */
    private var translatedText: String? = null

    private fun setupTabs() {
        fun updateTabUI(isTranslatedTab: Boolean) {
            val tvContentStatus = container.findViewById<TextView>(R.id.tvContentStatus)
            if (isTranslatedTab) {
                tabTranslated.setBackgroundResource(R.drawable.bg_segmented_active)
                tabTranslated.setTextColor(Color.WHITE)
                tabOriginal.setBackgroundResource(R.drawable.bg_segmented_inactive)
                tabOriginal.setTextColor(Color.parseColor("#64748B"))
                textViewTranslatedContent.setText(translatedText ?: "Translation will appear here...")
                tvContentStatus?.text = "Translation Output"
            } else {
                tabOriginal.setBackgroundResource(R.drawable.bg_segmented_active)
                tabOriginal.setTextColor(Color.WHITE)
                tabTranslated.setBackgroundResource(R.drawable.bg_segmented_inactive)
                tabTranslated.setTextColor(Color.parseColor("#64748B"))
                textViewTranslatedContent.setText(extractedText)
                tvContentStatus?.text = "Original Document Text"
            }
        }

        // Default: Original Tab
        updateTabUI(isTranslatedTab = false)

        tabOriginal.setOnClickListener {
            updateTabUI(isTranslatedTab = false)
        }

        tabTranslated.setOnClickListener {
            updateTabUI(isTranslatedTab = true)
        }
    }

    /** Language spinner */
    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, R.layout.item_spinner_language, languages)
        adapter.setDropDownViewResource(R.layout.item_spinner_language_dropdown)
        spinnerLanguages.adapter = adapter
        spinnerLanguages.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedLanguage = languages[position]
                if (selectedLanguage != "Select Language") translateText(selectedLanguage)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun translateText(selectedLanguage: String) {
        val targetLanguageCode = languageCodes[selectedLanguage] ?: return
        if (extractedText.isBlank()) {
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        processingDialog.show(message = "Translating to $selectedLanguage...")

        lifecycleScope.launch(Dispatchers.Main) {
            val result = TranslationApiClient.translate(extractedText, targetLanguageCode)
            processingDialog.dismiss()

            result.onSuccess { translated ->
                translatedText = translated

                // Switch to translated tab
                tabTranslated.setBackgroundResource(R.drawable.bg_segmented_active)
                tabTranslated.setTextColor(Color.WHITE)
                tabOriginal.setBackgroundResource(R.drawable.bg_segmented_inactive)
                tabOriginal.setTextColor(Color.parseColor("#64748B"))

                val tvContentStatus = container.findViewById<TextView>(R.id.tvContentStatus)
                tvContentStatus?.text = "Translation Output ($selectedLanguage)"

                textViewTranslatedContent.setText(translated)
                Toast.makeText(this@TranslationActivity, "Translated to $selectedLanguage", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(this@TranslationActivity, error.message ?: "Translation failed", Toast.LENGTH_LONG).show()
            }
        }
    }
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openDocumentPicker()
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }


    private fun openDocumentPicker() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
                }
                documentPickerLauncher.launch(intent)
            }
            else -> {
                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }


    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> openCamera()

            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                Log.e("Camera", "Error creating file", ex)
                null
            }

            if (photoFile != null) {
                val appId = BuildConfig.APPLICATION_ID
                val photoURI = FileProvider.getUriForFile(
                    this,
                    "${appId}.fileprovider",
                    photoFile
                )

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                currentPhotoPath = photoFile.absolutePath
                takePictureLauncher.launch(photoURI)
            }
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }



    private fun showError(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    /** Prompt filename and upload */
    private fun promptFileNameAndSave() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filename_prompt, null).apply {
            findViewById<EditText>(R.id.filename_input).apply {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                setText("Scan_$ts")
                setSelection(text.length)
            }
            startAnimation(AnimationUtils.loadAnimation(this@TranslationActivity, R.anim.dialog_fade_in))
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val input = dialogView.findViewById<EditText>(R.id.filename_input)
                val fileName = input.text.toString().trim()
                if (fileName.isEmpty()) {
                    input.error = "File name cannot be empty"
                    return@setOnClickListener
                }

                prepareFileForUpload(fileName)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** Prepare file bytes, save locally to device storage & RoomDB, then sync to Drive */
    private fun prepareFileForUpload(filename: String) {
        val content = textViewTranslatedContent.text
        if (content.isBlank()) {
            Toast.makeText(this, "No text to save", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileBytes = if (selectedFormat == "PDF") {
                    createPdfFile(content)
                } else {
                    createWordFile(content)
                }

                val fileExtension = if (selectedFormat == "PDF") ".pdf" else ".docx"
                val fullFileName = "$filename$fileExtension"
                val mimeType = if (selectedFormat == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

                // 1. Save locally to Docunova storage
                val storageDir = StorageUtils.getDocunovaStorageDir(this@TranslationActivity)
                val localSavedFile = File(storageDir, fullFileName)
                FileOutputStream(localSavedFile).use { fos ->
                    fos.write(fileBytes)
                }

                // 2. Insert into local Room database
                val account = GoogleSignIn.getLastSignedInAccount(this@TranslationActivity)
                val userId = account?.email ?: "local_user"
                val recentFile = RecentFile(
                    userId = userId,
                    name = fullFileName,
                    filePath = localSavedFile.absolutePath,
                    mimeType = mimeType,
                    date = StorageUtils.getCurrentDate(),
                    size = localSavedFile.length(),
                    isSynced = false,
                    uploadStatus = "PENDING"
                )
                val localId = AppDatabase.getDatabase(this@TranslationActivity).recentFileDao().insert(recentFile)

                withContext(Dispatchers.Main) {
                    pendingFileName = filename
                    pendingFileBytes = fileBytes
                    pendingLocalFileId = localId.toInt()
                    ensureDriveAccountThenUpload()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TranslationActivity,
                        "Error saving file: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun createPdfFile(content: CharSequence): ByteArray {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        
        val pageWidth = 595 
        val pageHeight = 842 
        val margin = 40f
        val textWidth = (pageWidth - margin * 2).toInt()
        
        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(content, 0, content.length, paint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(content, paint, textWidth, Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false)
        }
        
        var currentLine = 0
        var pageNum = 1
        while (currentLine < layout.lineCount) {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            val startY = layout.getLineTop(currentLine)
            canvas.save()
            canvas.translate(margin, margin - startY)
            
            val clipRect = Rect(0, startY, pageWidth, (startY + pageHeight - margin * 2).toInt())
            canvas.clipRect(clipRect)
            
            layout.draw(canvas)
            canvas.restore()
            
            pdfDocument.finishPage(page)
            
            val nextPageY = startY + pageHeight - margin * 2
            currentLine = layout.getLineForVertical(nextPageY.toInt()) + 1
            pageNum++
        }
        
        val stream = ByteArrayOutputStream()
        pdfDocument.writeTo(stream)
        pdfDocument.close()
        return stream.toByteArray()
    }

    private fun createWordFile(content: CharSequence): ByteArray {
        return try {
            com.developer_rahul.docunova.DocxHelper.createDocx(content)
        } catch (t: Throwable) {
            Log.e("TranslationActivity", "Error generating DOCX via DocxHelper", t)
            content.toString().toByteArray(Charsets.UTF_8)
        }
    }

    /** Check Google sign-in and upload */
    private fun ensureDriveAccountThenUpload() {
        val last = GoogleSignIn.getLastSignedInAccount(this)
        val hasDrive = last != null && GoogleSignIn.hasPermissions(last, Scope(DriveScopes.DRIVE_FILE))

        if (hasDrive) {
            uploadFileToDrive()
        } else {
            val signInIntent = GoogleSignIn.getClient(this, driveSignInOptions).signInIntent
            driveSignInLauncher.launch(signInIntent)
        }
    }

    /** Upload using DriveServiceHelper */
    private fun uploadFileToDrive() {
        if (pendingFileBytes == null || pendingFileName == null) {
            Toast.makeText(this, "No file to upload", Toast.LENGTH_SHORT).show()
            return
        }

        val driveService = driveService ?: run {
            Toast.makeText(this, "Drive service not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        runOnUiThread {
            processingDialog.show(message = "Uploading to Google Drive...")
        }

        val fileExtension = if (selectedFormat == "PDF") ".pdf" else ".docx"
        val fileName = "${pendingFileName}$fileExtension"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempFile = File(cacheDir, fileName)
                FileOutputStream(tempFile).use { fos ->
                    fos.write(pendingFileBytes!!)
                }

                val fileId = DriveServiceHelper.uploadFileToAppFolder(
                    driveService,
                    this@TranslationActivity,
                    Uri.fromFile(tempFile),
                    fileName
                )

                if (fileId != null && pendingLocalFileId != null) {
                    AppDatabase.getDatabase(this@TranslationActivity).recentFileDao().updateDriveInfo(
                        pendingLocalFileId!!,
                        fileId,
                        null,
                        "UPLOADED"
                    )
                }

                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                    Toast.makeText(
                        this@TranslationActivity,
                        "Saved to device & synced to Google Drive!",
                        Toast.LENGTH_LONG
                    ).show()
                    tempFile.delete()
                    jumpToHome()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                    Toast.makeText(
                        this@TranslationActivity,
                        "Saved locally! Drive upload failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    jumpToHome()
                }
            }
        }
    }

    private fun jumpToHome() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("NAVIGATE_TO_HOME", true)
        }
        startActivity(intent)
        finish()
    }
}