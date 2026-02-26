package com.frzterr.app.ui.wallet

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val binding = ItemWalletAssetBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AssetViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AssetViewHolder(
        private val binding: ItemWalletAssetBinding,
        private val onItemClick: (CryptoAsset) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        private val decimalFormat = DecimalFormat("#,##0.####")

        fun bind(asset: CryptoAsset) {
            binding.root.setOnClickListener { onItemClick(asset) }
            
            binding.ivAssetIcon.setImageResource(asset.iconRes)
            binding.tvAssetSymbol.text = asset.symbol
            binding.tvAssetName.text = asset.name
            
            binding.tvAssetBalance.text = decimalFormat.format(asset.balance)
            binding.tvAssetValue.text = currencyFormat.format(asset.balance * asset.price)

            // Price and Change formatting
            val priceText = currencyFormat.format(asset.price)
            val changeText = String.format("%.2f%%", asset.change24h)
            val changeColor = if (asset.change24h >= 0) "#00E676" else "#FF5252"
            
            val fullPriceText = "$priceText $changeText"
            val spannable = SpannableString(fullPriceText)
            
            val startIndex = fullPriceText.indexOf(changeText)
            if (startIndex >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor(changeColor)),
                    startIndex,
                    fullPriceText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.tvAssetPrice.text = spannable
        }
    }

    class AssetDiffCallback : DiffUtil.ItemCallback<CryptoAsset>() {
        override fun areItemsTheSame(oldItem: CryptoAsset, newItem: CryptoAsset): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CryptoAsset, newItem: CryptoAsset): Boolean {
            return oldItem == newItem
        }
    }
}
