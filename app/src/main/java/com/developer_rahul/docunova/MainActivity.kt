package com.developer_rahul.docunova

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.developer_rahul.docunova.Fragments.Files.FilesFragment
import com.developer_rahul.docunova.Fragments.Home.HomeFragment
import com.developer_rahul.docunova.Fragments.Setting.SettingFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize BottomNavigationView
        bottomNavigationView = findViewById(R.id.bottomNavigationView)

        // Load the default fragment (Home)
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

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
                R.id.nav_settings -> {
                    loadFragment(SettingFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
