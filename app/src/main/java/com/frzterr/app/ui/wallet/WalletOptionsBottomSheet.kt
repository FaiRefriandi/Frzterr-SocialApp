package com.frzterr.app.ui.wallet

import android.view.View
import android.widget.LinearLayout
import com.frzterr.app.R
import com.frzterr.app.ui.common.BaseCustomBottomSheet

class WalletOptionsBottomSheet(
    private val onDeleteWalletClick: () -> Unit
) : BaseCustomBottomSheet() {

    override fun getLayoutResId(): Int = R.layout.bottom_sheet_wallet_options

    override fun onSheetCreated(view: View) {
        view.findViewById<LinearLayout>(R.id.btnDeleteWallet).setOnClickListener {
            onDeleteWalletClick()
            dismiss()
        }
    }

    companion object {
        const val TAG = "WalletOptionsBottomSheet"
    }
}
