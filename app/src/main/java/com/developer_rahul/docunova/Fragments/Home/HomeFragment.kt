package com.developer_rahul.docunova.Fragments.Home

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.Adapters.RecentFileAdapter
import com.developer_rahul.docunova.DriveServiceHelper
import com.developer_rahul.docunova.DriveServiceHelper.downloadFile
import com.developer_rahul.docunova.Fragments.SharedViewModel
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.RoomDB.AppDatabase
import com.developer_rahul.docunova.RoomDB.RecentFile
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.developer_rahul.docunova.Fragments.Files.DriveFileModel
import com.developer_rahul.docunova.Fragments.Files.FileAdapter
import com.developer_rahul.docunova.Fragments.Files.FilesFragment
import com.developer_rahul.docunova.Fragments.Setting.SettingFragment
//import com.developer_rahul.docunova.TranslationActivity
//import com.developer_rahul.docunova.TypoProcessingActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
    private val SUPABASE_URL = "https://grtzvwunaxlcbhqncizo.supabase.co"
    private val SUPABASE_API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdydHp2d3VuYXhsY2JocW5jaXpvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njg4ODU3NDEsImV4cCI6MjA4NDQ2MTc0MX0.-W7GdFb_r8hFIFXEFUIEXHhqb01e-nDjBN_ZZl75dQ0"
    private val SUPABASE_STORAGE_URL = "$SUPABASE_URL/storage/v1"
    private val BUCKET_NAME = "user-documents"
    private lateinit var driveAdapter: FileAdapter

    private lateinit var tvTotalFiles: TextView
    private lateinit var tvTotalSize: TextView
    private lateinit var sharedViewModel: SharedViewModel

    private lateinit var shimmerScannedCount: ShimmerFrameLayout
    private lateinit var shimmerStorageUsed: ShimmerFrameLayout
    private lateinit var shimmerRecentFiles: ShimmerFrameLayout
    private lateinit var layoutScannedData: View
    private lateinit var layoutStorageData: View

    private var scanner: GmsDocumentScanner? = null
    private var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    private lateinit var recyclerView: RecyclerView
    private val adapter by lazy {
        RecentFileAdapter(emptyList()) { file ->
            if (file.filePath.isNotEmpty()) {
                openDocumentFromSupabase(file)
            } else {
                downloadDocument(file)
            }
        }
    }
    private lateinit var db: AppDatabase

    private lateinit var tvUsername: TextView
    private lateinit var profileImage: ImageView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var googleDriveSignInLauncher: ActivityResultLauncher<Intent>
    private val driveGso by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
    }
    private val driveSignInClient by lazy { GoogleSignIn.getClient(requireContext(), driveGso) }
    private var pendingPdfUri: Uri? = null
    private var pendingDriveFileName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedViewModel = ViewModelProvider(requireActivity()).get(SharedViewModel::class.java)

        sharedViewModel.scanTrigger.observe(viewLifecycleOwner) { shouldScan ->
            if (shouldScan) {
                launchDocumentScanner()
                sharedViewModel.resetTrigger()
            }
        }
        tvTotalFiles = view.findViewById(R.id.tv_scanned_files_count)
        tvTotalSize = view.findViewById(R.id.tv_drive_size)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh)

        shimmerScannedCount = view.findViewById(R.id.shimmer_scanned_count)
        shimmerStorageUsed = view.findViewById(R.id.shimmer_storage_used)
        shimmerRecentFiles = view.findViewById(R.id.shimmer_recent_files)
        layoutScannedData = view.findViewById(R.id.layout_scanned_data)
        layoutStorageData = view.findViewById(R.id.layout_storage_data)

        db = AppDatabase.getDatabase(requireContext())
        initializeViews(view)
        setupScanner()
        setupRecyclerView()
        fetchUserDetails()

        driveAdapter = FileAdapter(
            requireContext(),
            emptyList(),
            FileAdapter.VIEW_TYPE_LIST,
            onDownloadClick = { file -> downloadFileFromDrive(file.id, file.name) },
            onFileClick = { file -> openDriveFile(file) },
            onMoreClick = { file, anchorView -> showFileOptionsMenu(file, anchorView) }
        )
        recyclerView.adapter = driveAdapter

        // Initial Drive load attempt
        autoLinkDrive()

        fetchUserDocumentsFromSupabase()
        setupSwipeRefresh()

        googleDriveSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    val uri = pendingPdfUri
                    val name = pendingDriveFileName
                    if (uri != null && name != null) {
                        uploadToDrive(account, uri, name)
                    } else {
                        loadDriveFiles()
                    }
                }
            } catch (e: ApiException) {
                pendingPdfUri = null
                pendingDriveFileName = null
            }
        }
    }

    private fun autoLinkDrive() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            loadDriveFiles()
        } else {
            driveSignInClient.silentSignIn().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    loadDriveFiles()
                } else {
                    tvTotalFiles.text = "0"
                    tvTotalSize.text = "Drive not linked"
                    stopShimmers()
                }
            }
        }
    }

    private fun startShimmers() {
        shimmerScannedCount.startShimmer()
        shimmerStorageUsed.startShimmer()
        shimmerRecentFiles.startShimmer()
        shimmerScannedCount.visibility = View.VISIBLE
        shimmerStorageUsed.visibility = View.VISIBLE
        shimmerRecentFiles.visibility = View.VISIBLE
        
        layoutScannedData.visibility = View.GONE
        layoutStorageData.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    private fun stopShimmers() {
        shimmerScannedCount.stopShimmer()
        shimmerStorageUsed.stopShimmer()
        shimmerRecentFiles.stopShimmer()
        shimmerScannedCount.visibility = View.GONE
        shimmerStorageUsed.visibility = View.GONE
        shimmerRecentFiles.visibility = View.GONE
        
        layoutScannedData.visibility = View.VISIBLE
        layoutStorageData.visibility = View.VISIBLE
        recyclerView.visibility = View.VISIBLE
    }

    private fun uploadToDrive(account: GoogleSignInAccount, uri: Uri, name: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                DriveServiceHelper.uploadFileToAppFolder(drive, requireContext(), uri, "$name.pdf")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Uploaded to Drive", Toast.LENGTH_SHORT).show()
                    loadDriveFiles()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Drive upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                pendingPdfUri = null
                pendingDriveFileName = null
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch {
                autoLinkDrive()
                fetchUserDocumentsFromSupabase()
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun openDriveFile(file: DriveFileModel) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            if (isGoogleDriveAppInstalled()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://drive.google.com/file/d/${file.id}/view")
                        setPackage("com.google.android.apps.docs")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    openFileInBrowser(file)
                }
            } else {
                openFileInBrowser(file)
            }
        } else {
            googleDriveSignInLauncher.launch(driveSignInClient.signInIntent)
        }
    }

    private fun openFileInBrowser(file: DriveFileModel) {
        val url = "https://drive.google.com/file/d/${file.id}/view"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isGoogleDriveAppInstalled(): Boolean {
        return try {
            requireContext().packageManager.getPackageInfo("com.google.android.apps.docs", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showFileOptionsMenu(file: DriveFileModel, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_file_options, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> { shareFile(file); true }
                R.id.action_rename -> { renameFile(file); true }
                R.id.action_delete -> { deleteFile(file); true }
                else -> false
            }
        }
        popup.setForceShowIcon(true)
        popup.show()
    }

    private fun shareFile(file: DriveFileModel) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TEXT, "https://drive.google.com/file/d/${file.id}/view")
        }
        startActivity(Intent.createChooser(shareIntent, "Share file"))
    }

    private fun renameFile(file: DriveFileModel) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_rename_file, null)
        val editText = dialogView.findViewById<EditText>(R.id.renameInput)
        editText.setText(file.name)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename File")
            .setView(dialogView)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != file.name) performRenameFile(file, newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performRenameFile(file: DriveFileModel, newName: String) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                drive.files().update(file.id, com.google.api.services.drive.model.File().setName(newName)).execute()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "File renamed", Toast.LENGTH_SHORT).show()
                    loadDriveFiles()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to rename: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteFile(file: DriveFileModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete '${file.name}'?")
            .setPositiveButton("Delete") { _, _ -> performDeleteFile(file) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDeleteFile(file: DriveFileModel) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                drive.files().delete(file.id).execute()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "File deleted", Toast.LENGTH_SHORT).show()
                    loadDriveFiles()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initializeViews(view: View) {
        tvUsername = view.findViewById(R.id.tv_Username)
        profileImage = view.findViewById(R.id.profileImage_home)
        view.findViewById<View>(R.id.iv_scan).setOnClickListener { launchDocumentScanner() }
        profileImage.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingFragment())
                .addToBackStack("Setting")
                .commit()
        }

        view.findViewById<View>(R.id.cardStorage).setOnClickListener {
            val account = GoogleSignIn.getLastSignedInAccount(requireContext())
            if (account == null || !GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
                googleDriveSignInLauncher.launch(driveSignInClient.signInIntent)
            }
        }

        setupOptionButtons(view)
    }

    private fun loadDriveFiles() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            
            lifecycleScope.launch(Dispatchers.Main) {
                startShimmers()
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                    val files = DriveServiceHelper.listFilesFromAppFolder(drive)
                    val driveFiles = files.map { file ->
                        DriveFileModel(file.id, file.name, file.mimeType, file.size ?: 0L, file.modifiedTime, file.thumbnailLink, file.webContentLink, file.createdTime)
                    }
                    val totalSize = driveFiles.sumOf { it.size }
                    withContext(Dispatchers.Main) {
                        driveAdapter.updateFiles(driveFiles.take(4))
                        tvTotalFiles.text = driveFiles.size.toString()
                        tvTotalSize.text = formatFileSize(totalSize)
                        stopShimmers()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "Drive load failed", e)
                        tvTotalSize.text = "Error loading"
                        stopShimmers()
                    }
                }
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var s = size.toDouble()
        var i = 0
        while (s >= 1024 && i < units.size - 1) { s /= 1024; i++ }
        return String.format("%.2f %s", s, units[i])
    }

    private fun downloadFileFromDrive(fileId: String, fileName: String) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Docunova")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                downloadFile(drive, fileId, file)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Downloaded to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupOptionButtons(view: View) {
        val options = listOf(
            view.findViewById<View>(R.id.tv_select_upload),
            view.findViewById<View>(R.id.tv_start_translate),
            view.findViewById<View>(R.id.tv_view_files)
        )
        options.forEach { button ->
            button.setOnClickListener {
                options.forEach { it.isSelected = false }; button.isSelected = true
                when (button.id) {
                    R.id.tv_select_upload -> startActivity(Intent(requireContext(), TypoProcessingActivity::class.java))
                    R.id.tv_start_translate -> startActivity(Intent(requireContext(), TranslationActivity::class.java))
                    R.id.tv_view_files -> parentFragmentManager.beginTransaction().replace(R.id.fragment_container, FilesFragment()).addToBackStack("files").commit()
                }
            }
        }
    }

    private fun setupScanner() {
        scanner = GmsDocumentScanning.getClient(GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build())
        scannerLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) handleScanResult(result.data)
        }
    }

    private fun handleScanResult(data: Intent?) {
        val result = GmsDocumentScanningResult.fromActivityResultIntent(data)
        if (result?.pdf != null && result.pages?.isNotEmpty() == true) {
            promptFileNameAndSave(result.pdf!!.uri, result.pages!![0].imageUri)
        }
    }

    private fun promptFileNameAndSave(pdfUri: Uri, thumbnailUri: Uri) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filename_prompt, null)
        val input = dialogView.findViewById<EditText>(R.id.filename_input)
        input.setText("Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}")
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) ensureDriveAccountThenUpload(pdfUri, name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun ensureDriveAccountThenUpload(pdfUri: Uri, fileName: String) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            uploadToDrive(account, pdfUri, fileName)
        } else {
            pendingPdfUri = pdfUri; pendingDriveFileName = fileName
            googleDriveSignInLauncher.launch(driveSignInClient.signInIntent)
        }
    }

    private fun setupRecyclerView() {
        recyclerView = requireView().findViewById<RecyclerView>(R.id.recentFilesRecycler).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.adapter
        }
        db.recentFileDao().getAllFiles().observe(viewLifecycleOwner) { files -> adapter.updateFiles(files ?: emptyList()) }
    }

    private fun fetchUserDetails() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val loginMethod = prefs.getString("login_method", null)
        val cachedEmail = prefs.getString("email", "User")
        val cachedName = prefs.getString("full_name", cachedEmail)
        tvUsername.text = cachedName
        val cachedAvatar = if (loginMethod == "google") prefs.getString("google_avatar_url", null) else prefs.getString("avatar_url", null)
        if (!cachedAvatar.isNullOrEmpty()) {
            Glide.with(this).load(cachedAvatar).placeholder(R.drawable.ic_profile).into(profileImage)
        }
        if (loginMethod == "email") fetchSupabaseUserInfo(prefs)
    }

    private fun fetchSupabaseUserInfo(prefs: android.content.SharedPreferences) {
        prefs.getString("access_token", null)?.let { token ->
            val request = object : JsonObjectRequest(Method.GET, "$SUPABASE_URL/auth/v1/user", null,
                { response -> handleUserResponse(response) },
                { _ -> Log.e(TAG, "Profile load failed") }
            ) {
                override fun getHeaders() = mapOf("apikey" to SUPABASE_API_KEY, "Authorization" to "Bearer $token", "Accept" to "application/json").toMutableMap()
            }
            Volley.newRequestQueue(requireContext()).add(request)
        }
    }

    private fun handleUserResponse(response: JSONObject) {
        val email = response.optString("email", "No Email")
        val metadata = response.optJSONObject("user_metadata")
        val fullName = metadata?.optString("full_name", email) ?: email
        tvUsername.text = fullName
        metadata?.optString("avatar_url")?.takeIf { it.isNotEmpty() }?.let { url ->
            Glide.with(this).load(url).placeholder(R.drawable.ic_profile).into(profileImage)
        }
    }

    private fun fetchUserDocumentsFromSupabase() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)
        val token = prefs.getString("access_token", null)
        if (userId != null && token != null) {
            val url = "$SUPABASE_STORAGE_URL/object/list/$BUCKET_NAME?prefix=$userId/"
            val request = object : JsonObjectRequest(Method.GET, url, null,
                { response -> handleDocumentsResponse(response) }, { _ -> }
            ) {
                override fun getHeaders() = mapOf("apikey" to SUPABASE_API_KEY, "Authorization" to "Bearer $token").toMutableMap()
            }
            Volley.newRequestQueue(requireContext()).add(request)
        }
    }

    private fun handleDocumentsResponse(response: JSONObject) {
        try {
            val documents = mutableListOf<RecentFile>()
            val items = response.getJSONArray("data")
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                documents.add(RecentFile(name = item.getString("name").substringAfterLast('/'), filePath = "", thumbnailUri = "", date = "Recently", isSynced = true))
            }
            lifecycleScope.launch(Dispatchers.Main) { adapter.updateFiles(documents) }
        } catch (_: Exception) {}
    }

    private fun openDocumentFromSupabase(file: RecentFile) {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", null) ?: return
        val url = "$SUPABASE_STORAGE_URL/object/$BUCKET_NAME/$userId/${file.name}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun downloadDocument(file: RecentFile) {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", null) ?: return
        val token = prefs.getString("access_token", null) ?: return
        val url = "$SUPABASE_STORAGE_URL/object/$BUCKET_NAME/$userId/${file.name}"
        val request = object : StringRequest(Method.GET, url, { _ -> openDocumentFromSupabase(file) }, { _ -> }) {
            override fun getHeaders() = mapOf("apikey" to SUPABASE_API_KEY, "Authorization" to "Bearer $token").toMutableMap()
        }
        Volley.newRequestQueue(requireContext()).add(request)
    }

    fun launchDocumentScanner() {
        scanner?.getStartScanIntent(requireActivity())?.addOnSuccessListener { intentSender ->
            scannerLauncher?.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }
}
