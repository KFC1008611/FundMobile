package com.example.fundmobile.ui.fund

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fundmobile.R
import com.example.fundmobile.data.local.PrefsStore
import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.NavHistoryEntry
import com.example.fundmobile.data.remote.FundApi
import com.example.fundmobile.databinding.ActivityFundDetailBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FundDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFundDetailBinding
    private var fundCode: String = ""
    private var fundName: String = ""
    private var currentLoadJob: Job? = null
    private val chinaZone = ZoneId.of("Asia/Shanghai")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    companion object {
        private const val EXTRA_FUND_CODE = "fund_code"
        private const val EXTRA_FUND_NAME = "fund_name"

        fun start(context: Context, code: String, name: String) {
            val intent = Intent(context, FundDetailActivity::class.java).apply {
                putExtra(EXTRA_FUND_CODE, code)
                putExtra(EXTRA_FUND_NAME, name)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PrefsStore(application)
        val isDark = prefs.loadDarkMode()
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark

        binding = ActivityFundDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        fundCode = intent.getStringExtra(EXTRA_FUND_CODE) ?: ""
        fundName = intent.getStringExtra(EXTRA_FUND_NAME) ?: ""

        binding.tvFundName.text = fundName
        binding.tvFundCode.text = fundCode
        binding.btnBack.setOnClickListener { finish() }

        setupChart()
        setupChipGroup()
        loadFundInfo()
        loadHistory()
    }

    private fun loadFundInfo() {
        val prefs = PrefsStore(application)
        val funds = prefs.loadFunds()
        val fund = funds.firstOrNull { it.code == fundCode }
        if (fund != null) {
            bindFundInfo(fund)
            bindHoldings(fund)
        }

        lifecycleScope.launch {
            runCatching {
                val freshFund = FundApi.fetchFundData(fundCode)
                bindFundInfo(freshFund)
                bindHoldings(freshFund)
            }
        }
    }

    private fun bindFundInfo(fund: FundData) {
        binding.tvFundName.text = fund.name
        binding.tvDetailNav.text = fund.dwjz ?: "--"
        binding.tvDetailDate.text = fund.jzrq ?: "--"

        val change = fund.gszzl ?: fund.zzl
        if (change != null) {
            val up = change >= 0
            binding.tvDetailChange.text = String.format("%+.2f%%", change)
            binding.tvDetailChange.setTextColor(
                ContextCompat.getColor(this, if (up) R.color.danger else R.color.success)
            )
        } else {
            binding.tvDetailChange.text = "--"
            binding.tvDetailChange.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
        }
    }

    private fun bindHoldings(fund: FundData) {
        if (fund.holdings.isNotEmpty()) {
            binding.tvHoldingsTitle.isVisible = true
            binding.recyclerHoldings.isVisible = true
            binding.recyclerHoldings.layoutManager = LinearLayoutManager(this)
            val adapter = StockHoldingAdapter()
            binding.recyclerHoldings.adapter = adapter
            adapter.submitList(fund.holdings.take(10))
        }
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            setNoDataText("加载中...")
            setNoDataTextColor(ContextCompat.getColor(this@FundDetailActivity, R.color.text_secondary))

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = ContextCompat.getColor(this@FundDetailActivity, R.color.text_secondary)
                textSize = 10f
                labelRotationAngle = 0f
                setLabelCount(4, true)
            }

            axisLeft.apply {
                textColor = ContextCompat.getColor(this@FundDetailActivity, R.color.text_secondary)
                textSize = 10f
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(this@FundDetailActivity, R.color.border)
                setDrawAxisLine(false)
            }

            axisRight.isEnabled = false
        }
    }

    private fun setupChipGroup() {
        binding.chip3m.isChecked = true

        binding.chipGroupRange.setOnCheckedStateChangeListener { _, checkedIds ->
            val months = when {
                checkedIds.contains(R.id.chip1m) -> 1L
                checkedIds.contains(R.id.chip3m) -> 3L
                checkedIds.contains(R.id.chip6m) -> 6L
                checkedIds.contains(R.id.chip1y) -> 12L
                checkedIds.contains(R.id.chipAll) -> -1L
                else -> 3L
            }
            loadAndDisplayChart(months)
        }
    }

    private fun loadHistory() {
        loadAndDisplayChart(3L)
    }

    /**
     * 每次切换时间范围时，根据 months 计算 sdate/edate 并重新调用 API。
     * months < 0 表示 "全部"，使用 maxPages 限制翻页数量（约3年数据）。
     */
    private fun loadAndDisplayChart(months: Long) {
        currentLoadJob?.cancel()
        currentLoadJob = lifecycleScope.launch {
            binding.chartLoading.isVisible = true
            binding.lineChart.clear()
            binding.lineChart.setNoDataText("加载中...")
            binding.lineChart.invalidate()

            val history = runCatching {
                val today = LocalDate.now(chinaZone)
                val edate = today.format(dateFmt)
                if (months < 0) {
                    // "全部" — 不传 sdate，用 maxPages 限制
                    FundApi.fetchFundNetHistory(
                        code = fundCode,
                        endDate = edate,
                        maxPages = FundApi.MAX_HISTORY_PAGES
                    )
                } else {
                    val sdate = today.minusMonths(months).format(dateFmt)
                    FundApi.fetchFundNetHistory(
                        code = fundCode,
                        startDate = sdate,
                        endDate = edate,
                        maxPages = Int.MAX_VALUE
                    )
                }
            }.getOrElse { emptyList() }

            binding.chartLoading.isVisible = false
            displayChart(history)
        }
    }

    private fun displayChart(history: List<NavHistoryEntry>) {
        if (history.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.setNoDataText("暂无历史数据")
            binding.lineChart.invalidate()
            return
        }

        val sorted = history.sortedBy { it.date }
        val dateLabels = sorted.map { it.date }
        val entries = sorted.mapIndexed { index, entry ->
            Entry(index.toFloat(), entry.nav.toFloat())
        }

        val dataSet = LineDataSet(entries, "净值").apply {
            color = ContextCompat.getColor(this@FundDetailActivity, R.color.primary)
            lineWidth = 1.8f
            setDrawCircles(false)
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(this@FundDetailActivity, R.color.primary)
            fillAlpha = 30
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.15f
            highLightColor = ContextCompat.getColor(this@FundDetailActivity, R.color.text_secondary)
        }

        binding.lineChart.apply {
            fitScreen()

            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val idx = value.toInt()
                    if (idx < 0 || idx >= dateLabels.size) return ""
                    val full = dateLabels[idx]
                    return if (full.length >= 10) full.substring(5) else full
                }
            }
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = (sorted.size - 1).toFloat()

            axisLeft.resetAxisMinimum()
            axisLeft.resetAxisMaximum()

            data = LineData(dataSet)
            notifyDataSetChanged()
            animateX(300)
            invalidate()
        }
    }
}
