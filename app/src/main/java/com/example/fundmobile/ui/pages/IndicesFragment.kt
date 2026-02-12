package com.example.fundmobile.ui.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fundmobile.R
import com.example.fundmobile.data.model.ShangHaiMinute
import com.example.fundmobile.data.remote.MarketApi
import com.example.fundmobile.databinding.FragmentIndicesBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class IndicesFragment : Fragment() {

    private var _binding: FragmentIndicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var indexAdapter: IndexAdapter
    private lateinit var volumeAdapter: VolumeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIndicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        indexAdapter = IndexAdapter()
        volumeAdapter = VolumeAdapter()

        binding.recyclerIndices.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerIndices.adapter = indexAdapter

        binding.recyclerVolume.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerVolume.adapter = volumeAdapter

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { loadData() }

        setupChart()
        loadData()
    }

    private fun setupChart() {
        binding.lineChartShanghai.apply {
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
                val indicesDeferred = async { MarketApi.fetchGlobalIndices() }
                val minuteDeferred = async { MarketApi.fetchShanghaiMinute() }
                val volumeDeferred = async { MarketApi.fetchVolumeData() }

                val indices = indicesDeferred.await()
                val minuteData = minuteDeferred.await()
                val volumeData = volumeDeferred.await()

                indexAdapter.submitList(indices)
                volumeAdapter.submitList(volumeData)
                displayShanghaiChart(minuteData)
            }
            binding.swipeRefresh.isRefreshing = false
            binding.chartLoading.visibility = View.GONE
        }
    }

    private fun displayShanghaiChart(data: List<ShangHaiMinute>) {
        if (data.isEmpty()) {
            binding.lineChartShanghai.clear()
            binding.lineChartShanghai.setNoDataText("暂无数据")
            binding.lineChartShanghai.invalidate()
            return
        }

        val entries = data.mapIndexed { index, point ->
            Entry(index.toFloat(), point.price.toFloatOrNull() ?: 0f)
        }
        val timeLabels = data.map { it.time }

        val dataSet = LineDataSet(entries, "上证指数").apply {
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

        binding.lineChartShanghai.apply {
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val idx = value.toInt()
                    if (idx < 0 || idx >= timeLabels.size) return ""
                    return timeLabels[idx]
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
