package com.example.fundmobile.ui.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fundmobile.R
import com.example.fundmobile.data.model.GoldChartPoint
import com.example.fundmobile.data.remote.MarketApi
import com.example.fundmobile.databinding.FragmentMetalsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class MetalsFragment : Fragment() {

    private var _binding: FragmentMetalsBinding? = null
    private val binding get() = _binding!!

    private lateinit var metalAdapter: MetalPriceAdapter
    private lateinit var historyAdapter: GoldHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMetalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        metalAdapter = MetalPriceAdapter()
        historyAdapter = GoldHistoryAdapter()

        binding.recyclerMetals.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMetals.adapter = metalAdapter

        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = historyAdapter

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { loadData() }

        setupChart()
        loadData()
    }

    private fun setupChart() {
        binding.lineChartGold.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            setNoDataText("加载中...")
            setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 10f
                setLabelCount(5, true)
            }

            axisLeft.apply {
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(requireContext(), R.color.border)
                setDrawAxisLine(false)
            }

            axisRight.isEnabled = false
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            binding.chartLoading.visibility = View.VISIBLE
            runCatching {
                val metalsDeferred = async { MarketApi.fetchRealTimeMetals() }
                val historyDeferred = async { MarketApi.fetchGoldHistory() }
                val chartDeferred = async { MarketApi.fetchGoldIntraday() }

                val metals = metalsDeferred.await()
                val history = historyDeferred.await()
                val chartData = chartDeferred.await()

                metalAdapter.submitList(metals)
                historyAdapter.submitList(history)
                displayGoldChart(chartData)
            }
            binding.swipeRefresh.isRefreshing = false
            binding.chartLoading.visibility = View.GONE
        }
    }

    private fun displayGoldChart(data: List<GoldChartPoint>) {
        if (data.isEmpty()) {
            binding.lineChartGold.clear()
            binding.lineChartGold.setNoDataText("暂无数据")
            binding.lineChartGold.invalidate()
            return
        }

        val entries = data.mapIndexed { index, point ->
            Entry(index.toFloat(), point.price.toFloat())
        }
        val dateLabels = data.map { it.date }

        val dataSet = LineDataSet(entries, "金价").apply {
            color = ContextCompat.getColor(requireContext(), R.color.primary)
            lineWidth = 1.8f
            setDrawCircles(false)
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(requireContext(), R.color.primary)
            fillAlpha = 30
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.15f
            highLightColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        }

        binding.lineChartGold.apply {
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val idx = value.toInt()
                    if (idx < 0 || idx >= dateLabels.size) return ""
                    return dateLabels[idx]
                }
            }

            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = (data.size - 1).toFloat()
            axisLeft.resetAxisMinimum()
            axisLeft.resetAxisMaximum()
            this.data = LineData(dataSet)
            notifyDataSetChanged()
            animateX(300)
            invalidate()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
