package com.frzterr.app.ui.aichat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import com.frzterr.app.R

class ModelSelectorAdapter(
    private val models: List<NvidiaModel>,
    private var selectedModelId: String,
    private val onModelSelected: (NvidiaModel) -> Unit
) : RecyclerView.Adapter<ModelSelectorAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView   = view.findViewById(R.id.ivModelIcon)
        val tvName: TextView    = view.findViewById(R.id.tvModelName)
        val tvDesc: TextView    = view.findViewById(R.id.tvModelDesc)
        val ivCheck: ImageView  = view.findViewById(R.id.ivCheckmark)
        val tvChip: TextView    = view.findViewById(R.id.tvSpeedChip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]
        holder.tvName.text = model.displayName
        holder.tvDesc.text = model.description

        // Set icon brand spesifik setiap model (tint ungu diatur di XML)
        holder.ivIcon.setImageResource(model.iconRes)

        // Speed chip
        if (model.speedLabel.isNotEmpty()) {
            holder.tvChip.text = model.speedLabel
            holder.tvChip.visibility = View.VISIBLE
            // Tint background pill sesuai warna kecepatan
            val drawable = DrawableCompat.wrap(holder.tvChip.background.mutate())
            DrawableCompat.setTint(drawable, Color.parseColor(model.speedColor))
            holder.tvChip.background = drawable
        } else {
            holder.tvChip.visibility = View.GONE
        }

        holder.ivCheck.visibility =
            if (model.modelId == selectedModelId) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener {
            selectedModelId = model.modelId
            notifyDataSetChanged()
            onModelSelected(model)
        }
    }

    override fun getItemCount() = models.size
}
