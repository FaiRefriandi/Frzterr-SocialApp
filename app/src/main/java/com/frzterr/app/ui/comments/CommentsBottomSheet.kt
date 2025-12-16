package com.frzterr.app.ui.comments

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.frzterr.app.R
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.post.PostRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class CommentsBottomSheet(
    private val postId: String,
    private val onCommentAdded: () -> Unit = {}
) : BottomSheetDialogFragment() {

    private val postRepo = PostRepository()
    private val authRepo = AuthRepository()
    private lateinit var adapter: CommentAdapter

    private lateinit var rvComments: RecyclerView
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar
    private lateinit var etComment: EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnClose: ImageView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        
        // Set soft input mode to adjust for keyboard
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            
            // Set transparent background untuk rounded corners
            bottomSheet.setBackgroundResource(android.R.color.transparent)
            
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
            
            // Instagram-style: bottom menempel, berhenti di tengah, bisa expand
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
            behavior.isDraggable = true
            behavior.isHideable = true
            behavior.skipCollapsed = false
            
            // Expand to half screen by default
            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            behavior.peekHeight = (screenHeight * 0.5).toInt()
        }
        
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_comments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvComments = view.findViewById(R.id.rvComments)
        emptyState = view.findViewById(R.id.emptyState)
        progressBar = view.findViewById(R.id.progressBar)
        etComment = view.findViewById(R.id.etComment)
        btnSend = view.findViewById(R.id.btnSend)
        btnClose = view.findViewById(R.id.btnClose)

        adapter = CommentAdapter()
        rvComments.adapter = adapter

        loadComments()

        btnClose.setOnClickListener {
            dismiss()
        }

        // Auto-expand when keyboard appears (when input is focused)
        etComment.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Expand bottom sheet saat keyboard muncul
                val dialog = dialog as? BottomSheetDialog
                val bottomSheet = dialog?.findViewById<View>(
                    com.google.android.material.R.id.design_bottom_sheet
                )
                bottomSheet?.let {
                    val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                    behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }

        // Show/hide send button based on text input
        etComment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnSend.visibility = if (s.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        })

        btnSend.setOnClickListener {
            val content = etComment.text.toString().trim()
            if (content.isNotBlank()) {
                addComment(content)
            }
        }
    }

    private fun loadComments() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                emptyState.visibility = View.GONE

                val comments = postRepo.getPostComments(postId)

                adapter.submitList(comments)
                emptyState.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat komentar", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun addComment(content: String) {
        lifecycleScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser()
                if (currentUser == null) {
                    Toast.makeText(requireContext(), "Belum login", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val result = postRepo.addComment(postId, currentUser.id, content)

                if (result.isSuccess) {
                    etComment.text.clear()
                    Toast.makeText(requireContext(), "Komentar ditambahkan", Toast.LENGTH_SHORT).show()
                    
                    // Reload comments
                    loadComments()
                    
                    // Shorter delay since counts are real-time
                    kotlinx.coroutines.delay(200)
                    onCommentAdded()
                } else {
                    Toast.makeText(requireContext(), "Gagal menambahkan komentar", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val TAG = "CommentsBottomSheet"
    }
}
