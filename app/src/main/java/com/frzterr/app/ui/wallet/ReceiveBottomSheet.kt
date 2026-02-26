package com.frzterr.app.ui.wallet

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.frzterr.app.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_receive, container, false)

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
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.navigationBarColor = ContextCompat.getColor(requireContext(), R.color.background)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabChain = view.findViewById<TabLayout>(R.id.tabChain)
        val tvReceiveAddress = view.findViewById<TextView>(R.id.tvReceiveAddress)
        val tvWarning = view.findViewById<TextView>(R.id.tvWarning)
        val ivQrCode = view.findViewById<ImageView>(R.id.ivQrCode)
        val btnCopy = view.findViewById<ImageButton>(R.id.btnCopyReceiveAddress)

        val symbols = listOf("ETH", "BTC", "SOL", "BNB", "USDT")
        symbols.forEach { tabChain.addTab(tabChain.newTab().setText(it)) }

        fun getAddress(symbol: String) = when (symbol) {
            "BTC" -> btcAddress; "SOL" -> solAddress; else -> ethAddress
        }
        fun getWarning(symbol: String) = when (symbol) {
            "BNB" -> "⚠ Send only BNB (BEP-20) to this address"
            "USDT" -> "⚠ Send only USDT (ERC-20) to this address"
            else -> ""
        }
        fun showChain(symbol: String) {
            val address = getAddress(symbol)
            tvReceiveAddress.text = address
            tvWarning.text = getWarning(symbol)
            ivQrCode.setImageBitmap(generateQrCode(address))
        }

        tabChain.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { showChain(symbols[tab.position]) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        showChain("ETH")

        btnCopy.setOnClickListener {
            val address = tvReceiveAddress.text.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Address", address))
            Toast.makeText(requireContext(), "Address copied!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateQrCode(text: String): Bitmap {
        val size = 512
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    companion object { const val TAG = "ReceiveBottomSheet" }
}
