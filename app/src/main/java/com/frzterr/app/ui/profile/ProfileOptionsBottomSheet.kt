package com.frzterr.app.ui.profile

import android.view.View
import android.widget.LinearLayout
import com.frzterr.app.R
import com.frzterr.app.ui.common.BaseCustomBottomSheet

class ProfileOptionsBottomSheet(
    private val onLogoutClick: () -> Unit,
    private val onVerificationClick: () -> Unit
) : BaseCustomBottomSheet() {

    override fun getLayoutResId(): Int = R.layout.bottom_sheet_profile_options

    override fun onSheetCreated(view: View) {
        view.findViewById<LinearLayout>(R.id.btnRequestVerification).setOnClickListener {
            dismiss()
            onVerificationClick()
        }
        view.findViewById<LinearLayout>(R.id.btnLogout).setOnClickListener {
            dismiss()
            onLogoutClick()
        }
    }

    companion object {
        const val TAG = "ProfileOptionsBottomSheet"
    }
}
