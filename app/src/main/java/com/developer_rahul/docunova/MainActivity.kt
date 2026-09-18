package com.developer_rahul.docunova

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.developer_rahul.docunova.Fragments.Files.FilesFragment
import com.developer_rahul.docunova.Fragments.Home.HomeFragment
import com.developer_rahul.docunova.Fragments.Home.TranslationActivity
import com.developer_rahul.docunova.Fragments.Setting.SettingFragment
import com.developer_rahul.docunova.Fragments.SharedViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var fabQuickScan: View
    private lateinit var sharedViewModel: SharedViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedViewModel = ViewModelProvider(this).get(SharedViewModel::class.java)

        // Initialize views
        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        fabQuickScan = findViewById(R.id.fab_quick_scan)

        // Load default fragment (Home)
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        handleNavigationIntent(intent)

        // Handle Bottom Navigation item clicks
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_files -> {
                    loadFragment(FilesFragment())
                    true
                }
                R.id.nav_translate -> {
                    startActivity(Intent(this, TranslationActivity::class.java))
                    false
                }
                R.id.nav_settings -> {
                    loadFragment(SettingFragment())
                    true
                }
                else -> false
            }
        }

        // Handle Center Quick-Scan Action with debouncing & state check
        var lastScanClickTime = 0L
        fabQuickScan.setOnClickListener {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastScanClickTime < 1000L) return@setOnClickListener
            lastScanClickTime = now

            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is HomeFragment && currentFragment.isResumed) {
                currentFragment.launchDocumentScanner()
            } else {
                loadFragment(HomeFragment())
                bottomNavigationView.selectedItemId = R.id.nav_home
                sharedViewModel.triggerScan()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("NAVIGATE_TO_HOME", false) == true) {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            loadFragment(HomeFragment())
            bottomNavigationView.selectedItemId = R.id.nav_home
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
