package com.frzterr.app.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Service untuk mengambil data harga dan chart dari CoinGecko API (gratis, tanpa API key).
 */
object CryptoApiService {

    // Mapping simbol → CoinGecko coin ID
    private val coinGeckoIds = mapOf(
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "SOL" to "solana",
        "USDT" to "tether",
        "BNB" to "binancecoin"
    )

    /**
     * Fetch harga realtime semua asset sekaligus dari CoinGecko.
     * Returns map: coinId → (harga dalam USD, persentase 24h change)
     */
    suspend fun fetchPrices(): Map<String, Pair<Double, Double>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, Pair<Double, Double>>()
        try {
            val ids = coinGeckoIds.values.joinToString(",")
            val urlStr = "https://api.coingecko.com/api/v3/simple/price?ids=$ids&vs_currencies=usd&include_24hr_change=true"
            val response = URL(urlStr).readText()
            val json = JSONObject(response)

            coinGeckoIds.forEach { (symbol, geckoId) ->
                if (json.has(geckoId)) {
                    val coinObj = json.getJSONObject(geckoId)
                    val price = coinObj.optDouble("usd", 0.0)
                    val change = coinObj.optDouble("usd_24h_change", 0.0)
                    result[symbol] = Pair(price, change)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    /**
     * Fetch data chart historis dari CoinGecko untuk ditampilkan di bottom sheet.
     * Returns list of (timestamp, price) untuk LineChart.
     * days: 1, 7, 30, 365
     */
    suspend fun fetchChartData(symbol: String, days: Int): List<Pair<Long, Float>> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Pair<Long, Float>>()
        val geckoId = coinGeckoIds[symbol] ?: return@withContext result
        try {
            val urlStr = "https://api.coingecko.com/api/v3/coins/$geckoId/market_chart?vs_currency=usd&days=$days"
            val response = URL(urlStr).readText()
            val json = JSONObject(response)
            val prices = json.getJSONArray("prices")
            for (i in 0 until prices.length()) {
                val point = prices.getJSONArray(i)
                val timestamp = point.getLong(0)
                val price = point.getDouble(1).toFloat()
                result.add(Pair(timestamp, price))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }
}
