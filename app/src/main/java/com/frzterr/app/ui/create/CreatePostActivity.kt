package com.frzterr.app.ui.create

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.frzterr.app.R
import com.frzterr.app.data.local.ProfileLocalStore
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.post.PostRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

class CreatePostActivity : AppCompatActivity() {

    private lateinit var authRepo: AuthRepository
    private lateinit var postRepo: PostRepository

    private lateinit var btnCancel: TextView
    private lateinit var btnPost: MaterialButton
    private lateinit var imgAvatar: ShapeableImageView
    private lateinit var tvUsername: TextView
    private lateinit var etContent: EditText
    private lateinit var btnAddImage: ImageView
    private lateinit var imgPreview: ShapeableImageView
    private lateinit var btnRemoveImage: ImageView
    private lateinit var imagePreviewContainer: View
    private lateinit var loadingContainer: View

    private var selectedImageUri: Uri? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            showImagePreview(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        authRepo = AuthRepository()
        postRepo = PostRepository()

        bindViews()
        setupUI()
        setupListeners()
    }

    private fun bindViews() {
        btnCancel = findViewById(R.id.btnCancel)
        btnPost = findViewById(R.id.btnPost)
        imgAvatar = findViewById(R.id.imgAvatar)
        tvUsername = findViewById(R.id.tvUsername)
        etContent = findViewById(R.id.etContent)
        btnAddImage = findViewById(R.id.btnAddImage)
        imgPreview = findViewById(R.id.imgPreview)
        btnRemoveImage = findViewById(R.id.btnRemoveImage)
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer)
        loadingContainer = findViewById(R.id.loadingContainer)
    }

    private fun setupUI() {
        // Load user info from local storage
        val (name, avatarPath, username) = ProfileLocalStore.load(this)

        tvUsername.text = "@${username ?: "username"}"

        // Load avatar
        val localAvatarPath = ProfileLocalStore.loadLocalAvatarPath(this)
        if (localAvatarPath != null) {
            val file = File(localAvatarPath)
            if (file.exists()) {
                imgAvatar.setImageURI(Uri.fromFile(file))
                imgAvatar.background =
                    ContextCompat.getDrawable(this, R.drawable.bg_circle)
            }
        }

        // Focus on content input
        etContent.requestFocus()
    }

    private fun setupListeners() {
        btnCancel.setOnClickListener {
            finish()
        }

        btnPost.setOnClickListener {
            createPost()
        }

        btnAddImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        btnRemoveImage.setOnClickListener {
            removeImage()
        }

        // Enable/disable post button based on content
        etContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnPost.isEnabled = !s.isNullOrBlank()
            }
        })
    }

    private fun showImagePreview(uri: Uri) {
        imgPreview.load(uri) {
            crossfade(true)
        }
        imagePreviewContainer.visibility = View.VISIBLE
        btnAddImage.visibility = View.GONE
    }

    private fun removeImage() {
        selectedImageUri = null
        imagePreviewContainer.visibility = View.GONE
        btnAddImage.visibility = View.VISIBLE
    }

    private fun createPost() {
        val content = etContent.text.toString().trim()
        if (content.isBlank()) {
            Toast.makeText(this, "Please enter some content", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                loadingContainer.visibility = View.VISIBLE
                btnPost.isEnabled = false

                val currentUser = authRepo.getCurrentUser()
                if (currentUser == null) {
                    Toast.makeText(this@CreatePostActivity, "Not authenticated", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Upload image if selected
                var imageUrl: String? = null
                if (selectedImageUri != null) {
                    val imageBytes = compressImage(selectedImageUri!!)
                    val uploadResult = postRepo.uploadPostImage(currentUser.id, imageBytes)
                    
                    if (uploadResult.isSuccess) {
                        imageUrl = uploadResult.getOrNull()
                    } else {
                        Toast.makeText(
                            this@CreatePostActivity,
                            "Failed to upload image",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                }

                // Create post
                val result = postRepo.createPost(currentUser.id, content, imageUrl)

                if (result.isSuccess) {
                    Toast.makeText(this@CreatePostActivity, "Post created!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@CreatePostActivity,
                        "Failed to create post",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@CreatePostActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                loadingContainer.visibility = View.GONE
                btnPost.isEnabled = true
            }
        }
    }

    private suspend fun compressImage(uri: Uri): ByteArray {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                contentResolver,
                uri
            )

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            stream.toByteArray()
        }
    }
}
