package com.frzterr.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.frzterr.app.R
import com.google.android.material.imageview.ShapeableImageView

class PostImageAdapter(
    private val onImageClick: (Int, View) -> Unit
) : ListAdapter<String, PostImageAdapter.ViewHolder>(ImageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgPost: ShapeableImageView = itemView.findViewById(R.id.imgPost)
        private val shimmerLayout: com.facebook.shimmer.ShimmerFrameLayout = itemView.findViewById(R.id.shimmerLayout)

        fun bind(url: String) {
            // Set unique transition name (using URL)
            ViewCompat.setTransitionName(imgPost, url)
            
            // Reset state - Hide Stroke & Show Shimmer
            shimmerLayout.visibility = View.VISIBLE
            shimmerLayout.startShimmer()
            imgPost.strokeWidth = 0f // Hide stroke while loading
            
            imgPost.load(url) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ ->
                        shimmerLayout.stopShimmer()
                        shimmerLayout.visibility = View.GONE
                        imgPost.strokeWidth = 3f // Show 1dp stroke (3px approx) when loaded
                    },
                    onError = { _, _ ->
                        shimmerLayout.stopShimmer()
                        shimmerLayout.visibility = View.GONE
                        imgPost.strokeWidth = 0f // Keep hidden on error
                    }
                )
            }
            
            imgPost.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onImageClick(bindingAdapterPosition, imgPost)
                }
            }
        }
    }

    class ImageDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}
