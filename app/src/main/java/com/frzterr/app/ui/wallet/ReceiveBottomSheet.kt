package com.frzterr.app.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.frzterr.app.databinding.DialogReceiveBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

class ReceiveBottomSheet(
    private val btcAddress: String,
    private val ethAddress: String,
    private val solAddress: String
) : BottomSheetDialogFragment() {

    private var _binding: DialogReceiveBinding? = null
    private val binding get() = _binding!!

    private data class ChainInfo(val label: String, val symbol: String, val address: String, val warning: String = "")

    private val chains = listOf(
        ChainInfo("Ethereum", "ETH", "", ""),       // alamat diisi saat init
        ChainInfo("Bitcoin", "BTC", "", ""),
        ChainInfo("Solana", "SOL", "", ""),
        ChainInfo("BNB Chain", "BNB", "", "Same as ETH address (EVM)"),
        ChainInfo("USDT (ERC-20)", "USDT", "", "Same as ETH address (EVM)")
    )

    // Resolve address per chain
    private fun getAddress(symbol: String) = when (symbol) {
        "BTC" -> btcAddress
        "SOL" -> solAddress
        else -> ethAddress // ETH, BNB, USDT
    }

    private fun getWarning(symbol: String) = when (symbol) {
        "BNB" -> "⚠ Send only BNB (BEP-20) to this address"
        "USDT" -> "⚠ Send only USDT (ERC-20) to this address"
        else -> ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogReceiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup tabs
        chains.forEach { chain ->
            binding.tabChain.addTab(binding.tabChain.newTab().setText(chain.symbol))
        }

        binding.tabChain.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showChain(chains[tab.position].symbol)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Default: ETH
        showChain("ETH")

        binding.btnCopyReceiveAddress.setOnClickListener {
            val address = binding.tvReceiveAddress.text.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Address", address))
            Toast.makeText(context, "Address copied!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showChain(symbol: String) {
        val address = getAddress(symbol)
        binding.tvReceiveAddress.text = address
        binding.tvWarning.text = getWarning(symbol)
        binding.ivQrCode.setImageBitmap(generateQrCode(address))
    }

    private fun generateQrCode(text: String): Bitmap {
        val size = 512
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
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
