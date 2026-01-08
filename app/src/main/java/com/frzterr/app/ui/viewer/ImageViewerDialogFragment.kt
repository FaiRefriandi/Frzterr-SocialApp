package com.frzterr.app.ui.viewer

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.ChangeClipBounds
import androidx.transition.ChangeImageTransform
import androidx.transition.ChangeTransform
import androidx.transition.Fade
import androidx.transition.TransitionInflater
import androidx.transition.TransitionSet
import androidx.viewpager2.widget.ViewPager2
import coil.load
import coil.request.CachePolicy
import coil.size.Precision
import com.frzterr.app.R
import com.github.chrisbanes.photoview.PhotoView

class ImageViewerDialogFragment : Fragment() {

    companion object {
        private const val ARG_IMAGES = "arg_images"
        private const val ARG_POSITION = "arg_position"
        const val TAG = "ImageViewerDialogFragment"

        fun newInstance(images: List<String>, position: Int): ImageViewerDialogFragment {
            val fragment = ImageViewerDialogFragment()
            val args = Bundle()
            args.putStringArrayList(ARG_IMAGES, ArrayList(images))
            args.putInt(ARG_POSITION, position)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 🚀 ENTER TRANSITION (Zoom In)
        val enterTransitionSet = androidx.transition.TransitionSet().apply {
            addTransition(androidx.transition.ChangeBounds())
            addTransition(androidx.transition.ChangeTransform())
            addTransition(androidx.transition.ChangeImageTransform())
            addTransition(androidx.transition.ChangeClipBounds())
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        
        // 🚀 RETURN TRANSITION (Zoom Out) - Faster for smoothness
        val returnTransitionSet = androidx.transition.TransitionSet().apply {
            addTransition(androidx.transition.ChangeBounds())
            addTransition(androidx.transition.ChangeTransform())
            addTransition(androidx.transition.ChangeImageTransform())
            addTransition(androidx.transition.ChangeClipBounds())
            duration = 250
            interpolator = android.view.animation.AccelerateInterpolator()
        }
        
        sharedElementEnterTransition = enterTransitionSet
        sharedElementReturnTransition = returnTransitionSet
        
        // Use Fade only for entering backdrop, don't use for exit to avoid flicker/stutter
        enterTransition = Fade().apply { duration = 200 }
        exitTransition = null 
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_image_viewer, container, false)
        view.setBackgroundColor(Color.BLACK)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val images = arguments?.getStringArrayList(ARG_IMAGES) ?: emptyList<String>()
        val startPosition = arguments?.getInt(ARG_POSITION, 0) ?: 0
        
        val vpFullScreen: ViewPager2 = view.findViewById(R.id.vpFullScreen)
        val btnClose: ImageView = view.findViewById(R.id.btnClose)
        
        // Adjust Close button position to avoid status bar overlap
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(btnClose) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val params = v.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = bars.top + (16 * resources.displayMetrics.density).toInt()
            v.layoutParams = params
            insets
        }

        // Postpone transition until image is loaded
        postponeEnterTransition()

        if (images.isNotEmpty()) {
            val adapter = FullScreenImageAdapter(images)
            vpFullScreen.adapter = adapter
            vpFullScreen.setCurrentItem(startPosition, false)
        } else {
             startPostponedEnterTransition()
        }

        btnClose.setOnClickListener {
            dismiss()
        }
    }
    
    // Use popBackStack for proper navigation flow
    fun dismiss() {
        if (isAdded) {
            findNavController().popBackStack()
        }
    }

    // Inner Adapter Class
    inner class FullScreenImageAdapter(private val hiddenImages: List<String>) : RecyclerView.Adapter<FullScreenImageAdapter.ImageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image_full, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            holder.bind(hiddenImages[position])
        }

        override fun getItemCount(): Int = hiddenImages.size

        inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val photoView: PhotoView = itemView.findViewById(R.id.photoView)

            fun bind(url: String) {
                // Set transition name to match source
                androidx.core.view.ViewCompat.setTransitionName(photoView, url)
                
                photoView.load(url) {
                    crossfade(false)
                    allowHardware(true)
                    // Memory optimization for full screen images to avoid stuttering on return
                    bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                    precision(Precision.EXACT)
                    diskCachePolicy(CachePolicy.ENABLED)
                    
                    listener(
                        onSuccess = { _, _ ->
                            startPostponedEnterTransition()
                        },
                        onError = { _, _ ->
                            startPostponedEnterTransition()
                        }
                    )
                }
            }
        }
    }
}
