package com.developer_rahul.docunova.Fragments.Files

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.developer_rahul.docunova.DriveServiceHelper
import com.developer_rahul.docunova.Fragments.SharedViewModel
import com.developer_rahul.docunova.R
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: FileAdapter
    private lateinit var emptyStateView: View
    private lateinit var tvEmptyDescription: TextView
    private lateinit var btnEmptyScan: MaterialButton
    private lateinit var searchEditText: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var btnViewType: ImageButton
    private lateinit var btnSort: ImageButton
    private lateinit var shimmerFrameLayout: ShimmerFrameLayout
    private lateinit var tvVaultSubtitle: TextView

    // Filter Chips
    private lateinit var chipFilterAll: TextView
    private lateinit var chipFilterPdf: TextView
    private lateinit var chipFilterDocs: TextView
    private lateinit var chipFilterTxt: TextView
    private lateinit var chipFilterImages: TextView

    private var currentViewType = FileAdapter.VIEW_TYPE_LIST
    private var currentSortOption = SORT_DATE_NEWEST
    private var selectedCategory = CATEGORY_ALL
    private var filesList = mutableListOf<DriveFileModel>()
    private var filteredFilesList = mutableListOf<DriveFileModel>()
    private var searchQuery = ""

    companion object {
        private const val SORT_NAME_ASC = 0
        private const val SORT_NAME_DESC = 1
        private const val SORT_DATE_NEWEST = 2
        private const val SORT_DATE_OLDEST = 3
        private const val SORT_SIZE_LARGEST = 4
        private const val SORT_SIZE_SMALLEST = 5

        private const val CATEGORY_ALL = 0
        private const val CATEGORY_PDF = 1
        private const val CATEGORY_DOCS = 2
        private const val CATEGORY_TXT = 3
        private const val CATEGORY_IMAGES = 4
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_files, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerViewFiles)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshFiles)
        emptyStateView = view.findViewById(R.id.emptyStateView)
        tvEmptyDescription = view.findViewById(R.id.tvEmptyDescription)
        btnEmptyScan = view.findViewById(R.id.btnEmptyScan)
        searchEditText = view.findViewById(R.id.searchEditText)
        btnClearSearch = view.findViewById(R.id.btnClearSearch)
        btnViewType = view.findViewById(R.id.btnViewType)
        btnSort = view.findViewById(R.id.btnSort)
        shimmerFrameLayout = view.findViewById(R.id.shimmerFiles)
        tvVaultSubtitle = view.findViewById(R.id.tvVaultSubtitle)

        // Filter chips
        chipFilterAll = view.findViewById(R.id.chipFilterAll)
        chipFilterPdf = view.findViewById(R.id.chipFilterPdf)
        chipFilterDocs = view.findViewById(R.id.chipFilterDocs)
        chipFilterTxt = view.findViewById(R.id.chipFilterTxt)
        chipFilterImages = view.findViewById(R.id.chipFilterImages)

        // Setup swipe refresh colors
        swipeRefreshLayout.setColorSchemeResources(R.color.blue_500)

        // Setup RecyclerView
        setupRecyclerView()

        // Setup search & chips
        setupSearch()
        setupFilterChips()

        // Setup swipe refresh
        setupSwipeRefresh()

        // Setup FAB & empty scan button
        setupFAB()

        // Setup custom header buttons
        btnViewType.setOnClickListener { toggleViewType() }
        btnSort.setOnClickListener { showSortMenu() }

        // Set initial icon
        updateViewTypeIcons()

        // Load files initially
        loadDriveFiles()
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrEmpty()
                btnClearSearch.visibility = if (hasText) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                filterFiles()
            }
        })

        btnClearSearch.setOnClickListener {
            searchEditText.setText("")
        }
    }

    private fun setupFilterChips() {
        chipFilterAll.setOnClickListener { selectCategory(CATEGORY_ALL) }
        chipFilterPdf.setOnClickListener { selectCategory(CATEGORY_PDF) }
        chipFilterDocs.setOnClickListener { selectCategory(CATEGORY_DOCS) }
        chipFilterTxt.setOnClickListener { selectCategory(CATEGORY_TXT) }
        chipFilterImages.setOnClickListener { selectCategory(CATEGORY_IMAGES) }
    }

    private fun selectCategory(category: Int) {
        if (selectedCategory == category) return
        selectedCategory = category

        // Update chip visuals
        updateChipStyle(chipFilterAll, category == CATEGORY_ALL)
        updateChipStyle(chipFilterPdf, category == CATEGORY_PDF)
        updateChipStyle(chipFilterDocs, category == CATEGORY_DOCS)
        updateChipStyle(chipFilterTxt, category == CATEGORY_TXT)
        updateChipStyle(chipFilterImages, category == CATEGORY_IMAGES)

        filterFiles()
    }

    private fun updateChipStyle(chip: TextView, isSelected: Boolean) {
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_filter_chip_active)
            chip.setTextColor(Color.WHITE)
        } else {
            chip.setBackgroundResource(R.drawable.bg_filter_chip_inactive)
            chip.setTextColor(Color.parseColor("#64748B"))
        }
    }

    private fun updateChipCounts() {
        val allCount = filesList.size
        val pdfCount = filesList.count { it.name.endsWith(".pdf", ignoreCase = true) }
        val docCount = filesList.count { it.name.endsWith(".doc", ignoreCase = true) || it.name.endsWith(".docx", ignoreCase = true) }
        val txtCount = filesList.count { it.name.endsWith(".txt", ignoreCase = true) }
        val imgCount = filesList.count {
            it.name.endsWith(".jpg", ignoreCase = true) ||
            it.name.endsWith(".jpeg", ignoreCase = true) ||
            it.name.endsWith(".png", ignoreCase = true)
        }

        chipFilterAll.text = if (allCount > 0) "All ($allCount)" else "All Files"
        chipFilterPdf.text = if (pdfCount > 0) "PDFs ($pdfCount)" else "PDFs"
        chipFilterDocs.text = if (docCount > 0) "Word ($docCount)" else "Word (.docx)"
        chipFilterTxt.text = if (txtCount > 0) "Text ($txtCount)" else "Text (.txt)"
        chipFilterImages.text = if (imgCount > 0) "Images ($imgCount)" else "Images"
    }

    private fun filterFiles() {
        var list = if (searchQuery.isEmpty()) {
            filesList.toList()
        } else {
            filesList.filter { file ->
                file.name.contains(searchQuery, ignoreCase = true)
            }
        }

        // Apply category filter
        list = when (selectedCategory) {
            CATEGORY_PDF -> list.filter { it.name.endsWith(".pdf", ignoreCase = true) }
            CATEGORY_DOCS -> list.filter {
                it.name.endsWith(".doc", ignoreCase = true) ||
                        it.name.endsWith(".docx", ignoreCase = true)
            }
            CATEGORY_TXT -> list.filter {
                it.name.endsWith(".txt", ignoreCase = true)
            }
            CATEGORY_IMAGES -> list.filter {
                it.name.endsWith(".jpg", ignoreCase = true) ||
                        it.name.endsWith(".jpeg", ignoreCase = true) ||
                        it.name.endsWith(".png", ignoreCase = true)
            }
            else -> list
        }

        filteredFilesList.clear()
        filteredFilesList.addAll(list)
        sortFiles()
    }

    private fun setupFAB() {
        val sharedViewModel = ViewModelProvider(requireActivity()).get(SharedViewModel::class.java)
        val fabAdd = requireView().findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener {
            sharedViewModel.triggerScan()
        }

        btnEmptyScan.setOnClickListener {
            sharedViewModel.triggerScan()
        }
    }

    private fun showSortMenu() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_filter_sort, null)
        dialog.setContentView(sheetView)

        val rgSortOptions = sheetView.findViewById<RadioGroup>(R.id.rgSortOptions)
        val rbDateNewest = sheetView.findViewById<RadioButton>(R.id.rbSortDateNewest)
        val rbDateOldest = sheetView.findViewById<RadioButton>(R.id.rbSortDateOldest)
        val rbNameAsc = sheetView.findViewById<RadioButton>(R.id.rbSortNameAsc)
        val rbNameDesc = sheetView.findViewById<RadioButton>(R.id.rbSortNameDesc)
        val rbSizeLargest = sheetView.findViewById<RadioButton>(R.id.rbSortSizeLargest)
        val rbSizeSmallest = sheetView.findViewById<RadioButton>(R.id.rbSortSizeSmallest)

        when (currentSortOption) {
            SORT_DATE_NEWEST -> rbDateNewest.isChecked = true
            SORT_DATE_OLDEST -> rbDateOldest.isChecked = true
            SORT_NAME_ASC -> rbNameAsc.isChecked = true
            SORT_NAME_DESC -> rbNameDesc.isChecked = true
            SORT_SIZE_LARGEST -> rbSizeLargest.isChecked = true
            SORT_SIZE_SMALLEST -> rbSizeSmallest.isChecked = true
        }

        sheetView.findViewById<View>(R.id.btnResetFilters).setOnClickListener {
            currentSortOption = SORT_DATE_NEWEST
            rbDateNewest.isChecked = true
            selectCategory(CATEGORY_ALL)
            searchEditText.setText("")
            sortFiles()
            dialog.dismiss()
        }

        sheetView.findViewById<View>(R.id.btnApplySortFilter).setOnClickListener {
            currentSortOption = when (rgSortOptions.checkedRadioButtonId) {
                R.id.rbSortDateNewest -> SORT_DATE_NEWEST
                R.id.rbSortDateOldest -> SORT_DATE_OLDEST
                R.id.rbSortNameAsc -> SORT_NAME_ASC
                R.id.rbSortNameDesc -> SORT_NAME_DESC
                R.id.rbSortSizeLargest -> SORT_SIZE_LARGEST
                R.id.rbSortSizeSmallest -> SORT_SIZE_SMALLEST
                else -> SORT_DATE_NEWEST
            }
            sortFiles()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun toggleViewType() {
        currentViewType = if (currentViewType == FileAdapter.VIEW_TYPE_LIST) {
            FileAdapter.VIEW_TYPE_GRID
        } else {
            FileAdapter.VIEW_TYPE_LIST
        }
        updateViewType()
    }

    private fun updateViewType() {
        if (currentViewType == FileAdapter.VIEW_TYPE_LIST) {
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
        } else {
            recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        }
        updateViewTypeIcons()
        if (::adapter.isInitialized) {
            adapter.setViewType(currentViewType)
        }
    }

    private fun updateViewTypeIcons() {
        if (currentViewType == FileAdapter.VIEW_TYPE_LIST) {
            btnViewType.setImageResource(R.drawable.ic_grid_view)
        } else {
            btnViewType.setImageResource(R.drawable.ic_list_view)
        }
    }

    private fun setupRecyclerView() {
        adapter = FileAdapter(
            requireContext(),
            emptyList(),
            currentViewType,
            onDownloadClick = { file ->
                downloadFileFromDrive(file.id, file.name)
            },
            onFileClick = { file ->
                openDriveFile(file)
            },
            onMoreClick = { file, anchorView ->
                showFileOptionsMenu(file, anchorView)
            }
        )
        recyclerView.adapter = adapter

        if (currentViewType == FileAdapter.VIEW_TYPE_LIST) {
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
        } else {
            recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
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
            .setPositiveButton("Rename") { dialog, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != file.name) {
                    performRenameFile(file, newName)
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid name", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performRenameFile(file: DriveFileModel, newName: String) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = newName
                }
                drive.files().update(file.id, fileMetadata).execute()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "File renamed successfully", Toast.LENGTH_SHORT).show()
                    loadDriveFiles()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to rename file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteFile(file: DriveFileModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete '${file.name}'?")
            .setPositiveButton("Delete") { dialog, _ ->
                performDeleteFile(file)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performDeleteFile(file: DriveFileModel) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                val success = DriveServiceHelper.deleteFile(drive, file.id)

                if (success) {
                    com.developer_rahul.docunova.RoomDB.AppDatabase.getDatabase(requireContext())
                        .recentFileDao().deleteByDriveId(file.id)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "File deleted successfully", Toast.LENGTH_SHORT).show()
                        loadDriveFiles()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to delete file from Drive", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to delete file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadDriveFiles()
        }
    }

    private fun loadDriveFiles() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            
            // Show shimmer
            shimmerFrameLayout.visibility = View.VISIBLE
            shimmerFrameLayout.startShimmer()
            recyclerView.visibility = View.GONE
            emptyStateView.visibility = View.GONE

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                    val files = DriveServiceHelper.listFilesFromAppFolder(drive)

                    val driveFiles = files.map { file ->
                        DriveFileModel(
                            id = file.id,
                            name = file.name,
                            mimeType = file.mimeType,
                            size = file.size ?: 0L,
                            modifiedTime = file.modifiedTime,
                            thumbnailLink = file.thumbnailLink,
                            webContentLink = file.webContentLink,
                            createdTime = file.createdTime
                        )
                    }

                    filesList.clear()
                    filesList.addAll(driveFiles)
                    
                    withContext(Dispatchers.Main) {
                        tvVaultSubtitle.text = "${filesList.size} documents • Secured on Google Drive"
                        updateChipCounts()
                        filterFiles()
                        
                        // Hide shimmer
                        shimmerFrameLayout.stopShimmer()
                        shimmerFrameLayout.visibility = View.GONE
                        
                        updateEmptyState(filteredFilesList.isEmpty())
                        swipeRefreshLayout.isRefreshing = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to load files: ${e.message}", Toast.LENGTH_SHORT).show()
                        
                        shimmerFrameLayout.stopShimmer()
                        shimmerFrameLayout.visibility = View.GONE
                        
                        swipeRefreshLayout.isRefreshing = false
                        updateEmptyState(true)
                    }
                }
            }
        } else {
            tvVaultSubtitle.text = "Sign in with Google to view cloud files"
            updateEmptyState(true)
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun sortFiles() {
        lifecycleScope.launch(Dispatchers.Default) {
            val listToSort = filteredFilesList.toList()

            val sortedList = when (currentSortOption) {
                SORT_NAME_ASC -> listToSort.sortedBy { it.name.lowercase() }
                SORT_NAME_DESC -> listToSort.sortedByDescending { it.name.lowercase() }
                SORT_DATE_NEWEST -> listToSort.sortedByDescending { it.modifiedTime }
                SORT_DATE_OLDEST -> listToSort.sortedBy { it.modifiedTime }
                SORT_SIZE_LARGEST -> listToSort.sortedByDescending { it.size }
                SORT_SIZE_SMALLEST -> listToSort.sortedBy { it.size }
                else -> listToSort
            }

            withContext(Dispatchers.Main) {
                adapter.updateFiles(sortedList)
                updateEmptyState(sortedList.isEmpty())
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            emptyStateView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE

            if (searchQuery.isNotEmpty() || selectedCategory != CATEGORY_ALL) {
                tvEmptyDescription.text = "No documents match your active search query or format filter."
                btnEmptyScan.visibility = View.GONE
            } else {
                tvEmptyDescription.text = "Your scanned and uploaded documents on Google Drive will appear here."
                btnEmptyScan.visibility = View.VISIBLE
            }
        } else {
            emptyStateView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
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

    override fun onResume() {
        super.onResume()
        loadDriveFiles()
    }
}
