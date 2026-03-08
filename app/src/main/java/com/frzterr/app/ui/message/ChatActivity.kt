package com.frzterr.app.ui.message

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.frzterr.app.R
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.user.UserRepository
import androidx.lifecycle.lifecycleScope
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_OTHER_USER_ID = "extra_other_user_id"
        const val EXTRA_OTHER_USERNAME = "extra_other_username"
        const val EXTRA_OTHER_AVATAR_URL = "extra_other_avatar_url"
    }

    private val viewModel: ChatViewModel by viewModels()
    private val authRepo = AuthRepository()
    private lateinit var adapter: ChatMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: run {
            finish()
            return
        }
        val otherUserId = intent.getStringExtra(EXTRA_OTHER_USER_ID) ?: ""
        val otherUsername = intent.getStringExtra(EXTRA_OTHER_USERNAME)
        val otherAvatarUrl = intent.getStringExtra(EXTRA_OTHER_AVATAR_URL)

        // ===============================
        // 🔹 STATUS BAR SPACING
        // ===============================
        val toolbar = findViewById<View>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
            insets
        }

        // ===============================
        // 🔹 VIEWS
        // ===============================
        val ivBack = findViewById<ImageView>(R.id.ivBack)
        val ivChatAvatar = findViewById<ShapeableImageView>(R.id.ivChatAvatar)
        val tvChatUsername = findViewById<TextView>(R.id.tvChatUsername)
        val rvMessages = findViewById<RecyclerView>(R.id.rvMessages)
        val etMessage = findViewById<EditText>(R.id.etMessage)
        val btnSend = findViewById<ImageView>(R.id.btnSend)

        // ===============================
        // 🔹 TOOLBAR SETUP
        // ===============================
        ivBack.setOnClickListener { finish() }

        // Load username
        if (otherUsername != null) {
            tvChatUsername.text = otherUsername
        } else {
            lifecycleScope.launch {
                val user = UserRepository().getUserById(otherUserId)
                tvChatUsername.text = user?.fullName?.takeIf { it.isNotBlank() } ?: "@${user?.username}"
            }
        }

        // Load avatar
        Glide.with(this)
            .load(otherAvatarUrl)
            .placeholder(R.drawable.ic_user_placeholder)
            .circleCrop()
            .into(ivChatAvatar)

        // ===============================
        // 🔹 ADAPTER
        // ===============================
        val currentUserId = authRepo.getCurrentUser()?.id ?: ""
        adapter = ChatMessageAdapter(
            currentUserId = currentUserId,
            otherAvatarUrl = otherAvatarUrl
        )
        val layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.layoutManager = layoutManager
        rvMessages.adapter = adapter

        // ===============================
        // 🔹 OBSERVERS
        // ===============================
        viewModel.messages.observe(this) { messages ->
            adapter.submitList(messages) {
                if (adapter.itemCount > 0) {
                    rvMessages.smoothScrollToPosition(adapter.itemCount - 1)
                }
            }
        }

        viewModel.isSending.observe(this) {
            btnSend.isEnabled = !it
            btnSend.alpha = if (it) 0.5f else 1.0f
        }

        // ===============================
        // 🔹 SEND BUTTON
        // ===============================
        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isBlank()) return@setOnClickListener
            etMessage.setText("")
            viewModel.sendMessage(text)
        }

        // ===============================
        // 🔹 INIT
        // ===============================
        viewModel.init(conversationId)
        viewModel.markAsRead()

        // Adjust for keyboard
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.messageInputArea)) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, imeHeight.coerceAtLeast(navHeight))
            insets
        }
    }
}
