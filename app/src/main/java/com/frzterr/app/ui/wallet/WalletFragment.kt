package com.frzterr.app.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.frzterr.app.R
import com.frzterr.app.databinding.FragmentWalletBinding
import java.text.NumberFormat
import java.util.Locale

class WalletFragment : Fragment(R.layout.fragment_wallet) {

    private val viewModel: WalletViewModel by viewModels()
    private var _binding: FragmentWalletBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var assetAdapter: WalletAssetAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentWalletBinding.bind(view)
        
        setupUI()
        setupListeners()
        setupObservers()
    }
    
    private fun setupUI() {
        assetAdapter = WalletAssetAdapter { asset ->
            val address = viewModel.getAddressForSymbol(asset.symbol)
            val sheet = AssetDetailBottomSheet(asset, address)
            sheet.show(requireActivity().supportFragmentManager, "AssetDetailBottomSheet")
        }
        
        val rvAssets = binding.root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvAssets)
        rvAssets.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = assetAdapter
        }
    }

    private fun setupListeners() {
        binding.btnCreateWallet.setOnClickListener {
            viewModel.generateNewWallet()
        }

        binding.btnImportWallet.setOnClickListener {
            val bottomSheet = ImportWalletBottomSheet { mnemonic ->
                if (viewModel.importWallet(mnemonic)) {
                    Toast.makeText(requireContext(), "Wallet imported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Invalid seed phrase", Toast.LENGTH_SHORT).show()
                }
            }
            bottomSheet.show(childFragmentManager, "ImportWalletBottomSheet")
        }
        
        binding.btnSend.setOnClickListener {
            val assetList = viewModel.assets.value ?: emptyList()
            val sheet = SendBottomSheet(assetList)
            sheet.show(childFragmentManager, "SendBottomSheet")
        }
        
        binding.btnReceive.setOnClickListener {
            val wallet = (viewModel.walletState.value as? WalletViewModel.WalletState.Loaded)?.wallet ?: return@setOnClickListener
            val sheet = ReceiveBottomSheet(wallet.btcAddress, wallet.ethAddress, wallet.solAddress)
            sheet.show(childFragmentManager, "ReceiveBottomSheet")
        }
        
        binding.root.findViewById<android.view.View>(R.id.btnMenu).setOnClickListener {
            val optionsSheet = WalletOptionsBottomSheet {
                // Konfirmasi sebelum hapus
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Hapus Dompet")
                    .setMessage("Apakah Anda yakin ingin menghapus dompet ini? Tindakan ini tidak dapat dibatalkan jika Anda tidak memiliki frasa pemulihan.")
                    .setPositiveButton("Hapus") { _, _ ->
                        viewModel.deleteWallet()
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
            optionsSheet.show(requireActivity().supportFragmentManager, "WalletOptionsBottomSheet")
        }
    }

    private fun setupObservers() {
        viewModel.walletState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is WalletViewModel.WalletState.Empty -> {
                    binding.layoutEmptyState.visibility = View.VISIBLE
                    binding.layoutWalletInfo.visibility = View.GONE
                }
                is WalletViewModel.WalletState.Loaded -> {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.layoutWalletInfo.visibility = View.VISIBLE
                }
                is WalletViewModel.WalletState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        viewModel.createWalletEvent.observe(viewLifecycleOwner) { mnemonic ->
            mnemonic ?: return@observe  // sudah di-reset, skip

            // Reset SEBELUM show agar re-subscribe tidak trigger ulang
            viewModel.resetCreateWalletEvent()

            // Cegah duplikat jika sudah tampil
            if (requireActivity().supportFragmentManager.findFragmentByTag("CreateWalletBottomSheet") != null) return@observe

            val bottomSheet = CreateWalletBottomSheet(mnemonic) {
                viewModel.saveNewWallet(mnemonic)
            }
            bottomSheet.show(requireActivity().supportFragmentManager, "CreateWalletBottomSheet")
        }
        
        viewModel.assets.observe(viewLifecycleOwner) { assets ->
            assetAdapter.submitList(assets)
        }
        
        viewModel.totalBalance.observe(viewLifecycleOwner) { balance ->
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
            binding.root.findViewById<android.widget.TextView>(R.id.tvTotalBalance).text = currencyFormat.format(balance)
        }

        viewModel.isLoadingPrices.observe(viewLifecycleOwner) { loading ->
            val tvBalance = binding.root.findViewById<android.widget.TextView>(R.id.tvTotalBalance)
            val tvChange = binding.root.findViewById<android.widget.TextView>(R.id.tvBalanceChange)
            val density = resources.displayMetrics.density
            val shimmerColor = android.graphics.Color.parseColor("#25FFFFFF")

            if (loading) {
                applyShimmer(tvBalance, "  \$12,345.67  ", shimmerColor, density)
                applyShimmer(tvChange, "  +2.34%  ", shimmerColor, density)
            } else {
                // FIX: restore warna asli masing-masing
                clearShimmer(tvBalance, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary))
                clearShimmer(tvChange, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.wallet_green))
                // FIX: reset teks placeholder agar tidak tampil "+2.34%" palsu
                tvChange.text = "—"
            }

            assetAdapter.isLoading = loading
        }
    }

    /** Shimmer langsung pada TextView — ukuran selalu akurat sesuai teks */
    private fun applyShimmer(
        tv: android.widget.TextView,
        placeholder: String,
        color: Int,
        density: Float
    ) {
        tv.text = placeholder
        tv.setTextColor(android.graphics.Color.TRANSPARENT)
        val bg = android.graphics.drawable.GradientDrawable()
        bg.cornerRadius = 10f * density
        bg.setColor(color)
        tv.background = bg
        animatePulse(tv)
    }

    private fun clearShimmer(tv: android.widget.TextView, originalColor: Int) {
        tv.animate().cancel()
        tv.alpha = 1f
        tv.background = null
        tv.setTextColor(originalColor)  // ← FIX: restore warna asli
    }

    private fun animatePulse(view: android.view.View) {
        view.animate().cancel()
        view.alpha = 1f
        view.animate().alpha(0.3f).setDuration(700).withEndAction {
            if (view.background != null) {
                view.animate().alpha(1f).setDuration(700).withEndAction { animatePulse(view) }.start()
            }
        }.start()
    }
    
    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
