package com.developer_rahul.docunova.Fragments.Home

import android.Manifest
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.developer_rahul.docunova.BuildConfig
import com.developer_rahul.docunova.DriveServiceHelper
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.ProcessingDialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.material.button.MaterialButton
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.apache.poi.xwpf.usermodel.XWPFDocument
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

    private var extractedText = ""
    private var selectedFileUri: Uri? = null
    private var fileType: String = "unknown"
    private var translator: Translator? = null
    private var currentPhotoPath: String? = null
    private var currentExtractionJob: Job? = null
    private var selectedFormat: String = "PDF"

    // Google Drive Pending Info
    private var pendingFileName: String? = null
    private var pendingFileBytes: ByteArray? = null
    private lateinit var driveSignInOptions: GoogleSignInOptions
    private var driveService: Drive? = null

    companion object {
        private const val REQUEST_DOCUMENT = 101
        private const val REQUEST_DRIVE_SIGN_IN = 2001
        private const val MAX_IMAGE_DIMENSION = 1000
    }

    private val languages = listOf(
        "Select Language", "Hindi", "Marathi", "Spanish", "French",
        "German", "Tamil", "Gujarati", "Kannada", "Bengali", "Punjabi"
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
        "Punjabi" to "pa"
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
            selectedFileUri = Uri.fromFile(File(currentPhotoPath))
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
            Toast.makeText(this, "Google Drive sign-in failed", Toast.LENGTH_SHORT).show()
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
        translator?.close()
        currentExtractionJob?.cancel()
        if (processingDialog.isShowing()) processingDialog.dismiss()
    }

    /** Show upload options */
    private fun showUploadLayout() {
        val uploadView = layoutInflater.inflate(R.layout.layout_upload_files_for_tranlate, null)
        container.removeAllViews()
        container.addView(uploadView)

        val editTextManualInput = uploadView.findViewById<EditText>(R.id.etInputText)

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
        val contentView = layoutInflater.inflate(R.layout.layout_translation, null)
        container.removeAllViews()
        container.addView(contentView)

        tabOriginal = contentView.findViewById(R.id.tabOriginal)
        tabTranslated = contentView.findViewById(R.id.tabTranslated)
        spinnerLanguages = contentView.findViewById(R.id.spinnerLanguage)
        textViewTranslatedContent = contentView.findViewById(R.id.editTextTranslatedText)
        btnSaveFile = contentView.findViewById(R.id.btnSaveFile)

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
        // Highlight logic
        selected.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
        selected.setTextColor(Color.WHITE)
        selected.iconTint = ColorStateList.valueOf(Color.WHITE)

        other.setBackgroundColor(Color.TRANSPARENT)
        other.setTextColor(Color.BLACK)
        other.iconTint = ColorStateList.valueOf(Color.BLACK)
        
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

                extractedText = stringBuilder.toString()
                pdfDocument.close()
                pdfReader.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "iText extraction failed, falling back to OCR")
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
        options.inSampleSize =
            calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
        options.inJustDecodeBounds = false

        val bitmap = contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Failed to decode image")
        val rotatedBitmap = rotateBitmapIfRequired(bitmap, uri)
        val image = InputImage.fromBitmap(rotatedBitmap, 0)
        val result = textRecognizer.process(image).await()
        
        // Group by blocks to preserve visual separation
        val sb = StringBuilder()
        for (block in result.textBlocks) {
            sb.append(block.text).append("\n\n")
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
            contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    else -> bitmap
                }
            } ?: bitmap
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
        val selectedBgOriginal = R.drawable.tab_left_selected
        val selectedBgTranslated = R.drawable.tab_right_selected
        val unselectedBgOriginal = R.drawable.tab_left_unselected
        val unselectedBgTranslated = R.drawable.tab_right_unselected

        fun setActiveTab(selectedTab: TextView, otherTab: TextView, selectedBg: Int, otherBg: Int) {
            selectedTab.setBackgroundResource(selectedBg)
            selectedTab.setTypeface(null, Typeface.BOLD)
            selectedTab.setTextColor(Color.WHITE)

            otherTab.setBackgroundResource(otherBg)
            otherTab.setTypeface(null, Typeface.NORMAL)
            otherTab.setTextColor(Color.BLACK)
        }

        // Default: Original Tab
        setActiveTab(tabOriginal, tabTranslated, selectedBgOriginal, unselectedBgTranslated)
        textViewTranslatedContent.setText(extractedText)

        tabOriginal.setOnClickListener {
            setActiveTab(tabOriginal, tabTranslated, selectedBgOriginal, unselectedBgTranslated)
            textViewTranslatedContent.setText(extractedText)
        }

        tabTranslated.setOnClickListener {
            setActiveTab(tabTranslated, tabOriginal, selectedBgTranslated, unselectedBgOriginal)
            textViewTranslatedContent.setText(translatedText ?: "Translation will appear here...")
        }
    }


    /** Language spinner */
    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show(); return
        }

        processingDialog.show(message = "Translating to $selectedLanguage...")
        translator?.close()

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLanguageCode)
            .build()
        translator = Translation.getClient(options)

        translator?.downloadModelIfNeeded(DownloadConditions.Builder().build())
            ?.addOnSuccessListener {
                translator?.translate(extractedText)
                    ?.addOnSuccessListener { result ->
                        processingDialog.dismiss()
                        translatedText = result
                        if (tabTranslated.background.constantState == resources.getDrawable(R.drawable.tab_right_selected).constantState) {
                            textViewTranslatedContent.setText(result)
                        }
                        Toast.makeText(this, "Translated to $selectedLanguage", Toast.LENGTH_SHORT).show()
                    }

                    ?.addOnFailureListener { e ->
                        processingDialog.dismiss()
                        Toast.makeText(this, "Translation failed: ${e.message}", Toast.LENGTH_LONG)
                            .show()
                    }
            }
            ?.addOnFailureListener { e ->
                processingDialog.dismiss()
                Toast.makeText(this, "Model download failed: ${e.message}", Toast.LENGTH_LONG)
                    .show()
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

    /** Prepare file bytes and ensure sign-in */
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

                withContext(Dispatchers.Main) {
                    pendingFileName = filename
                    pendingFileBytes = fileBytes
                    ensureDriveAccountThenUpload()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TranslationActivity,
                        "Error preparing file",
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
        
        val layout = StaticLayout(content, paint, pageWidth - (margin * 2).toInt(), 
            Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false)
        
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
        val doc = XWPFDocument()
        val paragraphs = content.split("\n\n")
        
        for (pText in paragraphs) {
            val paragraph = doc.createParagraph()
            paragraph.createRun().setText(pText)
        }

        val stream = ByteArrayOutputStream()
        doc.write(stream)
        doc.close()
        return stream.toByteArray()
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

                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                    Toast.makeText(
                        this@TranslationActivity,
                        "File uploaded successfully!",
                        Toast.LENGTH_LONG
                    ).show()
                    tempFile.delete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                    Toast.makeText(
                        this@TranslationActivity,
                        "Upload failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}