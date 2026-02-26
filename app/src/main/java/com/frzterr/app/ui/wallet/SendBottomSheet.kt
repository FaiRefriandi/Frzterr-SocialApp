package com.frzterr.app.ui.wallet

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.frzterr.app.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.util.Locale

class SendBottomSheet(
    private val assets: List<CryptoAsset>
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_send, container, false)

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
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.navigationBarColor = ContextCompat.getColor(requireContext(), R.color.background)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val layoutCoinSelector = view.findViewById<LinearLayout>(R.id.layoutCoinSelector)
        val tvAmountSymbol = view.findViewById<TextView>(R.id.tvAmountSymbol)
        val tvSendBalance = view.findViewById<TextView>(R.id.tvSendBalance)
        val tvSendUsdValue = view.findViewById<TextView>(R.id.tvSendUsdValue)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val etRecipientAddress = view.findViewById<EditText>(R.id.etRecipientAddress)
        val btnConfirmSend = view.findViewById<MaterialButton>(R.id.btnConfirmSend)

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        var selectedAsset: CryptoAsset? = null

        fun updateUsdValue() {
            val asset = selectedAsset ?: return
            val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            tvSendUsdValue.text = "≈ ${currencyFormat.format(amount * asset.price)}"
        }

        fun selectAsset(asset: CryptoAsset, selectedChip: View) {
            for (i in 0 until layoutCoinSelector.childCount) {
                layoutCoinSelector.getChildAt(i).backgroundTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#28FFFFFF"))
            }
            selectedChip.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#3014F195"))
            selectedAsset = asset
            tvAmountSymbol.text = asset.symbol
            val fmt = if (asset.balance < 0.001 && asset.balance > 0) "%.8f" else "%.4f"
            tvSendBalance.text = "Balance: ${String.format(fmt, asset.balance)} ${asset.symbol}"
            updateUsdValue()
        }

        assets.forEach { asset ->
            val chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_coin_chip, layoutCoinSelector, false)
            chip.findViewById<TextView>(R.id.tvChipSymbol).text = asset.symbol
            chip.setOnClickListener { selectAsset(asset, chip) }
            layoutCoinSelector.addView(chip)
        }
        if (assets.isNotEmpty()) selectAsset(assets[0], layoutCoinSelector.getChildAt(0))

        etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { updateUsdValue() }
        })

        btnConfirmSend.setOnClickListener {
            val asset = selectedAsset ?: return@setOnClickListener
            val recipient = etRecipientAddress.text.toString().trim()
            val amountStr = etAmount.text.toString().trim()
            if (recipient.isEmpty()) { etRecipientAddress.error = "Enter recipient address"; return@setOnClickListener }
            if (!isValidAddress(recipient, asset.symbol)) { etRecipientAddress.error = "Invalid ${asset.symbol} address"; return@setOnClickListener }
            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) { etAmount.error = "Enter valid amount"; return@setOnClickListener }
            if (amount > asset.balance) { etAmount.error = "Insufficient balance"; return@setOnClickListener }

            AlertDialog.Builder(requireContext())
                .setTitle("Confirm Transaction")
                .setMessage("Send ${String.format("%.6f", amount)} ${asset.symbol} (${currencyFormat.format(amount * asset.price)}) to\n${recipient.take(20)}...?")
                .setPositiveButton("Send") { _, _ ->
                    Toast.makeText(requireContext(), "Transaction broadcast — feature requires node integration", Toast.LENGTH_LONG).show()
                    dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun isValidAddress(address: String, symbol: String) = when (symbol) {
        "BTC" -> address.matches(Regex("^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,62}$"))
        "SOL" -> address.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$"))
        else  -> address.matches(Regex("^0x[0-9a-fA-F]{40}$"))
    }

    companion object { const val TAG = "SendBottomSheet" }
}
