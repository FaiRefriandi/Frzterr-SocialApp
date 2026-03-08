package com.frzterr.app.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

class GoogleSignInHelper(private val context: Context) {

    private val webClientId =
        "25471157266-g9p37e12ldum46rc3ob9usi03a3f961e.apps.googleusercontent.com"

    private val credentialManager = CredentialManager.create(context)

    private fun generateNonce(): String {
        val raw = UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun getGoogleIdToken(activity: Activity): String? {
        // Coba GetSignInWithGoogleOption dulu (direkomendasikan untuk button-triggered flow)
        // Ini yang kompatibel dengan Xiaomi/HyperOS dan semua device lainnya
        val tokenFromSignInOption = trySignInWithGoogleOption(activity)
        if (tokenFromSignInOption != null) return tokenFromSignInOption

        // Fallback ke GetGoogleIdOption jika flow pertama gagal (bukan karena user cancel)
        Log.w("GOOGLE_FLOW", "Falling back to GetGoogleIdOption...")
        return tryGetGoogleIdOption(activity)
    }

    /**
     * Flow utama: GetSignInWithGoogleOption
     * Menampilkan dialog standar Google untuk memilih akun.
     * Direkomendasikan untuk button-triggered sign-in (lebih kompatibel di Xiaomi/MIUI/HyperOS).
     */
    private suspend fun trySignInWithGoogleOption(activity: Activity): String? {
        return try {
            val nonce = generateNonce()
            Log.d("GOOGLE_FLOW", "[Primary] Trying GetSignInWithGoogleOption...")

            val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
                .setNonce(nonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(activity, request)
            val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
            Log.d("GOOGLE_FLOW", "[Primary] Success! Token extracted.")
            googleCred.idToken

        } catch (e: GetCredentialCancellationException) {
            // User sengaja cancel → jangan fallback, langsung return null
            Log.w("GOOGLE_FLOW", "[Primary] User cancelled sign-in.")
            null
        } catch (e: NoCredentialException) {
            // Tidak ada akun yang tersedia di device
            Log.w("GOOGLE_FLOW", "[Primary] NoCredentialException: ${e.message}")
            null
        } catch (e: Exception) {
            // Error lain → boleh fallback ke metode berikutnya
            Log.e("GOOGLE_FLOW", "[Primary] Failed: ${e::class.java.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Fallback: GetGoogleIdOption (One-Tap)
     * Dipakai jika flow utama gagal karena alasan teknis (bukan karena user cancel).
     */
    private suspend fun tryGetGoogleIdOption(activity: Activity): String? {
        return try {
            val nonce = generateNonce()
            Log.d("GOOGLE_FLOW", "[Fallback] Trying GetGoogleIdOption...")

            val googleOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setNonce(nonce)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()

            val result = credentialManager.getCredential(activity, request)
            val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
            Log.d("GOOGLE_FLOW", "[Fallback] Success! Token extracted.")
            googleCred.idToken

        } catch (e: Exception) {
            Log.e("GOOGLE_FLOW_ERROR", "[Fallback] Failed: ${e::class.java.simpleName}: ${e.message}", e)
            null
        }
    }
}
