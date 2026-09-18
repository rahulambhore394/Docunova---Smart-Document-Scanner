package com.developer_rahul.docunova.Fragments.Setting

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.developer_rahul.docunova.DriveServiceHelper
import com.developer_rahul.docunova.HelpSupportActivity
import com.developer_rahul.docunova.LoginActivity
import com.developer_rahul.docunova.Privacy_Policy_Activity
import com.developer_rahul.docunova.R
import com.developer_rahul.docunova.SecurityActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingFragment : Fragment() {

    companion object {
        private const val APP_URL = "https://docunova-smart-scanner.netlify.app/"
        private const val PREFS_SETTINGS = "DocuNovaSettings"
        private const val PREF_AUTO_CROP = "pref_auto_crop"
        private const val PREF_HD_ENHANCE = "pref_hd_enhance"
    }

    private lateinit var tvUsername: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvPrivacy: View
    private lateinit var tvSecurity: View
    private lateinit var tvSupport: View
    private lateinit var tvDriveFilesCount: TextView
    private lateinit var tvDriveStorage: TextView
    private lateinit var profileImage: ImageView
    private lateinit var btnLogOut: MaterialButton
    private lateinit var btnShare: View
    private lateinit var btnRateApp: View
    private lateinit var switchAutoCrop: SwitchMaterial
    private lateinit var switchEnhance: SwitchMaterial
    private lateinit var rowClearCache: View
    private lateinit var tvCacheSize: TextView

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var driveSignInLauncher: androidx.activity.result.ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        driveSignInLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) {
            loadDriveInfo()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setting, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize GoogleSignInClient
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

        // Initialize views
        tvUsername = view.findViewById(R.id.userName)
        tvUserEmail = view.findViewById(R.id.emailTag)
        tvDriveFilesCount = view.findViewById(R.id.tv_file_count)
        tvDriveStorage = view.findViewById(R.id.tv_drive_size)
        profileImage = view.findViewById(R.id.profileImage)
        btnLogOut = view.findViewById(R.id.signOutBtn)
        tvPrivacy = view.findViewById(R.id.tv_privacy)
        tvSecurity = view.findViewById(R.id.tv_security)
        tvSupport = view.findViewById(R.id.tv_help)
        btnShare = view.findViewById(R.id.btnShare)
        btnRateApp = view.findViewById(R.id.btnRateApp)

        // Preferences & Storage views
        switchAutoCrop = view.findViewById(R.id.switchAutoCrop)
        switchEnhance = view.findViewById(R.id.switchEnhance)
        rowClearCache = view.findViewById(R.id.rowClearCache)
        tvCacheSize = view.findViewById(R.id.tvCacheSize)

        // Setup preferences
        setupPreferences()

        // Fetch and display user details
        fetchUserDetails()

        // Load Google Drive information
        loadDriveInfo()

        // Update cache size
        updateCacheSize()

        btnLogOut.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
            val loginMethod = prefs.getString("login_method", null)
            if (loginMethod == "guest") {
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    putExtra("force_show_login", true)
                }
                startActivity(intent)
            } else {
                showLogoutConfirmation()
            }
        }

        tvPrivacy.setOnClickListener {
            val i = Intent(requireContext(), Privacy_Policy_Activity::class.java)
            startActivity(i)
        }

        tvSecurity.setOnClickListener {
            val i = Intent(requireContext(), SecurityActivity::class.java)
            startActivity(i)
        }

        tvSupport.setOnClickListener {
            val i = Intent(requireContext(), HelpSupportActivity::class.java)
            startActivity(i)
        }

        btnShare.setOnClickListener {
            shareApp()
        }

        btnRateApp.setOnClickListener {
            openUrl(APP_URL)
        }

        rowClearCache.setOnClickListener {
            clearCache()
        }
    }

    private fun setupPreferences() {
        val prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        switchAutoCrop.isChecked = prefs.getBoolean(PREF_AUTO_CROP, true)
        switchEnhance.isChecked = prefs.getBoolean(PREF_HD_ENHANCE, true)

        switchAutoCrop.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_AUTO_CROP, isChecked).apply()
        }

        switchEnhance.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_HD_ENHANCE, isChecked).apply()
        }
    }

    private fun updateCacheSize() {
        CoroutineScope(Dispatchers.IO).launch {
            val size = calculateCacheSize()
            val formatted = DriveServiceHelper.formatFileSize(size)
            withContext(Dispatchers.Main) {
                tvCacheSize.text = formatted
            }
        }
    }

    private fun calculateCacheSize(): Long {
        return try {
            var total = 0L
            requireContext().cacheDir?.walkTopDown()?.forEach { file ->
                if (file.isFile) total += file.length()
            }
            total
        } catch (e: Exception) {
            0L
        }
    }

    private fun clearCache() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                requireContext().cacheDir?.deleteRecursively()
                requireContext().cacheDir?.mkdirs()
                withContext(Dispatchers.Main) {
                    tvCacheSize.text = "0 B"
                    Toast.makeText(requireContext(), "Temporary cache cleared successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to clear cache", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DocuNova - Smart Document Scanner")
            val shareMessage = "Check out DocuNova Smart Scanner to scan, translate, and securely back up documents: $APP_URL"
            putExtra(Intent.EXTRA_TEXT, shareMessage)
        }
        startActivity(Intent.createChooser(shareIntent, "Share DocuNova via"))
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDriveInfo() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val drive = DriveServiceHelper.buildService(requireContext(), account.email!!)
                    val files = DriveServiceHelper.listFilesFromAppFolder(drive)

                    var totalSize: Long = 0
                    for (file in files) {
                        totalSize += file.size
                    }

                    val sizeFormatted = DriveServiceHelper.formatFileSize(totalSize)

                    withContext(Dispatchers.Main) {
                        tvDriveFilesCount.text = files.size.toString()
                        tvDriveStorage.text = sizeFormatted
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvDriveFilesCount.text = "Error"
                        tvDriveStorage.text = "Tap to Reconnect"
                    }
                }
            }
        } else {
            tvDriveFilesCount.text = "0"
            tvDriveStorage.text = "Connect Drive"
        }

        tvDriveStorage.setOnClickListener {
            connectOrReconnectDrive()
        }
    }

    private fun connectOrReconnectDrive() {
        val driveGso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        val client = GoogleSignIn.getClient(requireContext(), driveGso)
        driveSignInLauncher.launch(client.signInIntent)
    }

    private fun fetchUserDetails() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val loginMethod = prefs.getString("login_method", null)
        val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
        val name = googleAccount?.displayName ?: prefs.getString("full_name", if (loginMethod == "guest") "Local User" else "User")
        val email = googleAccount?.email ?: prefs.getString("email", if (loginMethod == "guest") "Offline Mode (Local Storage)" else "No Email")
        val avatar = googleAccount?.photoUrl?.toString() ?: prefs.getString("avatar_url", null)

        tvUsername.text = name
        tvUserEmail.text = email

        if (loginMethod == "guest") {
            btnLogOut.text = "Sign in with Google"
            btnLogOut.setIconResource(R.drawable.ic_google)
            btnLogOut.iconTint = null
            btnLogOut.setTextColor(android.graphics.Color.parseColor("#2563EB"))
            btnLogOut.strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#BFDBFE"))
            btnLogOut.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#EFF6FF"))
        } else {
            btnLogOut.text = "Sign Out of Account"
            btnLogOut.setIconResource(R.drawable.ic_logout_24)
            btnLogOut.iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DC2626"))
            btnLogOut.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            btnLogOut.strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FCA5A5"))
            btnLogOut.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FEF2F2"))
        }

        if (!avatar.isNullOrEmpty()) {
            Glide.with(requireContext())
                .load(avatar)
                .placeholder(R.drawable.ic_profile)
                .into(profileImage)
        }
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out? Your scanned documents will remain safe in local and cloud storage.")
            .setPositiveButton("Sign Out") { dialog, _ ->
                dialog.dismiss()
                logout()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun logout() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        DriveServiceHelper.clearDriveSession()

        googleSignInClient.signOut().addOnCompleteListener {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onResume() {
        super.onResume()
        loadDriveInfo()
        updateCacheSize()
    }
}
