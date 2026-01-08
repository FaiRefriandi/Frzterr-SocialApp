package com.frzterr.app.ui.profile

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import coil.load
import com.frzterr.app.R
import com.google.android.material.imageview.ShapeableImageView

class AvatarPreviewDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_AVATAR_URL = "arg_avatar_url"
        const val TAG = "AvatarPreviewDialogFragment"

        fun newInstance(avatarUrl: String?): AvatarPreviewDialogFragment {
            val fragment = AvatarPreviewDialogFragment()
            val args = Bundle()
            args.putString(ARG_AVATAR_URL, avatarUrl)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use a translucent theme to fix initial black flash
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_avatar_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imgAvatarPreview: ShapeableImageView = view.findViewById(R.id.imgAvatarPreview)
        val btnClose: ImageView = view.findViewById(R.id.btnClose)
        
        // Apply window insets for close button (safe area)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(btnClose) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val params = v.layoutParams as ViewGroup.MarginLayoutParams
            // Increase buffer to 32dp to safely clear status bar
            params.topMargin = bars.top + (32 * resources.displayMetrics.density).toInt()
            v.layoutParams = params
            insets
        }

        // Load avatar
        val avatarUrl = arguments?.getString(ARG_AVATAR_URL)
        imgAvatarPreview.load(avatarUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_user_placeholder)
            error(R.drawable.ic_user_placeholder)
        }

        // Close on button click
        btnClose.setOnClickListener {
            dismiss()
        }

        // Close on background tap
        view.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#CC000000"))) // 80% opacity
            attributes.windowAnimations = R.style.DialogAnimation_Zoom
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this, false)
        }
    }
}
