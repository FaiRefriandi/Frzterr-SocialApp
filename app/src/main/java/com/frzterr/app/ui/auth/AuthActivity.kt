package com.frzterr.app.ui.auth

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.frzterr.app.MainActivity
import com.frzterr.app.data.remote.supabase.SupabaseManager
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.user.UserRepository
import com.frzterr.app.databinding.ActivityAuthBinding
import com.frzterr.app.ui.auth.AuthResetEmailActivity
import com.frzterr.app.utils.GoogleSignInHelper
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private lateinit var googleHelper: GoogleSignInHelper

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        SupabaseManager.initialize(applicationContext)

        if (authRepository.isLoggedIn()) {
            navigateToHome()
            return
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        googleHelper = GoogleSignInHelper(this)

        setupClicks()
        switchToLogin()

        binding.root.post { updateStatusBarIconColor() }
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

    // ============================================================================
    // BUTTON HANDLERS
    // ============================================================================

    private fun setupClicks() = with(binding) {

        btnLogin.setOnClickListener {
            val email = edtLoginEmail.text.toString().trim()
            val password = edtLoginPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                showToast("Email dan password tidak boleh kosong")
                return@setOnClickListener
            }

            doEmailLogin(email, password)
        }

        btnSignup.setOnClickListener {
            val name = edtSignupName.text.toString().trim()
            val email = edtSignupEmail.text.toString().trim()
            val password = edtSignupPassword.text.toString().trim()
            val confirm = edtSignupConfirmPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
                showToast("Semua field harus diisi")
                return@setOnClickListener
            }

            if (password != confirm) {
                showToast("Password tidak cocok")
                return@setOnClickListener
            }

            doEmailSignUp(name, email, password)
        }

        btnGoogleLogin.setOnClickListener {
            loginWithGoogle()
        }

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this@AuthActivity, AuthResetEmailActivity::class.java))
        }
    }

    // ============================================================================
    // GOOGLE LOGIN
    // ============================================================================

    private fun loginWithGoogle() {
        lifecycleScope.launch {
            Log.e("GOOGLE_FLOW", "Launching Google Credential Manager...")

            setLoading(true)

            val idToken = googleHelper.getGoogleIdToken()

            if (idToken == null) {
                Log.e("GOOGLE_FLOW", "ERROR: idToken null")
                setLoading(false)
                showToast("Google Sign-In gagal")
                return@launch
            }

            Log.e("GOOGLE_FLOW", "Google ID Token OK, authenticating with Supabase...")

            doGoogleSignIn(idToken)
        }
    }

    // ============================================================================
    // AUTH LOGIC
    // ============================================================================

    private fun doEmailLogin(email: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                authRepository.signInWithEmail(email, password)
                navigateToHome()
            } catch (e: Exception) {
                showToast("Login gagal: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun doEmailSignUp(name: String, email: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val user = authRepository.signUpWithEmail(email, password)

                if (user != null) {
                    authRepository.updateDisplayName(name)

                    userRepository.createOrUpdateUser(
                        id = user.id,
                        name = name,
                        email = email,
                        avatarUrl = null,
                        provider = "email"
                    )
                }

                showToast("Registrasi berhasil!")
                switchToLogin()
            } catch (e: Exception) {
                showToast("Registrasi gagal: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun doGoogleSignIn(idToken: String) {
        lifecycleScope.launch {
            try {
                val session = authRepository.signInWithGoogleIdToken(idToken)
                val supaUser = authRepository.getCurrentUser()

                if (session == null || supaUser == null) {
                    showToast("Google Sign-In gagal (session/user null)")
                    return@launch
                }

                val fullName = supaUser.userMetadata?.jsonObject?.get("full_name")
                    ?.jsonPrimitive?.content

                val avatarUrl = supaUser.userMetadata?.jsonObject?.get("avatar_url")
                    ?.jsonPrimitive?.content

                userRepository.createOrUpdateUser(
                    id = supaUser.id,
                    name = fullName,
                    email = supaUser.email,
                    avatarUrl = avatarUrl,
                    provider = "google"
                )

                navigateToHome()

            } catch (e: Exception) {
                Log.e("SUPABASE_AUTH", "Google login failed: ${e.message}", e)
                showToast("Google Sign-In gagal: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    // ============================================================================
    // MODE SWITCH
    // ============================================================================

    private fun switchToLogin() {
        isLoginMode = true

        with(binding) {
            layoutLoginForm.visibility = View.VISIBLE
            layoutSignupForm.visibility = View.GONE
            tvGotoSignup.visibility = View.VISIBLE
            tvGotoLogin.visibility = View.GONE
            tvTitle.text = "Masuk ke Akun Anda"
            tvSubtitle.text = "Lengkapi data anda untuk masuk"

            makeClickableBoldText(
                tvGotoSignup,
                "Belum punya akun? Daftar",
                "Daftar"
            ) { switchToSignUp() }
        }
    }

    private fun switchToSignUp() {
        isLoginMode = false

        with(binding) {
            layoutLoginForm.visibility = View.GONE
            layoutSignupForm.visibility = View.VISIBLE
            tvGotoSignup.visibility = View.GONE
            tvGotoLogin.visibility = View.VISIBLE
            tvTitle.text = "Buat Akun Baru"
            tvSubtitle.text = "Isi kolom dibawah ini untuk mendaftar"

            makeClickableBoldText(
                tvGotoLogin,
                "Sudah punya akun? Masuk",
                "Masuk"
            ) { switchToLogin() }
        }
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private fun setLoading(loading: Boolean) = with(binding) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        root.isEnabled = !loading
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun showToast(msg: String?) {
        Toast.makeText(this, msg ?: "Terjadi kesalahan", Toast.LENGTH_SHORT).show()
    }

    private fun makeClickableBoldText(
        textView: TextView,
        fullText: String,
        boldText: String,
        onClick: () -> Unit
    ) {
        val spannable = SpannableString(fullText)
        val start = fullText.indexOf(boldText)
        if (start == -1) {
            textView.text = fullText
            return
        }

        val end = start + boldText.length

        spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val clickSpan = object : ClickableSpan() {
            override fun onClick(widget: View) = onClick()
            override fun updateDrawState(ds: TextPaint) {
                ds.isUnderlineText = false
                ds.color = Color.WHITE
            }
        }

        spannable.setSpan(clickSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
    }
}