package com.frzterr.app.ui.wallet

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.frzterr.app.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ImportWalletBottomSheet(
    private val onImport: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_import_wallet, container, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
            bottomSheet?.let {
                BottomSheetBehavior.from(it).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            // Sheet naik ke atas keyboard — persis seperti EditPostBottomSheet
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            // Nav bar warnanya sama dengan background sheet agar tampak menyatu
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.navigationBarColor = ContextCompat.getColor(requireContext(), R.color.background)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etSeedPhrase = view.findViewById<TextInputEditText>(R.id.etSeedPhrase)
        val btnImport = view.findViewById<MaterialButton>(R.id.btnImport)
        val tvImportError = view.findViewById<TextView>(R.id.tvImportError)

        btnImport.setOnClickListener {
            val seed = etSeedPhrase.text.toString().trim()
            if (seed.split("\\s+".toRegex()).size in listOf(12, 24)) {
                onImport(seed)
                dismiss()
            } else {
                tvImportError.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        const val TAG = "ImportWalletBottomSheet"
    }
}
