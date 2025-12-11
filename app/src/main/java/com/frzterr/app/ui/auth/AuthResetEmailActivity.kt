package com.frzterr.app.ui.auth

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.databinding.ActivityResetEmailBinding
import kotlinx.coroutines.launch

class AuthResetEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetEmailBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        binding = ActivityResetEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSendOtp.setOnClickListener {
            val email = binding.edtResetEmail.text.toString().trim()

            if (email.isEmpty()) {
                showToast("Email harus diisi")
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val success = authRepository.sendResetOtp(email)
                    if (success) {
                        val i = Intent(this@AuthResetEmailActivity, AuthVerifyOtpActivity::class.java)
                        i.putExtra("email", email)
                        startActivity(i)
                    } else {
                        showToast("Gagal mengirim OTP")
                    }
                } catch (e: Exception) {
                    showToast(e.message)
                    Log.e("RESET_OTP", "Error: ${e.message}", e)
                }
            }
        }
    }

    // ============================================================================
    // STATUS BAR
    // ============================================================================

    private fun updateStatusBarIconColor() {
        val bgColor = (binding.root.background as? ColorDrawable)?.color ?: Color.BLACK
        val isDark = isColorDark(bgColor)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }

    private fun showToast(msg: String?) {
        Toast.makeText(this, msg ?: "Terjadi kesalahan", Toast.LENGTH_SHORT).show()
    }
}
