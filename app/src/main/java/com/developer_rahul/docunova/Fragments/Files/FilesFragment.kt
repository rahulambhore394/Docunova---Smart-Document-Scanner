package com.developer_rahul.docunova.Fragments.Files

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
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
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FilesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: FileAdapter
    private lateinit var emptyStateView: View
    private lateinit var searchEditText: EditText
    private lateinit var btnViewType: ImageButton
    private lateinit var btnSort: ImageButton
    private lateinit var shimmerFrameLayout: ShimmerFrameLayout

    private var currentViewType = FileAdapter.VIEW_TYPE_LIST
    private var currentSortOption = SORT_DATE_NEWEST // Default to newest first
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
        searchEditText = view.findViewById(R.id.searchEditText)
        btnViewType = view.findViewById(R.id.btnViewType)
        btnSort = view.findViewById(R.id.btnSort)
        shimmerFrameLayout = view.findViewById(R.id.shimmerFiles)

        // Setup RecyclerView
        setupRecyclerView()

        // Setup search functionality
        setupSearch()

        // Setup swipe refresh
        setupSwipeRefresh()

        // Setup FAB click listener
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
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString().trim()
                filterFiles()
            }
        })
    }

    private fun filterFiles() {
        if (searchQuery.isEmpty()) {
            filteredFilesList.clear()
            filteredFilesList.addAll(filesList)
        } else {
            filteredFilesList.clear()
            filteredFilesList.addAll(filesList.filter { file ->
                file.name.contains(searchQuery, ignoreCase = true)
            })
        }
        sortFiles()
    }

    private fun setupFAB() {
        val sharedViewModel = ViewModelProvider(requireActivity()).get(SharedViewModel::class.java)
        val fabAdd = requireView().findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener {
            sharedViewModel.triggerScan()
        }
    }


    private fun showSortMenu() {
        val popup = PopupMenu(requireContext(), btnSort)
        popup.menuInflater.inflate(R.menu.menu_sort, popup.menu)

        val sortNameAsc = popup.menu.findItem(R.id.sort_name_asc)
        val sortNameDesc = popup.menu.findItem(R.id.sort_name_desc)
        val sortDateNewest = popup.menu.findItem(R.id.sort_date_newest)
        val sortDateOldest = popup.menu.findItem(R.id.sort_date_oldest)
        val sortSizeLargest = popup.menu.findItem(R.id.sort_size_largest)
        val sortSizeSmallest = popup.menu.findItem(R.id.sort_size_smallest)

        when (currentSortOption) {
            SORT_NAME_ASC -> sortNameAsc?.isChecked = true
            SORT_NAME_DESC -> sortNameDesc?.isChecked = true
            SORT_DATE_NEWEST -> sortDateNewest?.isChecked = true
            SORT_DATE_OLDEST -> sortDateOldest?.isChecked = true
            SORT_SIZE_LARGEST -> sortSizeLargest?.isChecked = true
            SORT_SIZE_SMALLEST -> sortSizeSmallest?.isChecked = true
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort_name_asc -> {
                    currentSortOption = SORT_NAME_ASC
                    sortFiles()
                    true
                }
                R.id.sort_name_desc -> {
                    currentSortOption = SORT_NAME_DESC
                    sortFiles()
                    true
                }
                R.id.sort_date_newest -> {
                    currentSortOption = SORT_DATE_NEWEST
                    sortFiles()
                    true
                }
                R.id.sort_date_oldest -> {
                    currentSortOption = SORT_DATE_OLDEST
                    sortFiles()
                    true
                }
                R.id.sort_size_largest -> {
                    currentSortOption = SORT_SIZE_LARGEST
                    sortFiles()
                    true
                }
                R.id.sort_size_smallest -> {
                    currentSortOption = SORT_SIZE_SMALLEST
                    sortFiles()
                    true
                }
                else -> false
            }
        }

        popup.setForceShowIcon(true)
        popup.show()
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
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_file_options, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_share -> {
                    shareFile(file)
                    true
                }
                R.id.action_rename -> {
                    renameFile(file)
                    true
                }
                R.id.action_delete -> {
                    deleteFile(file)
                    true
                }
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

                // Get file metadata
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = newName
                }

                // Update file name
                drive.files().update(file.id, fileMetadata).execute()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "File renamed successfully", Toast.LENGTH_SHORT).show()
                    loadDriveFiles() // Refresh the list
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

                // Delete file from Drive
                drive.files().delete(file.id).execute()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "File deleted successfully", Toast.LENGTH_SHORT).show()
                    loadDriveFiles() // Refresh the list
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
                        filterFiles() // Apply current search filter
                        
                        // Hide shimmer
                        shimmerFrameLayout.stopShimmer()
                        shimmerFrameLayout.visibility = View.GONE
                        
                        updateEmptyState(filteredFilesList.isEmpty())
                        swipeRefreshLayout.isRefreshing = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to load files: ${e.message}", Toast.LENGTH_SHORT).show()
                        
                        // Hide shimmer
                        shimmerFrameLayout.stopShimmer()
                        shimmerFrameLayout.visibility = View.GONE
                        
                        swipeRefreshLayout.isRefreshing = false
                        updateEmptyState(true)
                    }
                }
            }
        } else {
            updateEmptyState(true)
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun sortFiles() {
        // Perform sorting on background thread but update UI on main thread
        lifecycleScope.launch(Dispatchers.Default) {
            val listToSort = if (filteredFilesList.isNotEmpty()) filteredFilesList else filesList

            val sortedList = when (currentSortOption) {
                SORT_NAME_ASC -> listToSort.sortedBy { it.name.lowercase() }
                SORT_NAME_DESC -> listToSort.sortedByDescending { it.name.lowercase() }
                SORT_DATE_NEWEST -> listToSort.sortedByDescending { it.modifiedTime }
                SORT_DATE_OLDEST -> listToSort.sortedBy { it.modifiedTime }
                SORT_SIZE_LARGEST -> listToSort.sortedByDescending { it.size }
                SORT_SIZE_SMALLEST -> listToSort.sortedBy { it.size }
                else -> listToSort
            }

            // MOVE UI UPDATE TO MAIN THREAD
            withContext(Dispatchers.Main) {
                adapter.updateFiles(sortedList)
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            emptyStateView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
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
            GoogleSignIn.getClient(requireContext(), GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .build())
                .silentSignIn()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        openDriveFile(file)
                    } else {
                        Toast.makeText(requireContext(), "Please sign in to access Drive", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun openFileInBrowser(file: DriveFileModel) {
        val url = "https://drive.google.com/file/d/${file.id}/view"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
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

    private fun downloadFileFromDrive(fileId: String, fileName: String) {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appFolder = File(downloadsDir, "Docunova")
                if (!appFolder.exists()) appFolder.mkdirs()

                val outputFile = File(appFolder, fileName)
                DriveServiceHelper.downloadFile(drive, fileId, outputFile)

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Downloaded to ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()
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
