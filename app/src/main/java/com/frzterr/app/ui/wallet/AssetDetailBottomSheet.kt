package com.frzterr.app.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.frzterr.app.databinding.DialogAssetDetailBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AssetDetailBottomSheet(
    private val asset: CryptoAsset,
    private val address: String
) : BottomSheetDialogFragment() {

    private var _binding: DialogAssetDetailBinding? = null
    private val binding get() = _binding!!

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val tabDays = listOf(1, 7, 30, 365)
    private val tabLabels = listOf("1D", "7D", "30D", "1Y")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAssetDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupTabs()
        setupChart()
        setupAddress()

        // Load chart 7D saat pertama kali dibuka
        loadChart(7)
    }

    private fun setupHeader() {
        binding.ivDetailIcon.setImageResource(asset.iconRes)
        binding.tvDetailSymbol.text = asset.symbol
        binding.tvDetailName.text = asset.name
        binding.tvDetailPrice.text = currencyFormat.format(asset.price)

        val changeText = String.format("%+.2f%%", asset.change24h)
        binding.tvDetailChange.text = changeText
        binding.tvDetailChange.setTextColor(
            if (asset.change24h >= 0) Color.parseColor("#00E676") else Color.parseColor("#FF5252")
        )

        // Balance info
        val balFormat = if (asset.balance < 0.001 && asset.balance > 0) "%.8f" else "%.4f"
        binding.tvDetailBalance.text = String.format("${balFormat} ${asset.symbol}", asset.balance)
        binding.tvDetailValue.text = currencyFormat.format(asset.balance * asset.price)
    }

    private fun setupTabs() {
        tabLabels.forEach { binding.tabTimeRange.addTab(binding.tabTimeRange.newTab().setText(it)) }
        // Default ke 7D (index 1)
        binding.tabTimeRange.getTabAt(1)?.select()

        binding.tabTimeRange.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                loadChart(tabDays[tab.position])
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupAddress() {
        binding.tvDetailAddress.text = address
        binding.btnCopyAddress.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("${asset.symbol} Address", address))
            Toast.makeText(context, "${asset.symbol} address copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.parseColor("#80FFFFFF")
                setDrawGridLines(false)
                setDrawAxisLine(false)
                granularity = 1f
                labelCount = 5
            }

            axisLeft.apply {
                textColor = Color.parseColor("#80FFFFFF")
                setDrawGridLines(true)
                gridColor = Color.parseColor("#20FFFFFF")
                setDrawAxisLine(false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value >= 1000) "$${(value / 1000).toInt()}K"
                        else "$${value.toInt()}"
                    }
                }
            }
        }
    }

    private fun loadChart(days: Int) {
        binding.pbChartLoading.visibility = View.VISIBLE
        binding.lineChart.visibility = View.INVISIBLE

        lifecycleScope.launch {
            val chartData = CryptoApiService.fetchChartData(asset.symbol, days)

            if (!isAdded) return@launch

            binding.pbChartLoading.visibility = View.GONE
            binding.lineChart.visibility = View.VISIBLE

            if (chartData.isEmpty()) return@launch

            val entries = chartData.mapIndexed { index, (_, price) ->
                Entry(index.toFloat(), price)
            }

            // Tentukan warna berdasarkan trend (naik/turun)
            val isUp = chartData.last().second >= chartData.first().second
            val lineColor = if (isUp) Color.parseColor("#00E676") else Color.parseColor("#FF5252")
            val chartFillColor = if (isUp) Color.parseColor("#2000E676") else Color.parseColor("#20FF5252")

            val dataSet = LineDataSet(entries, asset.symbol).apply {
                color = lineColor
                setDrawCircles(false)
                lineWidth = 2f
                setDrawFilled(true)
                fillColor = chartFillColor
                fillAlpha = 100
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity = 0.15f
            }

            // X-axis formatter: tampilkan tanggal sesungguhnya
            val timestamps = chartData.map { it.first }
            val dateFormat = if (days <= 1) SimpleDateFormat("HH:mm", Locale.US)
            else SimpleDateFormat("MM/dd", Locale.US)

            binding.lineChart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val idx = value.toInt().coerceIn(0, timestamps.size - 1)
                    return dateFormat.format(Date(timestamps[idx]))
                }
            }

            binding.lineChart.data = LineData(dataSet)
            binding.lineChart.invalidate()
            binding.lineChart.animateX(500)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as com.google.android.material.bottomsheet.BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }
        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
