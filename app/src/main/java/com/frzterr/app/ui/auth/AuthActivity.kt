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
import androidx.core.content.ContextCompat
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

    private val COLOR_RED = Color.parseColor("#EF4444")
    private val COLOR_ORANGE = Color.parseColor("#F97316")
    private val COLOR_GREEN = Color.parseColor("#22C55E")
    private var usernameTouched = false

    private lateinit var binding: ActivityAuthBinding
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private lateinit var googleHelper: GoogleSignInHelper

    private var usernameCheckJob: kotlinx.coroutines.Job? = null

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
        setupUsernameAvailabilityCheck()
        switchToLogin()

        binding.root.post { updateStatusBarIconColor() }
    }

    private fun showUsernameEmpty() = with(binding) {
        tvUsernameStatus.visibility = View.VISIBLE
        tvUsernameStatus.text = "Pilih nama pengguna untuk melanjutkan"
        tvUsernameStatus.setTextColor(COLOR_RED)
        setUsernameStroke(COLOR_RED)
    }

    private fun showUsernameTooShort() = with(binding) {
        tvUsernameStatus.visibility = View.VISIBLE
        tvUsernameStatus.text = "Nama pengguna tidak tersedia"
        tvUsernameStatus.setTextColor(COLOR_RED)
        setUsernameStroke(COLOR_RED)
    }

    private fun showUsernameTaken() = with(binding) {
        tvUsernameStatus.visibility = View.VISIBLE
        tvUsernameStatus.text = "Nama pengguna telah digunakan"
        tvUsernameStatus.setTextColor(COLOR_ORANGE)
        setUsernameStroke(COLOR_ORANGE)
    }

    private fun showUsernameAvailable() = with(binding) {
        tvUsernameStatus.visibility = View.VISIBLE
        tvUsernameStatus.text = "Nama pengguna tersedia"
        tvUsernameStatus.setTextColor(COLOR_GREEN)
        setUsernameStroke(COLOR_GREEN)
    }
    private fun setUsernameStroke(color: Int) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(-android.R.attr.state_focused)
        )

        val colors = intArrayOf(
            color, // focused
            color  // unfocused
        )

        binding.tilSignupUsername.setBoxStrokeColorStateList(
            android.content.res.ColorStateList(states, colors)
        )
    }

    private fun isValidEmail(email: String): Boolean {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return false
        }

        // Pastikan domain punya TLD (contoh: gmail.com)
        val parts = email.substringAfter("@", "")
        if (!parts.contains(".")) return false

        val tld = parts.substringAfterLast(".", "")
        if (tld.length < 2) return false

        return true
    }


    // =========================================================================
    // USERNAME CHECKER
    // =========================================================================

    private fun setupUsernameAvailabilityCheck() = with(binding) {

        edtSignupUsername.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                usernameTouched = true

                if (edtSignupUsername.text.isNullOrBlank()) {
                    showUsernameEmpty()
                }
            }
        }

        edtSignupUsername.addTextChangedListener(object : android.text.TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!usernameTouched) return

                usernameCheckJob?.cancel()

                val username = s.toString().trim().lowercase()

                // KOSONG
                if (username.isEmpty()) {
                    showUsernameEmpty()
                    return
                }

                // TERLALU PENDEK / INVALID
                if (username.length < 4 ||
                    !Regex("^[a-z][a-z0-9_]{3,19}$").matches(username)
                ) {
                    showUsernameTooShort()
                    return
                }

                // DEBOUNCE DB CHECK
                usernameCheckJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(400)

                    val available = userRepository.isUsernameAvailable(username)

                    if (available) {
                        showUsernameAvailable()
                    } else {
                        showUsernameTaken()
                    }
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    // =========================================================================
    // STATUS BAR
    // =========================================================================

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

    // =========================================================================
    // CLICK HANDLERS
    // =========================================================================

    private fun setupClicks() = with(binding) {

        btnLogin.setOnClickListener {
            val email = edtLoginEmail.text.toString().trim()
            val password = edtLoginPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                showToast("Email dan password tidak boleh kosong")
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                showToast("Masukkan email yang valid")
                return@setOnClickListener
            }

            doEmailLogin(email, password)
        }


        btnSignup.setOnClickListener {
            val name = edtSignupName.text.toString().trim()
            val username = edtSignupUsername.text.toString().trim()
            val email = edtSignupEmail.text.toString().trim()
            val password = edtSignupPassword.text.toString().trim()

            if (name.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                showToast("Semua kolom harus diisi")
                return@setOnClickListener
            }

            if (!isValidUsername(username)) {
                showToast("Nama pengguna tidak valid")
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                showToast("Masukkan email yang valid")
                return@setOnClickListener
            }

            doEmailSignUp(name, username, email, password)
        }

        btnGoogleLogin.setOnClickListener {
            loginWithGoogle()
        }

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this@AuthActivity, AuthResetEmailActivity::class.java))
        }
    }

    // =========================================================================
    // GOOGLE LOGIN
    // =========================================================================

    private fun loginWithGoogle() {
        lifecycleScope.launch {
            Log.e("GOOGLE_FLOW", "Launching Google Credential Manager...")
            setLoading(true)

            val idToken = googleHelper.getGoogleIdToken()

            if (idToken == null) {
                setLoading(false)
                showToast("Google Sign-In gagal")
                return@launch
            }

            doGoogleSignIn(idToken)
        }
    }

    // =========================================================================
    // AUTH LOGIC
    // =========================================================================

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

    private fun doEmailSignUp(
        name: String,
        username: String,
        email: String,
        password: String
    ) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val available = userRepository.isUsernameAvailable(username.lowercase())
                if (!available) {
                    showToast("Username sudah digunakan")
                    return@launch
                }

                val user = authRepository.signUpWithEmail(email, password)

                if (user != null) {
                    authRepository.updateDisplayName(name)

                    userRepository.createOrUpdateUser(
                        id = user.id,
                        name = name,
                        email = email,
                        avatarUrl = null,
                        provider = "email",
                        username = username,
                        usernameLower = username.lowercase()
                    )
                }

                showToast("Registrasi berhasil")
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
                    showToast("Google Sign-In gagal")
                    return@launch
                }

                val email = supaUser.email ?: return@launch

                // 🔥 AMBIL USER DARI DB JIKA SUDAH ADA
                val existingUser = userRepository.getUserByIdForce(supaUser.id)

                // ===============================
                // USERNAME (SUDAH BENAR DI KODE KAMU)
                // ===============================
                val (finalUsername, finalUsernameLower) =
                    if (existingUser != null && !existingUser.username.isNullOrBlank()) {
                        existingUser.username to existingUser.usernameLower
                    } else {
                        val prefix = email.substringBefore("@")
                        userRepository.generateUniqueUsername(prefix)
                    }

                // ===============================
                // NAMA PANGGILAN (FIX BARU)
                // ===============================
                val finalName =
                    if (existingUser != null && !existingUser.fullName.isNullOrBlank()) {
                        // ✅ USER SUDAH SET NAMA SEBELUMNYA → JANGAN TIMPA
                        existingUser.fullName
                    } else {
                        // 🆕 USER BARU VIA GOOGLE → AMBIL DARI GOOGLE
                        supaUser.userMetadata
                            ?.jsonObject
                            ?.get("full_name")
                            ?.jsonPrimitive
                            ?.content
                    }

                userRepository.createOrUpdateUser(
                    id = supaUser.id,
                    name = finalName,
                    email = email,
                    avatarUrl = null,
                    provider = "google",
                    username = finalUsername,
                    usernameLower = finalUsernameLower
                )

                navigateToHome()

            } catch (e: Exception) {
                Log.e("SUPABASE_AUTH", "Gagal Login Akun Google", e)
                showToast("Login Gagal: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    // =========================================================================
    // MODE SWITCH
    // =========================================================================

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

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun isValidUsername(username: String): Boolean {
        return Regex("^[a-z][a-z0-9_]{3,19}$").matches(username)
    }

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

        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val clickSpan = object : ClickableSpan() {
            override fun onClick(widget: View) = onClick()
            override fun updateDrawState(ds: TextPaint) {
                ds.isUnderlineText = false
                ds.color = Color.WHITE
            }
        }

        spannable.setSpan(
            clickSpan,
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        textView.text = spannable
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
    }
}
