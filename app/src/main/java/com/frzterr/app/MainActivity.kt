package com.frzterr.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.frzterr.app.data.local.ProfileLocalStore
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.user.AppUser
import com.frzterr.app.ui.profile.ProfileViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (!authRepository.isLoggedIn()) {
            navigateToAuth()
            return
        }

        // 🔥 LOAD PROFILE LOKAL DI SINI
        val (name, avatar, username) = ProfileLocalStore.load(this)

        if (name != null || avatar != null) {
            val vm: ProfileViewModel by viewModels()
            vm.cachedUser = AppUser(
                id = "local",
                fullName = name,
                email = null,
                avatarUrl = avatar,
                provider = null,
                username = username ?: "",
                usernameLower = username?.lowercase() ?: ""
            )
        }

        // ⬇️ BARU SET CONTENT VIEW
        setContentView(R.layout.fragment_home)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setNavigationBarContrastEnforced(false)
        window.setStatusBarContrastEnforced(false)

        setContentView(R.layout.activity_main)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)
    }

    private fun navigateToAuth() {
        val intent = Intent(this, com.frzterr.app.ui.auth.AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
