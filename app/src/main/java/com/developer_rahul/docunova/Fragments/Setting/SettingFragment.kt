package com.developer_rahul.docunova.Fragments.Setting

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
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
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SettingFragment : Fragment() {

    companion object {
        private const val TAG = "SettingFragment"
        private const val SUPABASE_URL = "https://grtzvwunaxlcbhqncizo.supabase.co"
        private const val SUPABASE_API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdydHp2d3VuYXhsY2JocW5jaXpvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njg4ODU3NDEsImV4cCI6MjA4NDQ2MTc0MX0.-W7GdFb_r8hFIFXEFUIEXHhqb01e-nDjBN_ZZl75dQ0"
        private const val APP_URL = "https://docunova-smart-scanner.netlify.app/"
    }

    private lateinit var tvUsername: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvPrivacy: TextView
    private lateinit var tvSecurity: TextView
    private lateinit var tvSupport: TextView
    private lateinit var tvDriveFilesCount: TextView
    private lateinit var tvDriveStorage: TextView
    private lateinit var profileImage: ImageView
    private lateinit var btnLogOut: Button
    private lateinit var btnShare: Button
    private lateinit var btnRateApp: Button
    private lateinit var googleSignInClient: GoogleSignInClient

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

        // Fetch and display user details
        fetchUserDetails()

        // Load Google Drive information
        loadDriveInfo()

        btnLogOut.setOnClickListener {
            logout()
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
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Docunova Smart Scanner")
            val shareMessage = "Check out Docunova Smart Scanner to scan and manage your documents: $APP_URL"
            putExtra(Intent.EXTRA_TEXT, shareMessage)
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
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

                    val sizeFormatted = formatFileSize(totalSize)

                    withContext(Dispatchers.Main) {
                        tvDriveFilesCount.text = files.size.toString()
                        tvDriveStorage.text = sizeFormatted
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvDriveFilesCount.text = "Error"
                        tvDriveStorage.text = "Failed to load"
                    }
                }
            }
        } else {
            tvDriveFilesCount.text = "0"
            tvDriveStorage.text = "Drive not linked"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        val units = arrayOf("KB", "MB", "GB", "TB")
        var unitIndex = 0

        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }

        return String.format("%.1f %s", value, units[unitIndex])
    }

    private fun fetchUserDetails() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val loginMethod = prefs.getString("login_method", null)
        val accessToken = prefs.getString("access_token", null)

        // 1. Show cached info immediately
        val cachedEmail = prefs.getString("email", "No Email")
        val cachedName = prefs.getString("full_name", cachedEmail)
        tvUsername.text = cachedName
        tvUserEmail.text = cachedEmail

        val cachedAvatar = if (loginMethod == "google") {
            prefs.getString("google_avatar_url", null)
        } else {
            prefs.getString("avatar_url", null)
        }

        if (!cachedAvatar.isNullOrEmpty()) {
            Glide.with(requireContext())
                .load(cachedAvatar)
                .placeholder(R.drawable.ic_profile)
                .into(profileImage)
        }

        // 2. Fetch fresh data if login method is email
        if (loginMethod == "email" && !accessToken.isNullOrEmpty()) {
            val url = "$SUPABASE_URL/auth/v1/user"
            val request = object : JsonObjectRequest(
                Method.GET, url, null,
                { response -> handleSupabaseUserResponse(response) },
                { error -> Log.e(TAG, "Supabase profile fetch error", error) }
            ) {
                override fun getHeaders(): MutableMap<String, String> {
                    return hashMapOf(
                        "apikey" to SUPABASE_API_KEY,
                        "Authorization" to "Bearer $accessToken",
                        "Accept" to "application/json"
                    )
                }
            }
            Volley.newRequestQueue(requireContext()).add(request)
        }
    }

    private fun handleSupabaseUserResponse(response: JSONObject) {
        try {
            val email = response.optString("email", "No Email")
            val metadata = response.optJSONObject("user_metadata")
            val fullName = metadata?.optString("full_name") ?: email
            val avatarUrl = metadata?.optString("avatar_url") ?: ""

            tvUsername.text = fullName
            tvUserEmail.text = email

            if (avatarUrl.isNotEmpty()) {
                Glide.with(requireContext())
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_profile)
                    .into(profileImage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing user response", e)
        }
    }

    private fun logout() {
        val prefs = requireContext().getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val loginMethod = prefs.getString("login_method", null)

        prefs.edit().clear().apply()

        if (loginMethod == "google") {
            googleSignInClient.signOut().addOnCompleteListener {
                navigateToLogin()
            }
        } else {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}
