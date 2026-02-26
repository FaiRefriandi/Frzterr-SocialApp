package com.frzterr.app.ui.wallet

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.frzterr.app.R
import com.frzterr.app.databinding.DialogSendBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.NumberFormat
import java.util.Locale

class SendBottomSheet(
    private val assets: List<CryptoAsset>
) : BottomSheetDialogFragment() {

    private var _binding: DialogSendBinding? = null
    private val binding get() = _binding!!

    private var selectedAsset: CryptoAsset? = null
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCoinSelector()
        setupAmountWatcher()
        setupSendButton()
    }

    private fun setupCoinSelector() {
        assets.forEach { asset ->
            val chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_coin_chip, binding.layoutCoinSelector, false)

            chip.findViewById<TextView>(R.id.tvChipSymbol).text = asset.symbol
            chip.setOnClickListener { selectAsset(asset, chip) }
            binding.layoutCoinSelector.addView(chip)
        }

        // Auto-select pertama
        if (assets.isNotEmpty()) {
            val firstChip = binding.layoutCoinSelector.getChildAt(0)
            selectAsset(assets[0], firstChip)
        }
    }

    private fun selectAsset(asset: CryptoAsset, selectedChip: View) {
        // Reset semua chip
        for (i in 0 until binding.layoutCoinSelector.childCount) {
            val chip = binding.layoutCoinSelector.getChildAt(i)
            chip.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#28FFFFFF"))
        }
        // Highlight selected
        selectedChip.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#3014F195"))

        selectedAsset = asset
        binding.tvAmountSymbol.text = asset.symbol

        // Update balance display
        val balFormat = if (asset.balance < 0.001 && asset.balance > 0) "%.8f" else "%.4f"
        binding.tvSendBalance.text = "Balance: ${String.format(balFormat, asset.balance)} ${asset.symbol}"

        // Recalculate USD value
        updateUsdValue()
    }

    private fun setupAmountWatcher() {
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateUsdValue() }
        })
    }

    private fun updateUsdValue() {
        val asset = selectedAsset ?: return
        val amountStr = binding.etAmount.text.toString()
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val usdValue = amount * asset.price
        binding.tvSendUsdValue.text = "≈ ${currencyFormat.format(usdValue)}"
    }

    private fun setupSendButton() {
        binding.btnConfirmSend.setOnClickListener {
            val asset = selectedAsset ?: return@setOnClickListener
            val recipient = binding.etRecipientAddress.text.toString().trim()
            val amountStr = binding.etAmount.text.toString().trim()

            // Validasi input
            if (recipient.isEmpty()) {
                binding.etRecipientAddress.error = "Enter recipient address"
                return@setOnClickListener
            }
            if (!isValidAddress(recipient, asset.symbol)) {
                binding.etRecipientAddress.error = "Invalid ${asset.symbol} address format"
                return@setOnClickListener
            }
            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                binding.etAmount.error = "Enter valid amount"
                return@setOnClickListener
            }
            if (amount > asset.balance) {
                binding.etAmount.error = "Insufficient balance"
                return@setOnClickListener
            }

            // Konfirmasi
            val usdValue = currencyFormat.format(amount * asset.price)
            val msg = "Send ${String.format("%.6f", amount)} ${asset.symbol} ($usdValue) to\n${recipient.take(20)}...?"
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Confirm Transaction")
                .setMessage(msg)
                .setPositiveButton("Send") { _, _ ->
                    Toast.makeText(
                        requireContext(),
                        "Transaction broadcast — feature requires node integration",
                        Toast.LENGTH_LONG
                    ).show()
                    dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /** Validasi format address dasar berdasarkan chain */
    private fun isValidAddress(address: String, symbol: String): Boolean {
        return when (symbol) {
            "BTC" -> address.matches(Regex("^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,62}$"))
            "SOL" -> address.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$"))
            else -> address.matches(Regex("^0x[0-9a-fA-F]{40}$")) // ETH, BNB, USDT
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as com.google.android.material.bottomsheet.BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }
        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
