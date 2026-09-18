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
import android.os.Build
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
import androidx.activity.OnBackPressedCallback
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
import com.developer_rahul.docunova.MainActivity
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.ProcessingDialog
import com.developer_rahul.docunova.RoomDB.AppDatabase
import com.developer_rahul.docunova.RoomDB.RecentFile
import com.developer_rahul.docunova.utils.StorageUtils
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class TypoProcessingActivity : AppCompatActivity() {

    // UI Components
    private lateinit var container: FrameLayout
    private lateinit var fileNameTextView: TextView
    private lateinit var extractedTextEditText: EditText
    private lateinit var copyButton: View
    private lateinit var formatButton: View
    private lateinit var saveButton: View
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
    private var isShowingConvertLayout = false

    // Google Drive & Local Storage
    private lateinit var driveSignInOptions: GoogleSignInOptions
    private var driveService: Drive? = null
    private var pendingFileName: String? = null
    private var pendingFileBytes: ByteArray? = null
    private var pendingLocalFileId: Int? = null
    private var selectedFormat: String = "PDF"

    companion object {
        private const val TAG = "TypoProcessingActivity"
        const val REQUEST_DOCUMENT = 101
        const val REQUEST_STORAGE_PERMISSION = 102
        const val REQUEST_DRIVE_SIGN_IN = 2001
        private const val MAX_IMAGE_DIMENSION = 2560
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
            selectedFileUri = Uri.fromFile(File(currentPhotoPath!!))
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
            Toast.makeText(this, "File saved locally!", Toast.LENGTH_SHORT).show()
            jumpToHome()
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isShowingConvertLayout) {
                    showUploadLayout()
                } else {
                    finish()
                }
            }
        })

        val incomingUri = intent.data ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (incomingUri != null) {
            val mimeType = contentResolver.getType(incomingUri)
            showConvertLayout(incomingUri, mimeType)
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
        if (processingDialog.isShowing()) {
            processingDialog.dismiss()
        }
    }

    private fun showUploadLayout() {
        isShowingConvertLayout = false
        val uploadView = layoutInflater.inflate(R.layout.layout_upload_files_for_extract, null)
        container.removeAllViews()
        container.addView(uploadView)

        uploadView.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

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
                .into(holder.imageView)

            holder.itemView.setOnClickListener { onItemClick(uri) }
        }

        override fun getItemCount() = images.size
    }

    private fun showConvertLayout(uri: Uri, mimeType: String?) {
        isShowingConvertLayout = true
        val convertView = layoutInflater.inflate(R.layout.activity_typo_proccessing, null)
        container.removeAllViews()
        container.addView(convertView)

        convertView.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            showUploadLayout()
        }

        // Initialize UI components
        fileNameTextView = convertView.findViewById(R.id.editTextDocumentName)
        extractedTextEditText = convertView.findViewById(R.id.editTextExtractedText)
        copyButton = convertView.findViewById(R.id.btnCopyText)
        formatButton = convertView.findViewById(R.id.btnFormatText)
        saveButton = convertView.findViewById(R.id.btnSave)
        val tvWordCharCount = convertView.findViewById<TextView>(R.id.tvExtractWordCharCount)

        fun updateWordCharCount(text: CharSequence?) {
            val s = text?.toString() ?: ""
            val trimmed = s.trim()
            val words = if (trimmed.isEmpty()) 0 else trimmed.split(Regex("\\s+")).size
            tvWordCharCount?.text = "$words words • ${s.length} chars"
        }

        extractedTextEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateWordCharCount(s)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        convertView.findViewById<View>(R.id.btnShareText)?.setOnClickListener {
            val text = extractedTextEditText.text.toString()
            if (text.isNotEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Extracted Text"))
            }
        }

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

    private data class SpatialSegment(
        val text: String,
        val box: Rect
    )

    private data class SpatialRow(
        val segments: MutableList<SpatialSegment> = mutableListOf(),
        var top: Int = 0,
        var bottom: Int = 0
    ) {
        val centerY: Float get() = (top + bottom) / 2f
        val height: Int get() = (bottom - top).coerceAtLeast(1)
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

            val formattedText = formatExtractedTextExactLayout(result)

            withContext(Dispatchers.Main) {
                extractedTextEditText.setText(formattedText)
            }
        } catch (e: Exception) {
            throw IOException("Failed to process image: ${e.message}")
        }
    }

    /**
     * Extracts text preserving exact 2D spatial layout (columns, tables, receipts, indentation, paragraph breaks)
     */
    private fun formatExtractedTextExactLayout(textResult: com.google.mlkit.vision.text.Text): String {
        if (textResult.textBlocks.isEmpty()) return ""

        // Check if the document has multi-column flowing prose (e.g. newspaper or research paper)
        if (isMultiColumnArticle(textResult)) {
            return formatMultiColumnArticle(textResult)
        }

        val segments = extractSpatialSegments(textResult)
        if (segments.isEmpty()) {
            return textResult.text.trim()
        }

        return formatSpatialGrid(segments)
    }

    /**
     * Extracts spatial segments from text blocks. Separates lines where wide gaps exist
     * into distinct column segments so table columns and key-value fields align accurately.
     */
    private fun extractSpatialSegments(textResult: com.google.mlkit.vision.text.Text): List<SpatialSegment> {
        val segments = mutableListOf<SpatialSegment>()

        // Estimate character width to identify wide gaps within lines
        val lineEstimates = mutableListOf<Float>()
        for (block in textResult.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox
                if (box != null && line.text.length > 2 && box.width() > 0) {
                    lineEstimates.add(box.width().toFloat() / line.text.length)
                }
            }
        }
        val approxCharWidth = if (lineEstimates.isNotEmpty()) {
            lineEstimates.sorted()[lineEstimates.size / 2].coerceAtLeast(1f)
        } else {
            12f
        }

        for (block in textResult.textBlocks) {
            for (line in block.lines) {
                val lineBox = line.boundingBox ?: continue
                if (line.text.isBlank()) continue

                val elements = line.elements
                if (elements.size <= 1) {
                    segments.add(SpatialSegment(line.text.trim(), lineBox))
                } else {
                    var currentClusterText = StringBuilder()
                    var clusterLeft = -1
                    var clusterTop = lineBox.top
                    var clusterRight = -1
                    var clusterBottom = lineBox.bottom
                    var prevElementRight = -1

                    for (elem in elements) {
                        val elemBox = elem.boundingBox ?: continue
                        if (elem.text.isBlank()) continue

                        val gap = if (prevElementRight != -1) elemBox.left - prevElementRight else 0

                        if (prevElementRight != -1 && gap > approxCharWidth * 2.5f) {
                            if (currentClusterText.isNotBlank()) {
                                segments.add(
                                    SpatialSegment(
                                        currentClusterText.toString().trim(),
                                        Rect(clusterLeft, clusterTop, clusterRight, clusterBottom)
                                    )
                                )
                            }
                            currentClusterText = StringBuilder(elem.text)
                            clusterLeft = elemBox.left
                            clusterTop = elemBox.top
                            clusterRight = elemBox.right
                            clusterBottom = elemBox.bottom
                        } else {
                            if (currentClusterText.isEmpty()) {
                                currentClusterText.append(elem.text)
                                clusterLeft = elemBox.left
                                clusterTop = elemBox.top
                                clusterRight = elemBox.right
                                clusterBottom = elemBox.bottom
                            } else {
                                currentClusterText.append(" ").append(elem.text)
                                clusterRight = maxOf(clusterRight, elemBox.right)
                                clusterTop = minOf(clusterTop, elemBox.top)
                                clusterBottom = maxOf(clusterBottom, elemBox.bottom)
                            }
                        }
                        prevElementRight = elemBox.right
                    }

                    if (currentClusterText.isNotBlank()) {
                        segments.add(
                            SpatialSegment(
                                currentClusterText.toString().trim(),
                                Rect(clusterLeft, clusterTop, clusterRight, clusterBottom)
                            )
                        )
                    }
                }
            }
        }

        return segments
    }

    /**
     * Formats spatial segments into a 2D text grid matching the exact layout of the source.
     */
    private fun formatSpatialGrid(segments: List<SpatialSegment>): String {
        if (segments.isEmpty()) return ""

        val docLeft = segments.minOf { it.box.left }
        val docRight = segments.maxOf { it.box.right }

        val heights = segments.map { it.box.height().coerceAtLeast(1) }.sorted()
        val medianLineHeight = heights[heights.size / 2].toFloat()

        val charWidthEstimates = segments.mapNotNull { seg ->
            if (seg.text.length > 1 && seg.box.width() > 0) {
                seg.box.width().toFloat() / seg.text.length
            } else null
        }.sorted()

        val medianCharWidth = if (charWidthEstimates.isNotEmpty()) {
            charWidthEstimates[charWidthEstimates.size / 2].coerceIn(4f, 60f)
        } else {
            (medianLineHeight * 0.5f).coerceIn(4f, 60f)
        }

        val sortedSegments = segments.sortedWith(compareBy({ it.box.top }, { it.box.left }))

        val rows = mutableListOf<SpatialRow>()
        for (seg in sortedSegments) {
            var bestRow: SpatialRow? = null
            var maxOverlap = 0

            for (row in rows) {
                val overlap = maxOf(0, minOf(row.bottom, seg.box.bottom) - maxOf(row.top, seg.box.top))
                val minH = minOf(row.height, seg.box.height())
                val centerDist = Math.abs(row.centerY - seg.box.centerY().toFloat())

                val matchesRow = (overlap >= 0.38f * minH) || (centerDist <= 0.35f * medianLineHeight)
                if (matchesRow && overlap >= maxOverlap) {
                    maxOverlap = overlap
                    bestRow = row
                }
            }

            if (bestRow != null) {
                bestRow.segments.add(seg)
                bestRow.top = minOf(bestRow.top, seg.box.top)
                bestRow.bottom = maxOf(bestRow.bottom, seg.box.bottom)
            } else {
                rows.add(SpatialRow(mutableListOf(seg), seg.box.top, seg.box.bottom))
            }
        }

        rows.sortBy { it.top }

        val sb = StringBuilder()
        var previousRowBottom: Int? = null

        for (row in rows) {
            if (previousRowBottom != null) {
                val verticalGap = row.top - previousRowBottom
                if (verticalGap > medianLineHeight * 1.35f) {
                    val emptyLines = ((verticalGap / medianLineHeight).toInt() - 1).coerceIn(1, 2)
                    repeat(emptyLines) {
                        sb.append("\n")
                    }
                }
            }

            row.segments.sortBy { it.box.left }

            val rowBuilder = StringBuilder()
            var currentCursorX = docLeft

            for (i in row.segments.indices) {
                val seg = row.segments[i]
                val segLeft = seg.box.left

                if (i == 0) {
                    val indentPx = segLeft - docLeft
                    if (indentPx > medianCharWidth * 1.5f) {
                        val numSpaces = (indentPx / medianCharWidth).roundToInt().coerceIn(1, 80)
                        rowBuilder.append(" ".repeat(numSpaces))
                        currentCursorX = segLeft
                    }
                } else {
                    val gapPx = segLeft - currentCursorX
                    if (gapPx > medianCharWidth * 0.75f) {
                        val spaceCount = (gapPx / medianCharWidth).roundToInt().coerceIn(1, 80)
                        rowBuilder.append(" ".repeat(spaceCount))
                    } else {
                        if (rowBuilder.isNotEmpty() && !rowBuilder.endsWith(" ")) {
                            rowBuilder.append(" ")
                        }
                    }
                }

                rowBuilder.append(seg.text.trim())
                currentCursorX = maxOf(currentCursorX, seg.box.right)
            }

            sb.append(rowBuilder.toString().trimEnd()).append("\n")
            previousRowBottom = row.bottom
        }

        return sb.toString().trimEnd()
    }

    private fun isMultiColumnArticle(textResult: com.google.mlkit.vision.text.Text): Boolean {
        val blocks = textResult.textBlocks
        if (blocks.size < 2) return false

        for (i in 0 until blocks.size - 1) {
            val b1 = blocks[i]
            val r1 = b1.boundingBox ?: continue

            for (j in i + 1 until blocks.size) {
                val b2 = blocks[j]
                val r2 = b2.boundingBox ?: continue

                val horizontalGap = if (r1.right < r2.left) r2.left - r1.right else if (r2.right < r1.left) r1.left - r2.right else -1
                if (horizontalGap < 0) continue

                val vOverlap = maxOf(0, minOf(r1.bottom, r2.bottom) - maxOf(r1.top, r2.top))
                val minH = minOf(r1.height(), r2.height())
                if (minH <= 0 || vOverlap.toFloat() / minH < 0.6f) continue

                if (b1.lines.size >= 4 && b2.lines.size >= 4) {
                    val avgLen1 = b1.lines.map { it.text.length }.average()
                    val avgLen2 = b2.lines.map { it.text.length }.average()
                    if (avgLen1 > 30 && avgLen2 > 30) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun formatMultiColumnArticle(textResult: com.google.mlkit.vision.text.Text): String {
        val sortedBlocks = textResult.textBlocks.sortedWith(compareBy({ it.boundingBox?.left ?: 0 }, { it.boundingBox?.top ?: 0 }))
        val sb = StringBuilder()

        for (block in sortedBlocks) {
            val blockLines = block.lines.mapNotNull { line ->
                val box = line.boundingBox
                if (box != null && line.text.isNotBlank()) SpatialSegment(line.text.trim(), box) else null
            }
            if (blockLines.isNotEmpty()) {
                val formattedBlock = formatSpatialGrid(blockLines)
                sb.append(formattedBlock).append("\n\n")
            }
        }
        return sb.toString().trimEnd()
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
            var extractedWithIText = false
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

                    pdfDocument.close()
                    pdfReader.close()

                    val resultText = stringBuilder.toString().trim()
                    if (resultText.length >= 30) {
                        withContext(Dispatchers.Main) {
                            extractedTextEditText.setText(resultText)
                        }
                        extractedWithIText = true
                        return
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "iText extraction failed or scanned PDF, falling back to OCR", e)
            }

            if (!extractedWithIText) {
                fallbackToPdfOcr(parcelFileDescriptor)
            }
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
                    val pageFormatted = formatExtractedTextExactLayout(result)
                    stringBuilder.append(pageFormatted).append("\n\n")
                    bitmap.recycle()
                }
            }

            withContext(Dispatchers.Main) {
                extractedTextEditText.setText(stringBuilder.toString().trim())
            }

            pdfRenderer.close()
        } catch (e: Exception) {
            throw IOException("OCR fallback failed: ${e.message}")
        }
    }

    private fun calculateOptimalScale(pageWidth: Int, pageHeight: Int): Float {
        val targetWidth = 2200f
        val targetHeight = 3000f

        val widthScale = targetWidth / pageWidth.toFloat()
        val heightScale = targetHeight / pageHeight.toFloat()

        return minOf(widthScale, heightScale).coerceIn(1.5f, 3.5f)
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
                val lines = rawText.split("\n")
                val formattedText = SpannableStringBuilder()

                for (idx in lines.indices) {
                    val line = lines[idx]
                    val trimmed = line.trim()
                    val startLinePos = formattedText.length

                    if (trimmed.isEmpty()) {
                        formattedText.append("\n")
                        continue
                    }

                    formattedText.append(line).append("\n")

                    if (isHeading(trimmed)) {
                        val trimmedStart = line.indexOf(trimmed)
                        val hStart = startLinePos + trimmedStart
                        val hEnd = hStart + trimmed.length
                        formattedText.setSpan(StyleSpan(Typeface.BOLD), hStart, hEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        formattedText.setSpan(RelativeSizeSpan(1.2f), hStart, hEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else {
                        val colonIndex = trimmed.indexOf(':')
                        if (colonIndex in 1..25) {
                            val key = trimmed.substring(0, colonIndex + 1)
                            val kStart = line.indexOf(key)
                            if (kStart != -1) {
                                val s = startLinePos + kStart
                                formattedText.setSpan(StyleSpan(Typeface.BOLD), s, s + key.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        }
                    }
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

    private fun isHeading(text: String): Boolean {
        val wordCount = text.split("\\s+".toRegex()).size
        return wordCount in 1..7 &&
                !text.endsWith(".") &&
                !text.endsWith(":") &&
                text.any { it.isUpperCase() } &&
                text.length < 120
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
            val lightColor = ColorUtils.blendARGB(color, Color.WHITE, 0.92f)
            val strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics).toInt()
            val drawable = GradientDrawable().apply {
                cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics)
                setColor(lightColor)
                setStroke(strokeWidth, color)
            }
            container.background = drawable
            icon.setColorFilter(color)
            text.setTextColor(color)
            checkMark.visibility = View.VISIBLE
            ViewCompat.setElevation(container, 4f)
        } else {
            val drawable = ContextCompat.getDrawable(context, R.drawable.bg_option_selector)
            container.background = drawable
            icon.setColorFilter(ContextCompat.getColor(context, R.color.gray_700))
            text.setTextColor(Color.parseColor("#0F172A"))
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

                val fileExtension = when (selectedFormat) {
                    "PDF" -> ".pdf"
                    "WORD" -> ".docx"
                    else -> ".txt"
                }
                val mimeType = when (selectedFormat) {
                    "PDF" -> "application/pdf"
                    "WORD" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    else -> "text/plain"
                }

                val fullFileName = "$pendingFileName$fileExtension"

                // 1. Save locally to Docunova storage
                val storageDir = StorageUtils.getDocunovaStorageDir(this@TypoProcessingActivity)
                val localSavedFile = File(storageDir, fullFileName)
                FileOutputStream(localSavedFile).use { fos ->
                    fos.write(fileBytes)
                }

                // 2. Insert into local Room database
                val account = GoogleSignIn.getLastSignedInAccount(this@TypoProcessingActivity)
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
                val localId = AppDatabase.getDatabase(this@TypoProcessingActivity).recentFileDao().insert(recentFile)

                withContext(Dispatchers.Main) {
                    pendingFileBytes = fileBytes
                    pendingLocalFileId = localId.toInt()
                    ensureDriveAccountThenUpload()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                    Toast.makeText(this@TypoProcessingActivity, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createPdfFile(content: CharSequence): ByteArray {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        
        val pageWidth = 595 // A4 width in pts
        val pageHeight = 842 // A4 height in pts
        val margin = 36f
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
        return try {
            com.developer_rahul.docunova.DocxHelper.createDocx(content)
        } catch (t: Throwable) {
            Log.e(TAG, "Error generating DOCX via DocxHelper", t)
            content.toString().toByteArray(Charsets.UTF_8)
        }
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

                val fileId = DriveServiceHelper.uploadFileToAppFolder(
                    driveService,
                    this@TypoProcessingActivity,
                    Uri.fromFile(tempFile),
                    fileName
                )

                if (fileId != null && pendingLocalFileId != null) {
                    AppDatabase.getDatabase(this@TypoProcessingActivity).recentFileDao().updateDriveInfo(
                        pendingLocalFileId!!,
                        fileId,
                        null,
                        "UPLOADED"
                    )
                }

                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                    Toast.makeText(this@TypoProcessingActivity, "Saved to device & synced to Google Drive!", Toast.LENGTH_LONG).show()
                    tempFile.delete()
                    jumpToHome()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    processingDialog.dismiss()
                    Toast.makeText(this@TypoProcessingActivity, "Saved to device! Drive upload failed: ${e.message}", Toast.LENGTH_LONG).show()
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