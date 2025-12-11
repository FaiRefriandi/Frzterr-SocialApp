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
import com.frzterr.app.databinding.ActivityVerifyOtpBinding
import kotlinx.coroutines.launch

class AuthVerifyOtpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyOtpBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        binding = ActivityVerifyOtpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val email = intent.getStringExtra("email") ?: ""

        binding.btnConfirm.setOnClickListener {
            val otp = binding.edtOtp.text.toString().trim()
            val newPassword = binding.edtNewPassword.text.toString().trim()
            val confirm = binding.edtConfirmPassword.text.toString().trim()

            if (otp.isEmpty() || newPassword.isEmpty() || confirm.isEmpty()) {
                showToast("Semua field wajib diisi")
                return@setOnClickListener
            }

            if (newPassword != confirm) {
                showToast("Password tidak cocok")
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val success = authRepository.verifyResetOtp(email, otp, newPassword)
                    if (success) {
                        showToast("Password berhasil diubah")

                        val intent = Intent(this@AuthVerifyOtpActivity, AuthActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                } else {
                        showToast("OTP salah atau kadaluarsa")
                    }
                } catch (e: Exception) {
                    showToast(e.message)
                    Log.e("RESET_VERIFY", "Error: ${e.message}", e)
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
