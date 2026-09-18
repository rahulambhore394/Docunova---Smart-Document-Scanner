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
import android.graphics.Color
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

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

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleScanResult(result.data)
        }
    }

    private val googleDriveSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                val localF = pendingLocalFile
                val recId = pendingFileRecordId
                val name = pendingDriveFileName
                if (localF != null && recId != null && name != null) {
                    uploadLocalFileToDrive(account, localF, recId, name)
                } else {
                    loadDriveFiles()
                }
                retryPendingUploads()
            }
        } catch (e: ApiException) {
            pendingLocalFile = null
            pendingFileRecordId = null
            pendingDriveFileName = null
        }
    }

    private lateinit var recyclerView: RecyclerView
    private val adapter by lazy {
        RecentFileAdapter(
            emptyList(),
            onItemClick = { recentFile ->
                openRecentFile(recentFile)
            },
            onMoreClick = { recentFile, _ ->
                showRecentFileOptionsMenu(recentFile)
            }
        )
    }
    private lateinit var db: AppDatabase

    private lateinit var tvUsername: TextView
    private lateinit var profileImage: ImageView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val driveGso by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
    }
    private val driveSignInClient by lazy { GoogleSignIn.getClient(requireContext(), driveGso) }
    private var pendingLocalFile: File? = null
    private var pendingFileRecordId: Int? = null
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

        setupSwipeRefresh()
    }

    private fun getCurrentUserId(): String {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        return account?.id ?: account?.email ?: "default_user"
    }

    private fun autoLinkDrive() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            loadDriveFiles()
            retryPendingUploads()
        } else {
            driveSignInClient.silentSignIn().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    loadDriveFiles()
                    retryPendingUploads()
                } else {
                    tvTotalFiles.text = "0"
                    tvTotalSize.text = "Drive not linked"
                    stopShimmers()
                }
            }
        }
    }

    private fun retryPendingUploads() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return
        if (!GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pendingFiles = db.recentFileDao().getPendingUploads(getCurrentUserId())
                for (pending in pendingFiles) {
                    val file = File(pending.filePath)
                    if (file.exists()) {
                        uploadLocalFileToDrive(account, file, pending.id, pending.name)
                    }
                }
            } catch (ignored: Exception) {}
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

    private fun uploadLocalFileToDrive(account: GoogleSignInAccount, localFile: File, recordId: Int, fileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                db.recentFileDao().updateUploadStatus(recordId, "UPLOADING")
                val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                val driveFileId = DriveServiceHelper.uploadLocalFile(drive, localFile, fileName, "application/pdf")
                val folderId = DriveServiceHelper.getSubfolderForMimeType(drive, "application/pdf")

                db.recentFileDao().updateDriveInfo(recordId, driveFileId, folderId, "UPLOADED")

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Uploaded to Google Drive", Toast.LENGTH_SHORT).show()
                    loadDriveFiles()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Drive upload failed", e)
                db.recentFileDao().updateUploadStatus(recordId, "FAILED")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Upload pending (offline or error)", Toast.LENGTH_SHORT).show()
                }
            } finally {
                pendingLocalFile = null
                pendingFileRecordId = null
                pendingDriveFileName = null
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch {
                autoLinkDrive()
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun openDriveFile(file: DriveFileModel) {
        val intent = com.developer_rahul.docunova.DocumentViewerActivity.createIntent(
            context = requireContext(),
            fileId = file.id,
            fileName = file.name,
            mimeType = file.mimeType
        )
        startActivity(intent)
    }

    private fun openRecentFile(recentFile: RecentFile) {
        val intent = com.developer_rahul.docunova.DocumentViewerActivity.createIntent(
            context = requireContext(),
            fileId = recentFile.driveFileId,
            fileName = recentFile.name,
            mimeType = recentFile.mimeType.takeIf { it.isNotEmpty() } ?: "application/pdf",
            localPath = recentFile.filePath.takeIf { it.isNotEmpty() }
        )
        startActivity(intent)
    }

    private fun showRecentFileOptionsMenu(recentFile: RecentFile) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_file_options, null)
        dialog.setContentView(sheetView)

        val tvSheetFileName = sheetView.findViewById<TextView>(R.id.tvSheetFileName)
        val tvSheetFileDetails = sheetView.findViewById<TextView>(R.id.tvSheetFileDetails)
        val ivSheetFileIcon = sheetView.findViewById<ImageView>(R.id.ivSheetFileIcon)

        tvSheetFileName.text = recentFile.name
        val formattedSize = if (recentFile.size > 0) DriveServiceHelper.formatFileSize(recentFile.size) else ""
        tvSheetFileDetails.text = if (formattedSize.isNotEmpty() && recentFile.date.isNotEmpty()) {
            "$formattedSize • ${recentFile.date}"
        } else formattedSize.ifEmpty { recentFile.date }

        val ext = recentFile.name.substringAfterLast('.', "").lowercase()
        when (ext) {
            "pdf" -> {
                ivSheetFileIcon.setImageResource(R.drawable.ic_pdf)
                ivSheetFileIcon.setColorFilter(Color.parseColor("#EF4444"))
            }
            "doc", "docx" -> {
                ivSheetFileIcon.setImageResource(R.drawable.ic_word)
                ivSheetFileIcon.setColorFilter(Color.parseColor("#2563EB"))
            }
            "txt" -> {
                ivSheetFileIcon.setImageResource(R.drawable.ic_text)
                ivSheetFileIcon.setColorFilter(Color.parseColor("#10B981"))
            }
            "jpg", "jpeg", "png" -> {
                ivSheetFileIcon.setImageResource(R.drawable.ic_scanned_files)
                ivSheetFileIcon.setColorFilter(Color.parseColor("#8B5CF6"))
            }
            else -> {
                ivSheetFileIcon.setImageResource(R.drawable.ic_documents_stack_24)
                ivSheetFileIcon.setColorFilter(Color.parseColor("#2563EB"))
            }
        }

        sheetView.findViewById<View>(R.id.actionSheetShare).setOnClickListener {
            dialog.dismiss()
            shareRecentFile(recentFile)
        }

        sheetView.findViewById<View>(R.id.actionSheetRename).setOnClickListener {
            dialog.dismiss()
            renameRecentFile(recentFile)
        }

        sheetView.findViewById<View>(R.id.actionSheetDelete).setOnClickListener {
            dialog.dismiss()
            deleteRecentFile(recentFile)
        }

        dialog.show()
    }

    private fun shareRecentFile(recentFile: RecentFile) {
        val file = File(recentFile.filePath)
        if (file.exists()) {
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = recentFile.mimeType.takeIf { it.isNotEmpty() } ?: "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Document"))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else if (!recentFile.driveFileId.isNullOrEmpty()) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, recentFile.name)
                putExtra(Intent.EXTRA_TEXT, "https://drive.google.com/file/d/${recentFile.driveFileId}/view")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Document"))
        } else {
            Toast.makeText(requireContext(), "File not available locally", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renameRecentFile(recentFile: RecentFile) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_rename_file, null)
        val editText = dialogView.findViewById<EditText>(R.id.renameInput)
        editText.setText(recentFile.name)
        val dotIndex = recentFile.name.lastIndexOf('.')
        if (dotIndex > 0) {
            editText.setSelection(0, dotIndex)
        } else {
            editText.selectAll()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != recentFile.name) {
                    performRenameRecentFile(recentFile, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performRenameRecentFile(recentFile: RecentFile, newName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var updatedPath = recentFile.filePath
                val oldFile = File(recentFile.filePath)
                if (oldFile.exists()) {
                    val newFile = File(oldFile.parentFile, newName)
                    if (oldFile.renameTo(newFile)) {
                        updatedPath = newFile.absolutePath
                    }
                }

                db.recentFileDao().updateFileName(recentFile.id, newName, updatedPath)

                // If also uploaded to Drive, rename in Drive as well
                val driveId = recentFile.driveFileId
                if (!driveId.isNullOrEmpty()) {
                    val account = GoogleSignIn.getLastSignedInAccount(requireContext())
                    if (account != null) {
                        try {
                            val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                            drive.files().update(
                                driveId,
                                com.google.api.services.drive.model.File().setName(newName)
                            ).execute()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to rename in Drive: ${e.message}")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Document renamed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Rename failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteRecentFile(recentFile: RecentFile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Document")
            .setMessage("Are you sure you want to delete '${recentFile.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                performDeleteRecentFile(recentFile)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDeleteRecentFile(recentFile: RecentFile) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Delete local file
                val file = File(recentFile.filePath)
                if (file.exists()) {
                    file.delete()
                }
                // Delete thumbnail
                if (recentFile.thumbnailUri.isNotEmpty()) {
                    val thumb = File(recentFile.thumbnailUri)
                    if (thumb.exists()) thumb.delete()
                }

                // Delete from Room
                db.recentFileDao().deleteById(recentFile.id)

                // Delete from Drive if exists
                val driveId = recentFile.driveFileId
                if (!driveId.isNullOrEmpty()) {
                    val account = GoogleSignIn.getLastSignedInAccount(requireContext())
                    if (account != null) {
                        try {
                            val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                            DriveServiceHelper.deleteFile(drive, driveId)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete from Drive: ${e.message}")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Document deleted", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFileOptionsMenu(file: DriveFileModel, anchorView: View) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_file_options, null)
        dialog.setContentView(sheetView)

        val tvSheetFileName = sheetView.findViewById<TextView>(R.id.tvSheetFileName)
        val tvSheetFileDetails = sheetView.findViewById<TextView>(R.id.tvSheetFileDetails)
        val ivSheetFileIcon = sheetView.findViewById<ImageView>(R.id.ivSheetFileIcon)

        tvSheetFileName.text = file.name
        val formattedSize = if (file.size > 0) DriveServiceHelper.formatFileSize(file.size) else ""
        val formattedDate = if (file.modifiedTime > 0) {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(file.modifiedTime))
        } else ""
        tvSheetFileDetails.text = if (formattedSize.isNotEmpty() && formattedDate.isNotEmpty()) {
            "$formattedSize • $formattedDate"
        } else formattedSize.ifEmpty { formattedDate }

        val ext = file.name.substringAfterLast('.', "").lowercase()
        when (ext) {
            "pdf" -> {
                ivSheetFileIcon.setImageResource(R.drawable.ic_pdf)
                ivSheetFileIcon.setColorFilter(Color.parseColor("#EF4444"))
            }
            "doc", "docx" -> {
                ivSheetFileIcon.setImageResource(R.drawable.ic_word)
                ivSheetFileIcon.setColorFilter(Color.parseColor("#2563EB"))
            }
            "txt" -> {
                ivSheetFileIcon.setImageResource(R.drawable.ic_text)
                ivSheetFileIcon.setColorFilter(Color.parseColor("#10B981"))
            }
            "jpg", "jpeg", "png" -> {
                ivSheetFileIcon.setImageResource(R.drawable.ic_scanned_files)
                ivSheetFileIcon.setColorFilter(Color.parseColor("#8B5CF6"))
            }
            else -> {
                ivSheetFileIcon.setImageResource(R.drawable.ic_documents_stack_24)
                ivSheetFileIcon.setColorFilter(Color.parseColor("#2563EB"))
            }
        }

        sheetView.findViewById<View>(R.id.actionSheetShare).setOnClickListener {
            dialog.dismiss()
            shareFile(file)
        }

        sheetView.findViewById<View>(R.id.actionSheetRename).setOnClickListener {
            dialog.dismiss()
            renameFile(file)
        }

        sheetView.findViewById<View>(R.id.actionSheetDelete).setOnClickListener {
            dialog.dismiss()
            deleteFile(file)
        }

        dialog.show()
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
        val dotIndex = file.name.lastIndexOf('.')
        if (dotIndex > 0) {
            editText.setSelection(0, dotIndex)
        } else {
            editText.selectAll()
        }

        MaterialAlertDialogBuilder(requireContext())
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
                val success = DriveServiceHelper.deleteFile(drive, file.id)
                if (success) {
                    db.recentFileDao().deleteByDriveId(file.id)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "File deleted", Toast.LENGTH_SHORT).show()
                        loadDriveFiles()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to delete file from Drive", Toast.LENGTH_SHORT).show()
                    }
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
        view.findViewById<View>(R.id.iv_scan)?.setOnClickListener { launchDocumentScanner() }
        view.findViewById<View>(R.id.cardScanHero)?.setOnClickListener { launchDocumentScanner() }
        view.findViewById<View>(R.id.btnViewAllRecent)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FilesFragment())
                .addToBackStack("files")
                .commit()
        }
        profileImage.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingFragment())
                .addToBackStack("Setting")
                .commit()
        }

        view.findViewById<View>(R.id.cardStorage)?.setOnClickListener {
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
                    val totalSize = files.sumOf { it.size }
                    withContext(Dispatchers.Main) {
                        driveAdapter.updateFiles(files.take(4))
                        tvTotalFiles.text = files.size.toString()
                        tvTotalSize.text = DriveServiceHelper.formatFileSize(totalSize)
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

    private fun formatFileSize(size: Long): String = DriveServiceHelper.formatFileSize(size)

    private fun downloadFileFromDrive(fileId: String, fileName: String) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                val mimeType = if (fileName.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg"
                val uri = DriveServiceHelper.downloadFileToPublicDestination(
                    requireContext(),
                    drive,
                    fileId,
                    fileName,
                    mimeType
                )
                withContext(Dispatchers.Main) {
                    if (uri != null) {
                        Toast.makeText(requireContext(), "Document downloaded successfully.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupOptionButtons(view: View) {
        val openUpload = { startActivity(Intent(requireContext(), TypoProcessingActivity::class.java)) }
        val openTranslate = { startActivity(Intent(requireContext(), TranslationActivity::class.java)) }
        val openFiles = {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FilesFragment())
                .addToBackStack("files")
                .commit()
        }

        view.findViewById<View>(R.id.cardUpload)?.setOnClickListener { openUpload() }
        view.findViewById<View>(R.id.tv_select_upload)?.setOnClickListener { openUpload() }

        view.findViewById<View>(R.id.cardTranslate)?.setOnClickListener { openTranslate() }
        view.findViewById<View>(R.id.tv_start_translate)?.setOnClickListener { openTranslate() }

        view.findViewById<View>(R.id.cardFiles)?.setOnClickListener { openFiles() }
        view.findViewById<View>(R.id.tv_view_files)?.setOnClickListener { openFiles() }
    }

    private fun setupScanner() {
        scanner = GmsDocumentScanning.getClient(GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build())
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
        val defaultName = "Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
        input.setText(defaultName)
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveDocumentLocalFirst(pdfUri, thumbnailUri, name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveDocumentLocalFirst(pdfUri: Uri, thumbnailUri: Uri, name: String) {
        val userId = getCurrentUserId()
        val formattedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        val fileName = "$name.pdf"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Save local copy in internal storage (app-private documents dir)
                val scansDir = File(requireContext().filesDir, "scanned_docs").apply { if (!exists()) mkdirs() }
                val localFile = File(scansDir, fileName)
                requireContext().contentResolver.openInputStream(pdfUri)?.use { input ->
                    java.io.FileOutputStream(localFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // 2. Save thumbnail locally if present
                var localThumbPath = ""
                try {
                    val thumbDir = File(requireContext().filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
                    val thumbFile = File(thumbDir, "${name}_thumb.jpg")
                    requireContext().contentResolver.openInputStream(thumbnailUri)?.use { input ->
                        java.io.FileOutputStream(thumbFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    localThumbPath = thumbFile.absolutePath
                } catch (ignored: Exception) {}

                // 3. Create Room record with uploadStatus = "PENDING"
                val recentFile = RecentFile(
                    userId = userId,
                    name = fileName,
                    filePath = localFile.absolutePath,
                    thumbnailUri = localThumbPath,
                    date = formattedDate,
                    mimeType = "application/pdf",
                    uploadStatus = "PENDING",
                    isSynced = false,
                    size = localFile.length(),
                    createdAt = System.currentTimeMillis()
                )
                val newId = db.recentFileDao().insert(recentFile).toInt()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Document saved locally", Toast.LENGTH_SHORT).show()
                }

                // 4. Trigger upload to Google Drive if authorized
                val account = GoogleSignIn.getLastSignedInAccount(requireContext())
                if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
                    uploadLocalFileToDrive(account, localFile, newId, fileName)
                } else {
                    withContext(Dispatchers.Main) {
                        pendingLocalFile = localFile
                        pendingFileRecordId = newId
                        pendingDriveFileName = fileName
                        googleDriveSignInLauncher.launch(driveSignInClient.signInIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving document locally", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = requireView().findViewById<RecyclerView>(R.id.recentFilesRecycler).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.adapter
        }
        val currentUserId = getCurrentUserId()
        db.recentFileDao().getFilesByUser(currentUserId).observe(viewLifecycleOwner) { files ->
            adapter.updateFiles(files ?: emptyList())
        }
    }

    private fun fetchUserDetails() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
        val name = googleAccount?.displayName ?: prefs.getString("full_name", "User")
        val avatar = googleAccount?.photoUrl?.toString() ?: prefs.getString("avatar_url", null)

        tvUsername.text = name
        if (!avatar.isNullOrEmpty()) {
            Glide.with(this).load(avatar).placeholder(R.drawable.ic_profile).into(profileImage)
        }
    }

    fun launchDocumentScanner() {
        if (!isAdded || isDetached) return
        val act = activity ?: return
        try {
            val scannerClient = scanner ?: GmsDocumentScanning.getClient(
                GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                    )
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
            ).also { scanner = it }

            scannerClient.getStartScanIntent(act)
                .addOnSuccessListener { intentSender ->
                    if (!isAdded || isDetached) return@addOnSuccessListener
                    try {
                        scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch document scanner", e)
                        if (isAdded) {
                            Toast.makeText(context, "Could not open scanner: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "GmsDocumentScanning failure", e)
                    if (isAdded) {
                        Toast.makeText(context, "Scanner unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (t: Throwable) {
            Log.e(TAG, "Error starting scanner", t)
            if (isAdded) {
                Toast.makeText(context, "Scanner error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
