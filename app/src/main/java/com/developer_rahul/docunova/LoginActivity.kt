package com.developer_rahul.docunova

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private val TAG = "LoginActivity"

    private lateinit var continueWithGoogle: LinearLayout
    private lateinit var tvGoogleBtnText: TextView
    private lateinit var pbGoogleLogin: ProgressBar
    private lateinit var ivGoogleIcon: ImageView

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            handleGoogleSignIn(account)
        } catch (e: ApiException) {
            setLoading(false)
            if (e.statusCode == 12501) {
                // User cancelled the dialog, no error message needed
                Log.d(TAG, "Google Sign-In cancelled by user")
            } else {
                Log.e(TAG, "Google Sign-In failed with status code: ${e.statusCode}", e)
                val hint = when (e.statusCode) {
                    10 -> "Google Cloud OAuth client ID must be registered for package 'com.developer_rahul.docunova_scanner'."
                    7 -> "Google Play Services was unable to establish a secure connection."
                    else -> "Sign-in returned code ${e.statusCode}."
                }
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Google Sign-In Notice")
                    .setMessage("$hint\n\nYou can continue in Offline Mode to use all camera scanning, OCR, translation, and PDF tools immediately!")
                    .setPositiveButton("Continue in Offline Mode") { _, _ ->
                        proceedAsGuest()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is logged in with Google or Guest
        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val loginMethod = prefs.getString("login_method", null)
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val forceShowLogin = intent.getBooleanExtra("force_show_login", false)
        if (!forceShowLogin && ((loginMethod == "google" && account != null) || loginMethod == "guest")) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        continueWithGoogle = findViewById(R.id.btn_continueWithGoogle)
        tvGoogleBtnText = findViewById(R.id.tvGoogleBtnText)
        pbGoogleLogin = findViewById(R.id.pbGoogleLogin)
        ivGoogleIcon = findViewById(R.id.ivGoogleIcon)

        findViewById<View>(R.id.btn_continueAsGuest)?.setOnClickListener {
            proceedAsGuest()
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        continueWithGoogle.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        setLoading(true)
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        continueWithGoogle.isEnabled = !isLoading
        continueWithGoogle.alpha = if (isLoading) 0.7f else 1.0f
        pbGoogleLogin.visibility = if (isLoading) View.VISIBLE else View.GONE
        ivGoogleIcon.visibility = if (isLoading) View.GONE else View.VISIBLE
        tvGoogleBtnText.text = if (isLoading) "Connecting to Google..." else "Continue with Google"
    }

    private fun handleGoogleSignIn(account: GoogleSignInAccount?) {
        if (account != null) {
            val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            prefs.edit()
                .putString("full_name", account.displayName ?: "Google User")
                .putString("email", account.email)
                .putString("avatar_url", account.photoUrl?.toString() ?: "")
                .putString("google_full_name", account.displayName ?: "Google User")
                .putString("google_avatar_url", account.photoUrl?.toString() ?: "")
                .putString("login_method", "google")
                .apply()

            Toast.makeText(this, "Welcome, ${account.displayName ?: "User"}!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            setLoading(false)
        }
    }

    private fun proceedAsGuest() {
        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        prefs.edit()
            .putString("full_name", "Local User")
            .putString("login_method", "guest")
            .apply()
        Toast.makeText(this, "Continuing in Offline Mode", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
