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
            sheet.show(parentFragmentManager, "AssetDetailBottomSheet")
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
            bottomSheet.show(parentFragmentManager, "ImportWalletBottomSheet")
        }
        
        binding.btnSend.setOnClickListener {
            val assetList = viewModel.assets.value ?: emptyList()
            val sheet = SendBottomSheet(assetList)
            sheet.show(parentFragmentManager, "SendBottomSheet")
        }
        
        binding.btnReceive.setOnClickListener {
            val wallet = (viewModel.walletState.value as? WalletViewModel.WalletState.Loaded)?.wallet ?: return@setOnClickListener
            val sheet = ReceiveBottomSheet(wallet.btcAddress, wallet.ethAddress, wallet.solAddress)
            sheet.show(parentFragmentManager, "ReceiveBottomSheet")
        }
        
        binding.root.findViewById<android.view.View>(R.id.btnDelete).setOnClickListener {
            viewModel.deleteWallet()
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
            if (parentFragmentManager.findFragmentByTag("CreateWalletBottomSheet") != null) return@observe

            val bottomSheet = CreateWalletBottomSheet(mnemonic) {
                viewModel.saveNewWallet(mnemonic)
            }
            bottomSheet.show(parentFragmentManager, "CreateWalletBottomSheet")
        }
        
        viewModel.assets.observe(viewLifecycleOwner) { assets ->
            assetAdapter.submitList(assets)
        }
        
        viewModel.totalBalance.observe(viewLifecycleOwner) { balance ->
            val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
            binding.root.findViewById<android.widget.TextView>(R.id.tvTotalBalance).text = currencyFormat.format(balance)
        }
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
