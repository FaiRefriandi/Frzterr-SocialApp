package com.frzterr.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.frzterr.app.R
import com.frzterr.app.data.model.Post
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EditPostBottomSheet(
    private val post: Post,
    private val onSaveClick: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_edit_post, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as com.google.android.material.bottomsheet.BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Fix keyboard overlap - Ensure dialog moves up when keyboard appears
        dialog?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        val etContent = view.findViewById<EditText>(R.id.etContent)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)
        val btnSave = view.findViewById<TextView>(R.id.btnSave)

        // Pre-fill content
        etContent.setText(post.content)
        etContent.setSelection(post.content.length)
        etContent.requestFocus()

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnSave.setOnClickListener {
            val newContent = etContent.text.toString().trim()
            if (newContent.isNotBlank()) {
                if (newContent != post.content) {
                    onSaveClick(newContent)
                }
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Konten tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val TAG = "EditPostBottomSheet"
    }
}
