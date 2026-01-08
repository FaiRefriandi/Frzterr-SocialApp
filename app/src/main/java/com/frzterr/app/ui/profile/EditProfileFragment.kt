package com.frzterr.app.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.frzterr.app.R
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.user.UserRepository
import com.frzterr.app.databinding.FragmentEditProfileBinding
import com.yalantis.ucrop.UCrop
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val authRepo = AuthRepository()
    private val userRepo = UserRepository()
    private val profileVM: ProfileViewModel by activityViewModels()

    private var newAvatarUrl: String? = null
    private var hasUnsavedChanges = false

    // Image picker
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { startCrop(it) }
    }

    // Crop result
    private val cropResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            UCrop.getOutput(result.data!!)?.let { uploadAvatar(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEditProfileBinding.bind(view)

        setupUI()
        loadCurrentProfile()
        setupListeners()
    }

    private fun setupUI() {
        // Track unsaved changes
        binding.etName.doAfterTextChanged { hasUnsavedChanges = true }
        binding.etUsername.doAfterTextChanged {
            hasUnsavedChanges = true
            binding.tilUsername.error = null // Clear error on typing
        }
        binding.etBio.doAfterTextChanged { hasUnsavedChanges = true }
    }

    private fun loadCurrentProfile() {
        val user = profileVM.user.value ?: return

        binding.etName.setText(user.fullName)
        binding.etUsername.setText(user.username)
        binding.etBio.setText(user.bio)

        binding.imgAvatar.load(user.avatarUrl) {
            placeholder(R.drawable.ic_user_placeholder)
            error(R.drawable.ic_user_placeholder)
        }
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            if (hasUnsavedChanges) {
                // TODO: Show confirmation dialog
                findNavController().navigateUp()
            } else {
                findNavController().navigateUp()
            }
        }

        binding.btnSave.setOnClickListener {
            saveProfile()
        }

        binding.btnChangeAvatar.setOnClickListener {
            pickImage.launch("image/*")
        }
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val bio = binding.etBio.text.toString().trim()

        // Validation
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        if (username.isEmpty()) {
            binding.tilUsername.error = "Username tidak boleh kosong"
            return
        }

        // Username format validation (letters, numbers, underscore only)
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            binding.tilUsername.error = "Username hanya boleh huruf, angka, dan underscore"
            return
        }

        showLoading(true)

        lifecycleScope.launch {
            try {
                val userId = runBlocking { authRepo.getCurrentUser()?.id }
                if (userId == null) {
                    Toast.makeText(requireContext(), "User tidak ditemukan", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                    return@launch
                }

                // Check username availability if changed
                val currentUsername = profileVM.user.value?.username
                if (username != currentUsername) {
                    val isAvailable = userRepo.checkUsernameAvailable(username, userId)
                    if (!isAvailable) {
                        withContext(Dispatchers.Main) {
                            binding.tilUsername.error = "Username sudah digunakan"
                            showLoading(false)
                        }
                        return@launch
                    }
                }

                // Update profile
                val result = userRepo.updateUserProfile(
                    userId = userId,
                    fullName = name,
                    username = username,
                    bio = bio.ifEmpty { null },
                    avatarUrl = newAvatarUrl
                )

                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        // Update Shared ViewModel IMMEDIATELY (Real-time)
                        val versionedAvatarUrl = if (newAvatarUrl != null) {
                            if (newAvatarUrl!!.contains("?v=")) newAvatarUrl 
                            else "$newAvatarUrl?v=${System.currentTimeMillis()}"
                        } else {
                            profileVM.user.value?.avatarUrl
                        }

                        val updatedUser = profileVM.user.value?.copy(
                            fullName = name,
                            username = username,
                            bio = bio.ifEmpty { null },
                            avatarUrl = versionedAvatarUrl
                        )
                        profileVM.updateUser(updatedUser)

                        Toast.makeText(requireContext(), "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                        hasUnsavedChanges = false
                        findNavController().navigateUp()
                    } else {
                        val error = result.exceptionOrNull()
                        android.util.Log.e("EditProfile", "Save failed", error)
                        Toast.makeText(requireContext(), "Gagal: ${error?.message ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    }
                    showLoading(false)
                }
            } catch (e: Exception) {
                android.util.Log.e("EditProfile", "Exception in saveProfile", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    showLoading(false)
                }
            }
        }
    }

    private fun startCrop(uri: Uri) {
        val destUri = Uri.fromFile(File(requireContext().cacheDir, "cropped_avatar_${System.currentTimeMillis()}.jpg"))
        val options = UCrop.Options().apply {
            setCompressionQuality(90)
            setCircleDimmedLayer(true)
            setShowCropFrame(false)
            setShowCropGrid(false)
            setToolbarColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.background))
            setStatusBarColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.background))
            setToolbarWidgetColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.white))
        }

        val uCrop = UCrop.of(uri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(800, 800)
            .withOptions(options)

        cropResult.launch(uCrop.getIntent(requireContext()))
    }

    private fun uploadAvatar(uri: Uri) {
        showLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userId = authRepo.getCurrentUser()?.id ?: return@launch
                
                // Filename must be {userId}.jpg to match storage policy:
                // split_part(name, '.', 1) = userId
                val fileName = "${userId}.jpg"

                val bytes = requireContext().contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw Exception("Cannot read image")

                val supabaseClient = com.frzterr.app.data.remote.supabase.SupabaseManager.client
                val bucket = supabaseClient.storage["avatars"]

                // Upload (will overwrite existing avatar)
                bucket.upload(fileName, bytes) {
                    upsert = true
                }

                // Get public URL and add versioning to bypass coil cache
                val publicUrl = "${bucket.publicUrl(fileName)}?v=${System.currentTimeMillis()}"

                withContext(Dispatchers.Main) {
                    newAvatarUrl = publicUrl
                    hasUnsavedChanges = true
                    binding.imgAvatar.load(publicUrl) {
                        placeholder(R.drawable.ic_user_placeholder)
                        error(R.drawable.ic_user_placeholder)
                    }
                    showLoading(false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal upload avatar: ${e.message}", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !show
        binding.btnCancel.isEnabled = !show
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
