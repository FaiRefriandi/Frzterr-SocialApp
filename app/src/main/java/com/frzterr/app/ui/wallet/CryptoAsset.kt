package com.frzterr.app.ui.wallet

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes

data class CryptoAsset(
    val id: String,
    val symbol: String,
    val name: String,
    val balance: Double,
    val price: Double,
    val change24h: Double,
    @DrawableRes val iconRes: Int,
    @ColorRes val colorRes: Int
)
