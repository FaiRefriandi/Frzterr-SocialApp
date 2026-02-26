package com.frzterr.app.ui.wallet

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.frzterr.app.databinding.ItemWalletAssetBinding
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

class WalletAssetAdapter(
    private val onItemClick: (CryptoAsset) -> Unit
) : ListAdapter<CryptoAsset, WalletAssetAdapter.AssetViewHolder>(AssetDiffCallback()) {

    var isLoading: Boolean = false
        set(value) {
            val wasLoading = field
            field = value
            if (wasLoading != value) notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val binding = ItemWalletAssetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AssetViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position), isLoading)
    }

    class AssetViewHolder(
        private val binding: ItemWalletAssetBinding,
        private val onItemClick: (CryptoAsset) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        private val decimalFormat = DecimalFormat("#,##0.####")

        // Warna shimmer & radius corner yang sama dengan text view height
        private val shimmerColor = Color.parseColor("#25FFFFFF")
        private val cornerRadius get() = 4f * binding.root.context.resources.displayMetrics.density

        fun bind(asset: CryptoAsset, loading: Boolean) {
            binding.root.setOnClickListener { onItemClick(asset) }

            // Icon, nama, symbol SELALU tampil normal
            binding.ivAssetIcon.setImageResource(asset.iconRes)
            binding.tvAssetSymbol.text = asset.symbol
            binding.tvAssetName.text = asset.name

            if (loading) {
                // Teks placeholder dengan ukuran representatif → skeleton pas-pasan dengan teksnya
                applyShimmer(binding.tvAssetPrice, "  \$96,326.00 +2.22%  ")
                applyShimmer(binding.tvAssetBalance, "  0.00000  ")
                applyShimmer(binding.tvAssetValue, "  \$0.00  ")
            } else {
                val ctx = binding.root.context
                // FIX: restore masing-masing warna aslinya
                clearShimmer(binding.tvAssetPrice, ctx.getColor(com.frzterr.app.R.color.icon_inactive))
                clearShimmer(binding.tvAssetBalance, ctx.getColor(com.frzterr.app.R.color.text_primary))
                clearShimmer(binding.tvAssetValue, ctx.getColor(com.frzterr.app.R.color.icon_inactive))

                binding.tvAssetBalance.text = decimalFormat.format(asset.balance)
                binding.tvAssetValue.text = currencyFormat.format(asset.balance * asset.price)

                val priceText = currencyFormat.format(asset.price)
                val changeText = String.format("%+.2f%%", asset.change24h)
                val changeColor = if (asset.change24h >= 0) "#00E676" else "#FF5252"
                val fullText = "$priceText $changeText"
                val spannable = SpannableString(fullText)
                val idx = fullText.indexOf(changeText)
                if (idx >= 0) {
                    spannable.setSpan(
                        ForegroundColorSpan(Color.parseColor(changeColor)),
                        idx, fullText.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                binding.tvAssetPrice.text = spannable
            }
        }

        private fun applyShimmer(tv: TextView, placeholder: String) {
            tv.text = placeholder
            tv.setTextColor(Color.TRANSPARENT)
            val bg = GradientDrawable()
            bg.cornerRadius = 10f * binding.root.context.resources.displayMetrics.density
            bg.setColor(shimmerColor)
            tv.background = bg
            pulse(tv)
        }

        /** Hapus shimmer, kembalikan ke normal */
        private fun clearShimmer(tv: TextView, originalColor: Int) {
            tv.animate().cancel()
            tv.alpha = 1f
            tv.background = null
            tv.setTextColor(originalColor)  // ← FIX: restore warna asli
        }

        private fun pulse(view: View) {
            view.animate().cancel()
            view.alpha = 1f
            view.animate().alpha(0.35f).setDuration(700).withEndAction {
                if (view.background != null) {
                    view.animate().alpha(1f).setDuration(700).withEndAction { pulse(view) }.start()
                }
            }.start()
        }
    }

    class AssetDiffCallback : DiffUtil.ItemCallback<CryptoAsset>() {
        override fun areItemsTheSame(old: CryptoAsset, new: CryptoAsset) = old.id == new.id
        override fun areContentsTheSame(old: CryptoAsset, new: CryptoAsset) = old == new
    }
}
