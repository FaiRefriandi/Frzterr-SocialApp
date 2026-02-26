package com.frzterr.app.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frzterr.app.R
import com.frzterr.app.ui.common.BaseCustomBottomSheet
import com.google.android.material.button.MaterialButton

class CreateWalletBottomSheet(
    private val mnemonic: String,
    private val onConfirm: () -> Unit
) : BaseCustomBottomSheet() {

    override fun getLayoutResId(): Int = R.layout.dialog_create_wallet

    override fun onSheetCreated(view: View) {
        val rvSeedWords = view.findViewById<RecyclerView>(R.id.rvSeedWords)
        val btnCopySeed = view.findViewById<MaterialButton>(R.id.btnCopySeed)
        val btnConfirmSaved = view.findViewById<MaterialButton>(R.id.btnConfirmSaved)

        val words = mnemonic.split(" ")
        rvSeedWords.layoutManager = GridLayoutManager(requireContext(), 3)
        rvSeedWords.adapter = SeedWordAdapter(words)

        btnCopySeed.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Seed Phrase", mnemonic)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnConfirmSaved.setOnClickListener {
            onConfirm()
            dismiss()
        }
    }

    private class SeedWordAdapter(private val words: List<String>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<SeedWordAdapter.ViewHolder>() {

        class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val textView: android.widget.TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = "${position + 1}. ${words[position]}"
            holder.textView.textSize = 14f
            holder.textView.setTextColor(
                holder.itemView.context.getColor(com.frzterr.app.R.color.text_primary)
            )
        }

        override fun getItemCount() = words.size
    }

    companion object {
        const val TAG = "CreateWalletBottomSheet"
    }
}
