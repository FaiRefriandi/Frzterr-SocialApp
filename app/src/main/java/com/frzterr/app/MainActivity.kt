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

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val authRepository = AuthRepository()
    private var isCheckingSession = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash screen until session check is done
        splashScreen.setKeepOnScreenCondition { isCheckingSession }

        lifecycleScope.launch {
            // Check session asynchronously (suspends until loaded from disk)
            val hasSession = authRepository.loadSession()

            if (!hasSession) {
                navigateToAuth()
                isCheckingSession = false
                return@launch
            }

            // Session valid! Load profile data locally
            // 🔥 LOAD PROFILE LOKAL DI SINI
            val (name, avatar, username) = ProfileLocalStore.load(this@MainActivity)

            if (name != null || avatar != null) {
                val vm: ProfileViewModel by viewModels()
                vm.updateUser(
                    AppUser(
                        id = "local",
                        fullName = name,
                        email = null,
                        avatarUrl = avatar,
                        provider = null,
                        username = username ?: "",
                        usernameLower = username?.lowercase() ?: ""
                    )
                )
            }

            // Release splash screen
            isCheckingSession = false
        }

        // ⬇️ BARU SET CONTENT VIEW
        // Note: Content might appear before splash screen is dismissed if isCheckingSession becomes false slightly later,
        // but 'setKeepOnScreenCondition' handles the visual.
        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setNavigationBarContrastEnforced(false)
        window.setStatusBarContrastEnforced(false)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        // bottomNav.setupWithNavController(navController)
        // 🔥 CUSTOM NAVIGATION SETUP TO DISABLE ANIMATIONS (FIX SHADOW GLITCH)
        bottomNav.setOnItemSelectedListener { item ->
            val builder = androidx.navigation.NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setEnterAnim(R.anim.slide_in_right)
                .setExitAnim(R.anim.slide_out_left)
                .setPopEnterAnim(R.anim.slide_in_left)
                .setPopExitAnim(R.anim.slide_out_right)

            // Pop up to the start destination of the graph to
            // avoid building up a large stack of destinations
            // on the back stack as users select items
            builder.setPopUpTo(
                navController.graph.startDestinationId,
                inclusive = false,
                saveState = true
            )

            val options = builder.build()

            try {
                navController.navigate(item.itemId, null, options)
                true
            } catch (e: Exception) {
                false
            }
        }

        bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.homeFragment) {
                 val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                 val homeFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull { 
                     it is com.frzterr.app.ui.home.HomeFragment 
                 } as? com.frzterr.app.ui.home.HomeFragment
                 
                 homeFragment?.scrollToTopAndRefresh()
            }
        }

        // Keep BottomNav selected item in sync with NavController (e.g. on Back press)
        navController.addOnDestinationChangedListener { _, destination, _ ->
             if (destination.id == R.id.publicProfileFragment || destination.id == R.id.imageViewerFragment) {
                 bottomNav.visibility = android.view.View.GONE
             } else {
                 bottomNav.visibility = android.view.View.VISIBLE
                 bottomNav.menu.findItem(destination.id)?.isChecked = true
             }
        }
    }

    private fun navigateToAuth() {
        val intent = Intent(this, com.frzterr.app.ui.auth.AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
