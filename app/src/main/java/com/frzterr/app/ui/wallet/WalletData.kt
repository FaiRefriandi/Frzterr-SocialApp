package com.frzterr.app.ui.wallet

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import wallet.core.jni.CoinType
import wallet.core.jni.HDWallet
import wallet.core.jni.Mnemonic

@Serializable
data class WalletInfo(
    val name: String = "Main Wallet",
    val seedPhrase: String,
    val btcAddress: String,
    val ethAddress: String,
    val solAddress: String,
    val createdAt: Long = System.currentTimeMillis()
)

object WalletLocalStore {
    private const val PREF_NAME = "flowzup_wallet_prefs"
    private const val KEY_WALLET_INFO = "wallet_info"

    private val json = Json { ignoreUnknownKeys = true }

    fun saveWallet(context: Context, wallet: WalletInfo) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = json.encodeToString(wallet)
        prefs.edit().putString(KEY_WALLET_INFO, jsonString).apply()
    }

    fun loadWallet(context: Context): WalletInfo? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_WALLET_INFO, null) ?: return null
        return try {
            json.decodeFromString<WalletInfo>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    fun clearWallet(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_WALLET_INFO).apply()
    }
}

/**
 * Helper object yang membungkus Trust Wallet Core API.
 * Menggantikan implementasi BIP39 manual dan mock address derivation.
 *
 * TWC JNI harus sudah diinisialisasi via WalletCore.init(context)
 * sebelum memanggil fungsi-fungsi di sini (dilakukan di WalletViewModel.init).
 */
object TrustWalletHelper {

    /**
     * Generate 12-word BIP39 mnemonic menggunakan Trust Wallet Core.
     * Strength 128 bits = 12 kata.
     */
    fun generateMnemonic(): String {
        val hdWallet = HDWallet(128, "")
        return hdWallet.mnemonic()
    }

    /**
     * Validasi apakah mnemonic valid sesuai standar BIP39.
     */
    fun isValidMnemonic(mnemonic: String): Boolean {
        return Mnemonic.isValid(mnemonic)
    }

    /**
     * Derive Ethereum (EVM) address dari mnemonic.
     * Juga digunakan untuk USDT (ERC-20) dan BNB (BEP-20).
     * Derivation path: m/44'/60'/0'/0/0
     */
    fun deriveEthAddress(mnemonic: String): String {
        val hdWallet = HDWallet(mnemonic, "")
        return hdWallet.getAddressForCoin(CoinType.ETHEREUM)
    }

    /**
     * Derive Bitcoin (Native SegWit / bech32) address dari mnemonic.
     * Derivation path: m/84'/0'/0'/0/0
     */
    fun deriveBtcAddress(mnemonic: String): String {
        val hdWallet = HDWallet(mnemonic, "")
        return hdWallet.getAddressForCoin(CoinType.BITCOIN)
    }

    /**
     * Derive Solana address dari mnemonic.
     * Derivation path: m/44'/501'/0'/0'
     */
    fun deriveSolAddress(mnemonic: String): String {
        val hdWallet = HDWallet(mnemonic, "")
        return hdWallet.getAddressForCoin(CoinType.SOLANA)
    }
}
