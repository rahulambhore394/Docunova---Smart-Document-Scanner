package com.developer_rahul.docunova

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logoCard = findViewById<View>(R.id.appLogoCard)
        val appName = findViewById<View>(R.id.appName)
        val appTagline = findViewById<View>(R.id.appTagline)
        val splashBadge = findViewById<View>(R.id.tvSplashBadge)

        // Initial animation states
        logoCard?.apply {
            scaleX = 0.8f
            scaleY = 0.8f
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(600)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        listOfNotNull(appName, appTagline, splashBadge).forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 24f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(200L + (index * 100L))
                .setDuration(500)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // Direct session routing: If user already logged in with Google or Guest, go straight to MainActivity
        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val loginMethod = prefs.getString("login_method", null)
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val targetActivity = if ((loginMethod == "google" && account != null) || loginMethod == "guest") {
            MainActivity::class.java
        } else {
            LoginActivity::class.java
        }

        val forceShowSplash = intent.getBooleanExtra("force_show_splash", false)
        if (!forceShowSplash) {
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, targetActivity)
                startActivity(intent)
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }, 1800)
        }
    }
}
