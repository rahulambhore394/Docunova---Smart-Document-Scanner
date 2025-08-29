package com.developer_rahul.docunova.Fragments.Home

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.*
import android.text.style.*
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.BuildConfig
import com.developer_rahul.docunova.DriveServiceHelper
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.ProcessingDialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class TypoProcessingActivity : AppCompatActivity() {

    // UI Components
    private lateinit var container: FrameLayout
    private lateinit var fileNameTextView: TextView
    private lateinit var extractedTextEditText: EditText
    private lateinit var copyButton: Button
    private lateinit var formatButton: Button
    private lateinit var saveButton: Button
    private lateinit var recentImagesRecycler: RecyclerView
    private lateinit var processingDialog: ProcessingDialog

    // File data
    private var selectedFileUri: Uri? = null
    private lateinit var fileType: String
    private lateinit var originalFileName: String
    private var currentPhotoPath: String? = null

    // Processing
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var currentExtractionJob: Job? = null
    private val isProcessing = AtomicBoolean(false)

    // Google Drive Integration
    private lateinit var driveSignInOptions: GoogleSignInOptions
    private var driveService: Drive? = null
    private var pendingFileName: String? = null
    private var pendingFileBytes: ByteArray? = null
    private var selectedFormat: String = "PDF"

    companion object {
        private const val TAG = "TypoProcessingActivity"
        const val REQUEST_DOCUMENT = 101
        const val REQUEST_STORAGE_PERMISSION = 102
        const val REQUEST_DRIVE_SIGN_IN = 2001
        private const val MAX_IMAGE_DIMENSION = 1000
        private const val MAX_RECENT_IMAGES = 16
        private const val INDENT_PER_LEVEL = 30
    }

    // Permission launchers
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openCamera()
        } else {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                showPermissionSettingsDialog("Camera permission is required to take photos")
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoPath != null) {
            selectedFileUri = Uri.fromFile(File(currentPhotoPath))
            showConvertLayout(selectedFileUri!!, "image/jpeg")
        }
    }

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val mimeType = contentResolver.getType(uri)
                showConvertLayout(uri, mimeType)
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
        setContentView(R.layout.activity_text_extraction)
        processingDialog = ProcessingDialog(this)
        container = findViewById(R.id.container)

        // Initialize Google Drive
        driveSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        initializeDriveService()
        showUploadLayout()
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
        if (processingDialog.isShowing()) {
            processingDialog.dismiss()
        }
    }

    private fun showUploadLayout() {
        val uploadView = layoutInflater.inflate(R.layout.layout_upload_files_for_extract, null)
        container.removeAllViews()
        container.addView(uploadView)

        recentImagesRecycler = uploadView.findViewById(R.id.recentImagesRecycler)
        recentImagesRecycler.layoutManager = GridLayoutManager(this, 4)

        checkStoragePermission()

        uploadView.findViewById<Button>(R.id.chooseGalleryBtn).setOnClickListener {
            openDocumentPicker()
        }

        uploadView.findViewById<Button>(R.id.takePhotoBtn).setOnClickListener {
            checkCameraPermission()
        }
    }

    private fun checkStoragePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                loadRecentImages()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) -> {
                showPermissionRationale(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    "Storage permission is needed to access your recent images"
                )
            }
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION
                )
            }
        }
    }

    private fun showPermissionRationale(permission: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Needed")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    REQUEST_STORAGE_PERMISSION
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionSettingsDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("$message. Please enable it in app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadRecentImages()
                } else {
                    Toast.makeText(
                        this,
                        "Permission needed to show recent images",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun loadRecentImages() {
        if (isProcessing.get()) return

        lifecycleScope.launch(Dispatchers.IO) {
            isProcessing.set(true)
            try {
                val images = getRecentImages()
                withContext(Dispatchers.Main) {
                    if (images.isNotEmpty()) {
                        recentImagesRecycler.adapter = RecentImagesAdapter(images) { uri ->
                            val mimeType = contentResolver.getType(uri)
                            showConvertLayout(uri, mimeType)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recent images", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TypoProcessingActivity,
                        "Error loading images: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private fun getRecentImages(): List<Uri> {
        return try {
            val imageUris = mutableListOf<Uri>()
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var count = 0
                while (cursor.moveToNext() && count < MAX_RECENT_IMAGES) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    imageUris.add(contentUri)
                    count++
                }
            }
            imageUris
        } catch (e: Exception) {
            Log.e(TAG, "Error querying images", e)
            emptyList()
        }
    }

    private inner class RecentImagesAdapter(
        private val images: List<Uri>,
        private val onItemClick: (Uri) -> Unit
    ) : RecyclerView.Adapter<RecentImagesAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
        {
            val imageView: ImageView = itemView.findViewById(R.id.imageView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recent_image, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val uri = images[position]
            Glide.with(holder.itemView.context.applicationContext)
                .load(uri)
                .placeholder(R.drawable.ic_placeholder)
                .centerCrop()
                .thumbnail(0.1f)
                .into(holder.imageView)

            holder.itemView.setOnClickListener { onItemClick(uri) }
        }

        override fun getItemCount() = images.size
    }

    private fun showConvertLayout(uri: Uri, mimeType: String?) {
        val convertView = layoutInflater.inflate(R.layout.activity_typo_proccessing, null)
        container.removeAllViews()
        container.addView(convertView)

        // Initialize UI components
        fileNameTextView = convertView.findViewById(R.id.editTextDocumentName)
        extractedTextEditText = convertView.findViewById(R.id.editTextExtractedText)
        copyButton = convertView.findViewById(R.id.btnCopyText)
        formatButton = convertView.findViewById(R.id.btnFormatText)
        saveButton = convertView.findViewById(R.id.btnSave)

        // Set file data
        selectedFileUri = uri
        fileType = when {
            mimeType?.startsWith("image/") == true -> "image"
            mimeType == "application/pdf" -> "pdf"
            else -> "unknown"
        }

        originalFileName = uri.lastPathSegment?.substringBeforeLast('.') ?: "Document"
        fileNameTextView.text = "Text Extraction" // Use generic title for header

        // Set up button listeners
        copyButton.setOnClickListener { copyTextToClipboard() }
        formatButton.setOnClickListener { formatExtractedText() }
        saveButton.setOnClickListener { saveEditedText() }

        // Start text extraction
        startTextExtraction(uri, mimeType)
    }

    private fun startTextExtraction(uri: Uri, mimeType: String?) {
        if (isProcessing.get()) return

        processingDialog.show(message = "Extracting text...")
        currentExtractionJob = lifecycleScope.launch(Dispatchers.IO) {
            isProcessing.set(true)
            try {
                when (fileType) {
                    "image" -> extractTextFromImage(uri)
                    "pdf" -> extractTextFromPdf(uri)
                    else -> showError("Unsupported file type")
                }
            } catch (e: Exception) {
                showError("Error extracting text: ${e.message}")
            } finally {
                isProcessing.set(false)
                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                }
            }
        }
    }

    private suspend fun extractTextFromImage(uri: Uri) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
            options.inJustDecodeBounds = false

            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: throw IOException("Failed to decode image")

            val rotatedBitmap = rotateBitmapIfRequired(bitmap, uri)
            val image = InputImage.fromBitmap(rotatedBitmap, 0)
            val result = textRecognizer.process(image).await()

            withContext(Dispatchers.Main) {
                // Improve layout by grouping into blocks
                val sb = StringBuilder()
                for (block in result.textBlocks) {
                    sb.append(block.text).append("\n\n")
                }
                extractedTextEditText.setText(sb.toString().trim())
            }
        } catch (e: Exception) {
            throw IOException("Failed to process image: ${e.message}")
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    else -> bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating bitmap", e)
            bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private suspend fun extractTextFromPdf(uri: Uri) {
        val parcelFileDescriptor = try {
            contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Failed to open PDF")
        } catch (e: Exception) {
            throw IOException("Error opening PDF: ${e.message}")
        }

        try {
            // First try with iText
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val pdfReader = PdfReader(inputStream)
                    val pdfDocument = PdfDocument(pdfReader)
                    val pageCount = pdfDocument.numberOfPages
                    val stringBuilder = StringBuilder()

                    for (i in 1..pageCount) {
                        if (isProcessing.get().not()) break
                        updateProgress(i, pageCount)
                        val text = PdfTextExtractor.getTextFromPage(pdfDocument.getPage(i))
                        stringBuilder.append(text).append("\n\n")
                    }

                    withContext(Dispatchers.Main) {
                        extractedTextEditText.setText(stringBuilder.toString())
                    }

                    pdfDocument.close()
                    pdfReader.close()
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "iText extraction failed, falling back to OCR")
            }

            // Fallback to OCR
            fallbackToPdfOcr(parcelFileDescriptor)
        } finally {
            try {
                parcelFileDescriptor.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing PDF", e)
            }
        }
    }

    private suspend fun fallbackToPdfOcr(parcelFileDescriptor: android.os.ParcelFileDescriptor) {
        try {
            val pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)
            val pageCount = pdfRenderer.pageCount
            val stringBuilder = StringBuilder()

            for (i in 0 until pageCount) {
                if (isProcessing.get().not()) break
                updateProgress(i + 1, pageCount)

                pdfRenderer.openPage(i).use { page ->
                    val scale = calculateOptimalScale(page.width, page.height)
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
                    stringBuilder.append(result.text).append("\n\n")
                    bitmap.recycle()
                }
            }

            withContext(Dispatchers.Main) {
                extractedTextEditText.setText(stringBuilder.toString())
            }

            pdfRenderer.close()
        } catch (e: Exception) {
            throw IOException("OCR fallback failed: ${e.message}")
        }
    }

    private fun calculateOptimalScale(pageWidth: Int, pageHeight: Int): Float {
        val maxWidth = 1200
        val maxHeight = 1600

        val widthScale = maxWidth.toFloat() / pageWidth
        val heightScale = maxHeight.toFloat() / pageHeight

        return minOf(widthScale, heightScale, 1.0f).coerceAtLeast(0.1f)
    }

    private fun copyTextToClipboard() {
        val text = extractedTextEditText.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "No text to copy", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Extracted text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun formatExtractedText() {
        val rawText = extractedTextEditText.text.toString()
        if (rawText.isBlank()) {
            Toast.makeText(this, "No text to format", Toast.LENGTH_SHORT).show()
            return
        }

        processingDialog.show(message = "Formatting text...")

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // Relaxed cleaning to preserve potential alignment
                val cleanedText = rawText
                    .replace("(\\s*\\n){3,}".toRegex(), "\n\n")
                    .trim()

                val paragraphs = cleanedText.split("\n\n").map { it.trim() }
                val formattedText = SpannableStringBuilder()
                var currentListType: ListType? = null
                var listCounter = 1
                var isFirstParagraph = true

                for (paragraph in paragraphs) {
                    if (paragraph.isBlank()) continue

                    val paragraphType = when {
                        isHeading(paragraph) -> ParagraphType.HEADING
                        isSubheading(paragraph) -> ParagraphType.SUBHEADING
                        isList(paragraph) -> ParagraphType.LIST
                        else -> ParagraphType.NORMAL
                    }

                    if (paragraphType != ParagraphType.LIST) {
                        currentListType = null
                        listCounter = 1
                    }

                    if (!isFirstParagraph) {
                        formattedText.append("\n\n")
                    }

                    when (paragraphType) {
                        ParagraphType.HEADING -> formatHeading(formattedText, paragraph)
                        ParagraphType.SUBHEADING -> formatSubheading(formattedText, paragraph)
                        ParagraphType.LIST -> {
                            val detectedListType = detectListType(paragraph)
                            if (currentListType != detectedListType) {
                                listCounter = 1
                                currentListType = detectedListType
                            }
                            listCounter = formatList(formattedText, paragraph, detectedListType, listCounter)
                        }
                        ParagraphType.NORMAL -> formatNormalParagraph(formattedText, paragraph)
                    }

                    isFirstParagraph = false
                }

                withContext(Dispatchers.Main) {
                    extractedTextEditText.setText(formattedText)
                    processingDialog.dismiss()
                    Toast.makeText(
                        this@TypoProcessingActivity,
                        "Document formatted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                    showError("Error formatting text: ${e.message}")
                }
            }
        }
    }

    private enum class ParagraphType { HEADING, SUBHEADING, LIST, NORMAL }
    private enum class ListType { BULLET, NUMBERED }

    private fun isList(text: String): Boolean {
        val listPatterns = listOf(
            Regex("^(\\s*)([•\\-*]|\\d+[.)])\\s+"),
            Regex("\\n(\\s*)([•\\-*]|\\d+[.)])\\s+")
        )
        return listPatterns.any { it.containsMatchIn(text) }
    }

    private fun detectListType(text: String): ListType {
        val numberedPattern = Regex("(^|\\n)\\s*\\d+[.)]")
        return if (numberedPattern.containsMatchIn(text)) ListType.NUMBERED else ListType.BULLET
    }

    private fun formatList(
        builder: SpannableStringBuilder,
        text: String,
        listType: ListType,
        startCounter: Int
    ): Int {
        val lines = text.split("\n")
        var counter = startCounter

        for (line in lines) {
            if (line.isBlank()) continue

            val listItemPattern = Regex("^(\\s*)([•\\-*]|\\d+[.)])\\s+")
            val matchResult = listItemPattern.find(line)
            val indentLevel = matchResult?.groups?.get(1)?.value?.length ?: 0
            val indentSize = INDENT_PER_LEVEL * (indentLevel / 2 + 1)

            val content = line.replace(listItemPattern, "").trim()
            val start = builder.length

            when (listType) {
                ListType.BULLET -> builder.append("• ")
                ListType.NUMBERED -> builder.append("${counter++}. ")
            }

            builder.append(content)
            builder.append("\n")
            val end = builder.length

            builder.setSpan(
                LeadingMarginSpan.Standard(indentSize, 0),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return counter
    }

    private fun isHeading(text: String): Boolean {
        val wordCount = text.split("\\s+".toRegex()).size
        return wordCount in 1..7 &&
                !text.endsWith(".") &&
                !text.endsWith(":") &&
                text.any { it.isUpperCase() } &&
                text.length < 120
    }

    private fun isSubheading(text: String): Boolean {
        val wordCount = text.split("\\s+".toRegex()).size
        return wordCount in 3..15 &&
                (text.endsWith(":") || text.endsWith("-")) &&
                text.length < 200
    }

    private fun formatHeading(builder: SpannableStringBuilder, text: String) {
        val start = builder.length
        builder.append(text)
        val end = builder.length

        builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(RelativeSizeSpan(1.4f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun formatSubheading(builder: SpannableStringBuilder, text: String) {
        val start = builder.length
        builder.append(text)
        val end = builder.length

        builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(RelativeSizeSpan(1.2f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun formatNormalParagraph(builder: SpannableStringBuilder, text: String) {
        val start = builder.length
        builder.append(text)
        val end = builder.length

        builder.setSpan(LeadingMarginSpan.Standard(INDENT_PER_LEVEL, 0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun saveEditedText() {
        val text = extractedTextEditText.text
        if (text.isBlank()) {
            Toast.makeText(this, "No text to save", Toast.LENGTH_SHORT).show()
            return
        }

        promptFileNameAndSave()
    }

    /** Prompt filename and upload to Google Drive */
    private fun promptFileNameAndSave() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filename_prompt, null).apply {
            findViewById<EditText>(R.id.filename_input).apply {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                setText("Scan_$ts")
                setSelection(text.length)
            }
            startAnimation(AnimationUtils.loadAnimation(this@TypoProcessingActivity, R.anim.dialog_fade_in))
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Next", null)
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val nextButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            nextButton.setOnClickListener {
                val input = dialogView.findViewById<EditText>(R.id.filename_input)
                val fileName = input.text.toString().trim()
                if (fileName.isEmpty()) {
                    input.error = "File name cannot be empty"
                    return@setOnClickListener
                }

                pendingFileName = fileName
                dialog.dismiss()
                showFormatSelectionDialog()
            }
        }
        dialog.show()
    }

    private fun showFormatSelectionDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_format_selection, null)
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val pdfOption = dialogView.findViewById<LinearLayout>(R.id.option_pdf)
        val wordOption = dialogView.findViewById<LinearLayout>(R.id.option_word)
        val textOption = dialogView.findViewById<LinearLayout>(R.id.option_text)

        val pdfIcon = dialogView.findViewById<ImageView>(R.id.icon_pdf)
        val wordIcon = dialogView.findViewById<ImageView>(R.id.icon_word)
        val textIcon = dialogView.findViewById<ImageView>(R.id.icon_text)

        val pdfText = dialogView.findViewById<TextView>(R.id.text_pdf)
        val wordText = dialogView.findViewById<TextView>(R.id.text_word)
        val textText = dialogView.findViewById<TextView>(R.id.text_text)

        val checkPdf = dialogView.findViewById<ImageView>(R.id.check_pdf)
        val checkWord = dialogView.findViewById<ImageView>(R.id.check_word)
        val checkText = dialogView.findViewById<ImageView>(R.id.check_text)

        // Selection Logic
        fun resetSelection() {
            updateSelectionUI(pdfOption, pdfIcon, pdfText, checkPdf, false, R.color.pdf_color)
            updateSelectionUI(wordOption, wordIcon, wordText, checkWord, false, R.color.word_color)
            updateSelectionUI(textOption, textIcon, textText, checkText, false, R.color.text_color)
        }

        pdfOption.setOnClickListener {
            resetSelection()
            updateSelectionUI(pdfOption, pdfIcon, pdfText, checkPdf, true, R.color.pdf_color)
            selectedFormat = "PDF"
        }

        wordOption.setOnClickListener {
            resetSelection()
            updateSelectionUI(wordOption, wordIcon, wordText, checkWord, true, R.color.word_color)
            selectedFormat = "WORD"
        }

        textOption.setOnClickListener {
            resetSelection()
            updateSelectionUI(textOption, textIcon, textText, checkText, true, R.color.text_color)
            selectedFormat = "TEXT"
        }

        // Default
        pdfOption.performClick()

        dialogView.findViewById<Button>(R.id.btn_confirm).setOnClickListener {
            prepareFileForUpload()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateSelectionUI(
        container: LinearLayout,
        icon: ImageView,
        text: TextView,
        checkMark: ImageView,
        isSelected: Boolean,
        colorRes: Int
    ) {
        val context = container.context
        val color = ContextCompat.getColor(context, colorRes)
        
        if (isSelected) {
            val lightColor = ColorUtils.blendARGB(color, Color.WHITE, 0.9f)
            val drawable = GradientDrawable().apply {
                cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics)
                setColor(lightColor)
                setStroke(4, color)
            }
            container.background = drawable
            icon.setColorFilter(color)
            text.setTextColor(color)
            checkMark.visibility = View.VISIBLE
            ViewCompat.setElevation(container, 8f)
        } else {
            val drawable = ContextCompat.getDrawable(context, R.drawable.bg_option_selector)
            container.background = drawable
            icon.setColorFilter(ContextCompat.getColor(context, R.color.gray_700))
            text.setTextColor(ContextCompat.getColor(context, R.color.black))
            checkMark.visibility = View.GONE
            ViewCompat.setElevation(container, 0f)
        }
    }

    private fun prepareFileForUpload() {
        val content = extractedTextEditText.text
        if (content.isBlank()) {
            Toast.makeText(this, "No text to save", Toast.LENGTH_SHORT).show()
            return
        }

        processingDialog.show(message = "Preparing file...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileBytes = when (selectedFormat) {
                    "PDF" -> createPdfFile(content)
                    "WORD" -> createWordFile(content)
                    else -> createTextFile(content.toString())
                }

                withContext(Dispatchers.Main) {
                    pendingFileBytes = fileBytes
                    ensureDriveAccountThenUpload()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                    Toast.makeText(this@TypoProcessingActivity, "Error preparing file", Toast.LENGTH_LONG).show()
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
        
        val pageWidth = 595 // A4 width in pts
        val pageHeight = 842 // A4 height in pts
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
            
            // Clip to current page area
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
            if (content is Spanned) {
                // Find sub-spans within this paragraph part
                val pStart = content.toString().indexOf(pText)
                if (pStart != -1) {
                    val pEnd = pStart + pText.length
                    var i = pStart
                    while (i < pEnd) {
                        val next = content.nextSpanTransition(i, pEnd, Object::class.java)
                        val run = paragraph.createRun()
                        run.setText(content.subSequence(i, next).toString())
                        
                        val spans = content.getSpans(i, next, Object::class.java)
                        for (span in spans) {
                            when (span) {
                                is StyleSpan -> if (span.style == Typeface.BOLD) run.isBold = true
                                is RelativeSizeSpan -> run.fontSize = (12 * span.sizeChange).toInt()
                            }
                        }
                        i = next
                    }
                } else {
                    paragraph.createRun().setText(pText)
                }
            } else {
                paragraph.createRun().setText(pText)
            }
        }

        val stream = ByteArrayOutputStream()
        doc.write(stream)
        doc.close()
        return stream.toByteArray()
    }

    private fun createTextFile(text: String): ByteArray = text.toByteArray()

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

    private fun uploadFileToDrive() {
        if (pendingFileBytes == null || pendingFileName == null) return

        val driveService = driveService ?: run {
            Toast.makeText(this, "Drive service not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        runOnUiThread { processingDialog.show(message = "Uploading to Google Drive...") }

        val fileExtension = when (selectedFormat) {
            "PDF" -> ".pdf"
            "WORD" -> ".docx"
            else -> ".txt"
        }
        val fileName = "${pendingFileName}$fileExtension"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempFile = File(cacheDir, fileName)
                FileOutputStream(tempFile).use { it.write(pendingFileBytes!!) }

                DriveServiceHelper.uploadFileToAppFolder(
                    driveService,
                    this@TypoProcessingActivity,
                    Uri.fromFile(tempFile),
                    fileName
                )

                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                    Toast.makeText(this@TypoProcessingActivity, "File uploaded successfully!", Toast.LENGTH_LONG).show()
                    tempFile.delete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                    Toast.makeText(this@TypoProcessingActivity, "Upload failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
        }
        documentPickerLauncher.launch(intent)
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            val photoFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {
                null
            }

            if (photoFile != null) {
                val photoURI = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", photoFile)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                currentPhotoPath = photoFile.absolutePath
                takePictureLauncher.launch(photoURI)
            }
        }
    }

    private fun createImageFile(): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile("JPEG_${ts}_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
    }

    private fun updateProgress(curr: Int, total: Int) {
        runOnUiThread { processingDialog.updateText(message = "Processing page $curr of $total") }
    }

    private fun showError(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }
}