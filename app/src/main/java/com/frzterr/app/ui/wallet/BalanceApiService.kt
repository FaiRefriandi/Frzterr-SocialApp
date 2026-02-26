package com.frzterr.app.ui.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Service untuk mengambil saldo on-chain dari berbagai blockchain explorer.
 *
 * - ETH/USDT/BNB → Etherscan API
 * - BTC          → Blockstream.info API (tanpa API key)
 * - SOL          → Solana RPC (tanpa API key)
 */
object BalanceApiService {

    private const val ETHERSCAN_API_KEY = "KE4GA4IKIA39Z84DIWP4TG6MRB5ZQTSK7F"

    // ─── ETH Balance ───────────────────────────────────────────────────────────

    /**
     * Fetch saldo ETH dalam unit ETH (bukan Wei).
     */
    suspend fun fetchEthBalance(address: String): Double = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.etherscan.io/api?module=account&action=balance" +
                    "&address=$address&tag=latest&apikey=$ETHERSCAN_API_KEY"
            val json = JSONObject(URL(url).readText())
            if (json.getString("status") == "1") {
                val weiStr = json.getString("result")
                weiStr.toBigDecimalOrNull()?.let { it / 1e18.toBigDecimal() }?.toDouble() ?: 0.0
            } else 0.0
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        }
    }

    /**
     * Fetch saldo token ERC-20 (USDT contract: 0xdAC17F958D2ee523a2206206994597C13D831ec7).
     * Returns saldo dalam unit token (USDT = 6 desimal).
     */
    suspend fun fetchUsdtBalance(address: String): Double = withContext(Dispatchers.IO) {
        try {
            val contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
            val url = "https://api.etherscan.io/api?module=account&action=tokenbalance" +
                    "&contractaddress=$contractAddress&address=$address" +
                    "&tag=latest&apikey=$ETHERSCAN_API_KEY"
            val json = JSONObject(URL(url).readText())
            if (json.getString("status") == "1") {
                val rawStr = json.getString("result")
                // USDT has 6 decimals
                rawStr.toBigDecimalOrNull()?.let { it / 1e6.toBigDecimal() }?.toDouble() ?: 0.0
            } else 0.0
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        }
    }

    /**
     * Fetch saldo BNB menggunakan BSC Etherscan API.
     */
    suspend fun fetchBnbBalance(address: String): Double = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.bscscan.com/api?module=account&action=balance" +
                    "&address=$address&tag=latest&apikey=$ETHERSCAN_API_KEY"
            val json = JSONObject(URL(url).readText())
            if (json.getString("status") == "1") {
                val weiStr = json.getString("result")
                weiStr.toBigDecimalOrNull()?.let { it / 1e18.toBigDecimal() }?.toDouble() ?: 0.0
            } else 0.0
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        }
    }

    // ─── BTC Balance ───────────────────────────────────────────────────────────

    /**
     * Fetch saldo BTC via Blockstream.info API (tanpa API key, gratis).
     * Returns saldo dalam unit BTC.
     */
    suspend fun fetchBtcBalance(address: String): Double = withContext(Dispatchers.IO) {
        try {
            val url = "https://blockstream.info/api/address/$address"
            val json = JSONObject(URL(url).readText())
            val chainStats = json.getJSONObject("chain_stats")
            val funded = chainStats.getLong("funded_txo_sum")
            val spent = chainStats.getLong("spent_txo_sum")
            val satoshis = funded - spent
            satoshis / 1e8 // Satoshi → BTC
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        }
    }

    // ─── SOL Balance ───────────────────────────────────────────────────────────

    /**
     * Fetch saldo SOL via Solana mainnet RPC (tanpa API key).
     * Returns saldo dalam unit SOL.
     */
    suspend fun fetchSolBalance(address: String): Double = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://api.mainnet-beta.solana.com")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val body = """{"jsonrpc":"2.0","id":1,"method":"getBalance","params":["$address"]}"""
            connection.outputStream.write(body.toByteArray())

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val lamports = json.getJSONObject("result").getLong("value")
            lamports / 1e9 // Lamports → SOL
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        }
    }
}
