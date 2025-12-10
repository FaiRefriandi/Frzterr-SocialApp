package com.frzlss.frzterr.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.frzlss.frzterr.MainActivity
import com.frzlss.frzterr.data.remote.supabase.SupabaseManager
import com.frzlss.frzterr.data.repository.auth.AuthRepository
import com.frzlss.frzterr.data.repository.user.UserRepository
import com.frzlss.frzterr.databinding.ActivityAuthBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    private lateinit var googleSignInClient: GoogleSignInClient

    private val webClientId =
        "137915645454-q7fbf6h8nk1ikb7pvaai66etfgbbu4cb.apps.googleusercontent.com"

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SupabaseManager.initialize(applicationContext)

        if (authRepository.isLoggedIn()) {
            navigateToHome()
            return
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        setupClicks()

        switchToLogin()   // default
    }

    private fun setupClicks() = with(binding) {

        // Login button
        btnLogin.setOnClickListener {
            val email = edtLoginEmail.text.toString().trim()
            val password = edtLoginPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                showToast("Email dan password tidak boleh kosong")
                return@setOnClickListener
            }

            doEmailLogin(email, password)
        }

        // Signup button
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
            signInWithGoogle()
        }

        // Switch ke SIGNUP
        tvGotoSignup.setOnClickListener {
            if (isLoginMode) switchToSignUp()
        }

        // Switch ke LOGIN
        tvGotoLogin.setOnClickListener {
            if (!isLoginMode) switchToLogin()
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken

                if (idToken != null) {
                    doGoogleSignIn(idToken)
                } else {
                    showToast("Google ID Token null")
                }
            } catch (e: ApiException) {
                showToast("Google Sign-In gagal: ${e.statusCode}")
            }
        }

    private fun signInWithGoogle() {
        googleLauncher.launch(googleSignInClient.signInIntent)
    }

    // MODE UI
    private fun switchToLogin() = with(binding) {
        isLoginMode = true

        layoutLoginForm.visibility = View.VISIBLE
        layoutSignupForm.visibility = View.GONE

        tvGotoSignup.visibility = View.VISIBLE
        tvGotoLogin.visibility = View.GONE

        tvTitle.text = "Sign in to your Account"
        tvSubtitle.text = "Enter your email and password to log in"
    }

    private fun switchToSignUp() = with(binding) {
        isLoginMode = false

        layoutLoginForm.visibility = View.GONE
        layoutSignupForm.visibility = View.VISIBLE

        tvGotoSignup.visibility = View.GONE
        tvGotoLogin.visibility = View.VISIBLE

        tvTitle.text = "Create Account"
        tvSubtitle.text = "Fill the form below to register"
    }

    // AUTH LOGIC

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
        setLoading(true)
        lifecycleScope.launch {
            try {
                authRepository.signInWithGoogleIdToken(idToken)

                val supaUser = authRepository.getCurrentUser()
                if (supaUser != null) {

                    val fullName = supaUser.userMetadata
                        ?.jsonObject
                        ?.get("full_name")
                        ?.jsonPrimitive
                        ?.content

                    val avatarUrl = supaUser.userMetadata
                        ?.jsonObject
                        ?.get("avatar_url")
                        ?.jsonPrimitive
                        ?.content

                    userRepository.createOrUpdateUser(
                        id = supaUser.id,
                        name = fullName,
                        email = supaUser.email,
                        avatarUrl = avatarUrl,
                        provider = "google"
                    )
                }

                navigateToHome()
            } catch (e: Exception) {
                showToast("Google Sign-In gagal: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) = with(binding) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        root.isEnabled = !loading
    }

    private fun showToast(msg: String?) {
        Toast.makeText(this, msg ?: "Terjadi kesalahan", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
