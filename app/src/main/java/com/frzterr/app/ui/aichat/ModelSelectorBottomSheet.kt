package com.frzterr.app.ui.aichat

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frzterr.app.R
import com.frzterr.app.ui.common.BaseCustomBottomSheet

class ModelSelectorBottomSheet(
    private val currentModelId: String,
    private val onModelSelected: (NvidiaModel) -> Unit
) : BaseCustomBottomSheet() {

    override fun getLayoutResId() = R.layout.bottom_sheet_model_selector

    override fun onSheetCreated(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.rvModels)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = ModelSelectorAdapter(
            models = NVIDIA_MODELS,
            selectedModelId = currentModelId,
            onModelSelected = { model ->
                dismiss()
                onModelSelected(model)
            }
        )
    }

    companion object {
        const val TAG = "ModelSelectorBottomSheet"
    }
}
