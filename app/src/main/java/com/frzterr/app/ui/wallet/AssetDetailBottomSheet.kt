package com.frzterr.app.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.frzterr.app.R
import com.frzterr.app.ui.common.BaseCustomBottomSheet
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AssetDetailBottomSheet(
    private val asset: CryptoAsset,
    private val address: String
) : BaseCustomBottomSheet() {

    override fun getLayoutResId(): Int = R.layout.dialog_asset_detail

    // Back button tidak menutup sheet ini
    override val persistOnBack: Boolean = true

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val tabDays = listOf(1, 7, 30, 365)
    private val tabLabels = listOf("1D", "7D", "30D", "1Y")

    override fun onSheetCreated(view: View) {
        // Nonaktifkan drag agar interaksi chart tidak menyebabkan sheet mengambang/tertutup
        behavior?.isDraggable = false

        // Header
        view.findViewById<ImageView>(R.id.ivDetailIcon).setImageResource(asset.iconRes)
        view.findViewById<TextView>(R.id.tvDetailSymbol).text = asset.symbol
        view.findViewById<TextView>(R.id.tvDetailName).text = asset.name
        view.findViewById<TextView>(R.id.tvDetailPrice).text = currencyFormat.format(asset.price)

        val changeText = String.format("%+.2f%%", asset.change24h)
        val tvChange = view.findViewById<TextView>(R.id.tvDetailChange)
        tvChange.text = changeText
        tvChange.setTextColor(if (asset.change24h >= 0) Color.parseColor("#00E676") else Color.parseColor("#FF5252"))

        val balFormat = if (asset.balance < 0.001 && asset.balance > 0) "%.8f" else "%.4f"
        view.findViewById<TextView>(R.id.tvDetailBalance).text =
            String.format("${balFormat} ${asset.symbol}", asset.balance)
        view.findViewById<TextView>(R.id.tvDetailValue).text =
            currencyFormat.format(asset.balance * asset.price)

        // Address
        view.findViewById<TextView>(R.id.tvDetailAddress).text = address
        view.findViewById<ImageButton>(R.id.btnCopyAddress).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("${asset.symbol} Address", address))
            Toast.makeText(requireContext(), "${asset.symbol} address copied", Toast.LENGTH_SHORT).show()
        }

        // Chart setup
        val lineChart = view.findViewById<LineChart>(R.id.lineChart)
        setupChart(lineChart)

        // Tabs
        val tabTimeRange = view.findViewById<TabLayout>(R.id.tabTimeRange)
        tabLabels.forEach { tabTimeRange.addTab(tabTimeRange.newTab().setText(it)) }
        tabTimeRange.getTabAt(1)?.select()

        tabTimeRange.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { loadChart(view, tabDays[tab.position]) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        loadChart(view, 7)
    }

    private fun setupChart(lineChart: LineChart) {
        lineChart.apply {
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
                    override fun getFormattedValue(value: Float): String =
                        if (value >= 1000) "$${(value / 1000).toInt()}K" else "$${value.toInt()}"
                }
            }
        }
    }

    private fun loadChart(view: View, days: Int) {
        val pbChartLoading = view.findViewById<ProgressBar>(R.id.pbChartLoading)
        val lineChart = view.findViewById<LineChart>(R.id.lineChart)

        pbChartLoading.visibility = View.VISIBLE
        lineChart.visibility = View.INVISIBLE

        lifecycleScope.launch {
            val chartData = CryptoApiService.fetchChartData(asset.symbol, days)
            if (!isAdded) return@launch

            pbChartLoading.visibility = View.GONE
            lineChart.visibility = View.VISIBLE

            // Re-expand setelah konten berubah (chart load mengubah ukuran konten)
            behavior?.state = BottomSheetBehavior.STATE_EXPANDED

            if (chartData.isEmpty()) return@launch

            val entries = chartData.mapIndexed { index, (_, price) -> Entry(index.toFloat(), price) }
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

            val timestamps = chartData.map { it.first }
            val dateFormat = if (days <= 1) SimpleDateFormat("HH:mm", Locale.US)
            else SimpleDateFormat("MM/dd", Locale.US)

            lineChart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val idx = value.toInt().coerceIn(0, timestamps.size - 1)
                    return dateFormat.format(Date(timestamps[idx]))
                }
            }

            lineChart.data = LineData(dataSet)
            lineChart.invalidate()
            lineChart.animateX(500)
        }
    }

    companion object {
        const val TAG = "AssetDetailBottomSheet"
    }
}
