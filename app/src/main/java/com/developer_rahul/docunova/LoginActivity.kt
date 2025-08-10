package com.developer_rahul.docunova

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import org.json.JSONException
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private val SUPABASE_URL = "https://grtzvwunaxlcbhqncizo.supabase.co"
    private val SUPABASE_API_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdydHp2d3VuYXhsY2JocW5jaXpvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njg4ODU3NDEsImV4cCI6MjA4NDQ2MTc0MX0.-W7GdFb_r8hFIFXEFUIEXHhqb01e-nDjBN_ZZl75dQ0"

    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_GOOGLE_SIGN_IN = 1001
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", null)
        val loginMethod = prefs.getString("login_method", null)

        if (!accessToken.isNullOrEmpty() || (loginMethod == "google" && GoogleSignIn.getLastSignedInAccount(this) != null)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val emailEditText = findViewById<EditText>(R.id.etEmail)
        val passwordEditText = findViewById<EditText>(R.id.etPassword)
        val loginButton = findViewById<Button>(R.id.btnSignIn)
        val continueWithGoogle = findViewById<LinearLayout>(R.id.btn_continueWithGoogle)
        val createAccountText = findViewById<TextView>(R.id.createAccount)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        createAccountText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                performLogin(email, password)
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        continueWithGoogle.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun performLogin(email: String, password: String) {
        val url = "$SUPABASE_URL/auth/v1/token?grant_type=password"
        val jsonBody = JSONObject().apply {
            put("email", email)
            put("password", password)
        }

        val request = object : JsonObjectRequest(
            Method.POST, url, jsonBody,
            { response ->
                try {
                    val accessToken = response.getString("access_token")
                    val refreshToken = response.getString("refresh_token")
                    val userObj = response.getJSONObject("user")
                    val userId = userObj.getString("id")
                    val userMetadata = userObj.optJSONObject("user_metadata")

                    val fullName = userMetadata?.optString("full_name", email) ?: email
                    val avatarUrl = userMetadata?.optString("avatar_url", "")

                    val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("access_token", accessToken)
                        .putString("refresh_token", refreshToken)
                        .putString("user_id", userId)
                        .putString("email", email)
                        .putString("full_name", fullName)
                        .putString("avatar_url", avatarUrl)
                        .putString("login_method", "email")
                        .apply()

                    Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } catch (e: JSONException) {
                    Log.e(TAG, "JSON parse error", e)
                    Toast.makeText(this, "Invalid server response", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e(TAG, "Login error", error)
                val status = error.networkResponse?.statusCode
                if (status == 400) {
                    Toast.makeText(this, "Invalid credentials or unconfirmed email", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Login failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf(
                    "apikey" to SUPABASE_API_KEY,
                    "Content-Type" to "application/json"
                )
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun signInWithGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                handleGoogleSignIn(account)
            } catch (e: ApiException) {
                Log.e(TAG, "Google Sign-In failed: ${e.statusCode}")
                Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
            }
        }
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

            Toast.makeText(this, "Welcome ${account.displayName}", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
