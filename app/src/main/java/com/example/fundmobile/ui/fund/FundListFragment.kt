package com.example.fundmobile.ui.fund

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.databinding.FragmentFundListBinding
import com.example.fundmobile.ui.MainViewModel
import com.example.fundmobile.ui.sheets.HoldingActionBottomSheet
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

class FundListFragment : Fragment() {

    private var _binding: FragmentFundListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: FundAdapter
    private val tabType: String by lazy { requireArguments().getString(ARG_TAB_TYPE) ?: "all" }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFundListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.currentTab.value != tabType) {
            viewModel.setCurrentTab(tabType)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FundAdapter(
            onFundClick = { fund ->
                FundDetailActivity.start(requireContext(), fund.code, fund.name)
            },
            onHoldingAction = { fund ->
                HoldingActionBottomSheet.newInstance(fund.code, fund.name)
                    .show(parentFragmentManager, "holdingAction")
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), RecyclerView.VERTICAL)
        )

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { viewModel.refreshAll() }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            binding.recyclerView.canScrollVertically(-1)
        }

        observeUi()
    }

    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentTab.collect { tab ->
                        if (tabType != tab) return@collect
                        adapter.submitList(viewModel.displayFunds.value)
                    }
                }

                launch {
                    viewModel.displayFunds.collect { funds ->
                        if (viewModel.currentTab.value != tabType) return@collect
                        adapter.submitList(funds)
                        binding.emptyState.isVisible = funds.isEmpty()
                    }
                }

                launch {
                    viewModel.refreshing.collect {
                        binding.swipeRefresh.isRefreshing = it
                    }
                }

                launch {
                    viewModel.holdings.collect {
                        applyAdapterState()
                    }
                }

                launch {
                    viewModel.isTradingDay.collect {
                        applyAdapterState()
                    }
                }

                launch {
                    viewModel.portfolioSummary.collect { summary ->
                        if (summary == null) {
                            binding.cardSummary.isVisible = false
                        } else {
                            binding.cardSummary.isVisible = true
                            binding.tvTotalAssets.text = "¥${"%.2f".format(summary.totalAsset)}"
                            binding.tvDailyProfit.text = String.format("%+.2f", summary.totalProfitToday)
                            binding.tvHoldingReturn.text = String.format("%+.2f%%", summary.returnRate)
                            binding.tvDailyProfit.setTextColor(
                                requireContext().getColor(if (summary.totalProfitToday >= 0) R.color.danger else R.color.success)
                            )
                            binding.tvHoldingReturn.setTextColor(
                                requireContext().getColor(if (summary.returnRate >= 0) R.color.danger else R.color.success)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyAdapterState() {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
        adapter.submitHoldings(viewModel.holdings.value, viewModel.isTradingDay.value, today)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TAB_TYPE = "arg_tab_type"

        fun newInstance(tabType: String): FundListFragment {
            return FundListFragment().apply {
                arguments = Bundle().apply { putString(ARG_TAB_TYPE, tabType) }
            }
        }
    }
}
