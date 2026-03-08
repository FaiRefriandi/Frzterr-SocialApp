package com.frzterr.app.ui.aichat

import android.view.View
import android.widget.LinearLayout
import com.frzterr.app.R
import com.frzterr.app.ui.common.BaseCustomBottomSheet

class AiUploadBottomSheet(
    private val onCamera: () -> Unit,
    private val onGallery: () -> Unit,
    private val onDocument: () -> Unit
) : BaseCustomBottomSheet() {

    override fun getLayoutResId() = R.layout.bottom_sheet_ai_upload

    override fun onSheetCreated(view: View) {
        view.findViewById<LinearLayout>(R.id.btnCamera).setOnClickListener {
            dismiss()
            onCamera()
        }
        view.findViewById<LinearLayout>(R.id.btnGallery).setOnClickListener {
            dismiss()
            onGallery()
        }
        view.findViewById<LinearLayout>(R.id.btnDocument).setOnClickListener {
            dismiss()
            onDocument()
        }
    }

    companion object {
        const val TAG = "AiUploadBottomSheet"
    }
}
