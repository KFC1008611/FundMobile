package com.example.fundmobile.ui.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.data.remote.MarketApi
import com.example.fundmobile.databinding.FragmentNewsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NewsFragment : Fragment() {

    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    private lateinit var newsAdapter: NewsAdapter
    private var autoRefreshJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        newsAdapter = NewsAdapter()
        binding.recyclerNews.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerNews.adapter = newsAdapter
        binding.recyclerNews.addItemDecoration(
            DividerItemDecoration(requireContext(), RecyclerView.VERTICAL)
        )

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { loadData() }

        loadData()
        startAutoRefresh()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            val news = runCatching { MarketApi.fetchNews() }.getOrElse { emptyList() }
            newsAdapter.submitList(news)
            binding.swipeRefresh.isRefreshing = false
            updateLastRefreshTime()
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(30_000)
                val news = runCatching { MarketApi.fetchNews() }.getOrElse { emptyList() }
                newsAdapter.submitList(news)
                updateLastRefreshTime()
            }
        }
    }

    private fun updateLastRefreshTime() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
        binding.tvLastUpdate.text = "更新于 ${sdf.format(Date())}"
    }

    override fun onDestroyView() {
        autoRefreshJob?.cancel()
        _binding = null
        super.onDestroyView()
    }
}
