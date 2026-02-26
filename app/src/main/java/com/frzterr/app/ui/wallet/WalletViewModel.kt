package com.frzterr.app.ui.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.frzterr.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val _walletState = MutableLiveData<WalletState>()
    val walletState: LiveData<WalletState> = _walletState

    private val _createWalletEvent = MutableLiveData<String?>()
    val createWalletEvent: LiveData<String?> = _createWalletEvent

    private val _assets = MutableLiveData<List<CryptoAsset>>()
    val assets: LiveData<List<CryptoAsset>> = _assets

    private val _totalBalance = MutableLiveData<Double>()
    val totalBalance: LiveData<Double> = _totalBalance

    private val _isLoadingPrices = MutableLiveData<Boolean>(false)
    val isLoadingPrices: LiveData<Boolean> = _isLoadingPrices

    // Job untuk polling harga realtime
    private var pricePollJob: Job? = null

    sealed class WalletState {
        object Empty : WalletState()
        data class Loaded(val wallet: WalletInfo) : WalletState()
        data class Error(val message: String) : WalletState()
    }

    companion object {
        init {
            // Load native Trust Wallet Core JNI library
            System.loadLibrary("TrustWalletCore")
        }
        private const val PRICE_POLL_INTERVAL_MS = 60_000L // 60 detik
    }

    init {
        loadWallet()
    }

    fun loadWallet() {
        viewModelScope.launch {
            val walletInfo = WalletLocalStore.loadWallet(getApplication())
            if (walletInfo != null) {
                _walletState.value = WalletState.Loaded(walletInfo)
                // Build asset list dulu dengan harga default, lalu fetch realtime
                buildAssetList(walletInfo, emptyMap())
                startRealtimeUpdates(walletInfo)
            } else {
                _walletState.value = WalletState.Empty
            }
        }
    }

    /**
     * Mulai polling harga & saldo secara realtime setiap 60 detik.
     */
    private fun startRealtimeUpdates(wallet: WalletInfo) {
        pricePollJob?.cancel()
        pricePollJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                withContext(Dispatchers.Main) { _isLoadingPrices.value = true }
                try {
                    // Fetch harga + saldo on-chain secara paralel
                    val prices = CryptoApiService.fetchPrices()
                    val ethBal = BalanceApiService.fetchEthBalance(wallet.ethAddress)
                    val usdtBal = BalanceApiService.fetchUsdtBalance(wallet.ethAddress)
                    val bnbBal = BalanceApiService.fetchBnbBalance(wallet.ethAddress)
                    val btcBal = BalanceApiService.fetchBtcBalance(wallet.btcAddress)
                    val solBal = BalanceApiService.fetchSolBalance(wallet.solAddress)

                    val balances = mapOf(
                        "BTC" to btcBal,
                        "ETH" to ethBal,
                        "SOL" to solBal,
                        "USDT" to usdtBal,
                        "BNB" to bnbBal
                    )

                    withContext(Dispatchers.Main) {
                        _isLoadingPrices.value = false
                        buildAssetList(wallet, prices, balances)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { _isLoadingPrices.value = false }
                    e.printStackTrace()
                }
                delay(PRICE_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Bangun daftar asset dengan data harga dan saldo yang diberikan.
     */
    private fun buildAssetList(
        @Suppress("UNUSED_PARAMETER") wallet: WalletInfo,
        prices: Map<String, Pair<Double, Double>>,
        balances: Map<String, Double> = emptyMap()
    ) {
        fun price(symbol: String) = prices[symbol]?.first ?: getDefaultPrice(symbol)
        fun change(symbol: String) = prices[symbol]?.second ?: 0.0
        fun bal(symbol: String) = balances[symbol] ?: 0.0

        val assetList = listOf(
            CryptoAsset("btc", "BTC", "Bitcoin", bal("BTC"), price("BTC"), change("BTC"), R.drawable.ic_btc, R.color.wallet_accent),
            CryptoAsset("eth", "ETH", "Ethereum", bal("ETH"), price("ETH"), change("ETH"), R.drawable.ic_eth, R.color.wallet_accent),
            CryptoAsset("sol", "SOL", "Solana", bal("SOL"), price("SOL"), change("SOL"), R.drawable.ic_sol, R.color.wallet_accent_light),
            CryptoAsset("usdt", "USDT", "Tether", bal("USDT"), price("USDT"), change("USDT"), R.drawable.ic_usdt, R.color.wallet_green),
            CryptoAsset("bnb", "BNB", "BNB Chain", bal("BNB"), price("BNB"), change("BNB"), R.drawable.ic_bnb, R.color.wallet_accent)
        )

        _assets.value = assetList
        _totalBalance.value = assetList.sumOf { it.balance * it.price }
    }

    /** Harga fallback jika API gagal (tidak dipakai saat realtime berhasil) */
    private fun getDefaultPrice(symbol: String) = when (symbol) {
        "BTC" -> 96326.50; "ETH" -> 2714.61; "SOL" -> 170.33
        "USDT" -> 1.00;    "BNB" -> 655.34;  else -> 0.0
    }

    fun generateNewWallet() {
        viewModelScope.launch(Dispatchers.IO) {
            val mnemonic = TrustWalletHelper.generateMnemonic()
            withContext(Dispatchers.Main) { _createWalletEvent.value = mnemonic }
        }
    }

    fun resetCreateWalletEvent() {
        _createWalletEvent.value = null
    }

    fun saveNewWallet(mnemonic: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val btcAddress = TrustWalletHelper.deriveBtcAddress(mnemonic)
            val ethAddress = TrustWalletHelper.deriveEthAddress(mnemonic)
            val solAddress = TrustWalletHelper.deriveSolAddress(mnemonic)
            val walletInfo = WalletInfo(seedPhrase = mnemonic, btcAddress = btcAddress, ethAddress = ethAddress, solAddress = solAddress)
            WalletLocalStore.saveWallet(getApplication(), walletInfo)
            withContext(Dispatchers.Main) {
                _walletState.value = WalletState.Loaded(walletInfo)
                buildAssetList(walletInfo, emptyMap())
                startRealtimeUpdates(walletInfo)
                _createWalletEvent.value = null
            }
        }
    }

    fun importWallet(mnemonic: String): Boolean {
        if (!TrustWalletHelper.isValidMnemonic(mnemonic)) return false
        viewModelScope.launch(Dispatchers.IO) {
            val btcAddress = TrustWalletHelper.deriveBtcAddress(mnemonic)
            val ethAddress = TrustWalletHelper.deriveEthAddress(mnemonic)
            val solAddress = TrustWalletHelper.deriveSolAddress(mnemonic)
            val walletInfo = WalletInfo(seedPhrase = mnemonic, btcAddress = btcAddress, ethAddress = ethAddress, solAddress = solAddress)
            WalletLocalStore.saveWallet(getApplication(), walletInfo)
            withContext(Dispatchers.Main) {
                _walletState.value = WalletState.Loaded(walletInfo)
                buildAssetList(walletInfo, emptyMap())
                startRealtimeUpdates(walletInfo)
            }
        }
        return true
    }

    fun deleteWallet() {
        pricePollJob?.cancel()
        WalletLocalStore.clearWallet(getApplication())
        _walletState.value = WalletState.Empty
        _assets.value = emptyList()
        _totalBalance.value = 0.0
    }

    /**
     * Ambil address wallet untuk coin tertentu (untuk ditampilkan di detail sheet).
     */
    fun getAddressForSymbol(symbol: String): String {
        val wallet = (_walletState.value as? WalletState.Loaded)?.wallet ?: return ""
        return when (symbol) {
            "BTC" -> wallet.btcAddress
            "SOL" -> wallet.solAddress
            else -> wallet.ethAddress // ETH, USDT, BNB semua pakai EVM address
        }
    }

    override fun onCleared() {
        super.onCleared()
        pricePollJob?.cancel()
    }
}
