package com.example.fundmobile.ui.fund

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.HoldingPosition
import com.example.fundmobile.databinding.ItemFundCardBinding
import com.example.fundmobile.databinding.ItemFundListRowBinding
import com.example.fundmobile.domain.PortfolioCalculator

class FundAdapter(
    private val onToggleCollapse: (String) -> Unit,
    private val onHoldingAction: (FundData) -> Unit
) : ListAdapter<FundData, RecyclerView.ViewHolder>(Diff) {

    private var viewMode: String = "card"
    private var collapsed: Set<String> = emptySet()
    private var holdings: Map<String, HoldingPosition> = emptyMap()
    private var isTradingDay: Boolean = true
    private var todayStr: String = ""

    object Diff : DiffUtil.ItemCallback<FundData>() {
        override fun areItemsTheSame(oldItem: FundData, newItem: FundData): Boolean = oldItem.code == newItem.code
        override fun areContentsTheSame(oldItem: FundData, newItem: FundData): Boolean = oldItem == newItem
    }

    override fun getItemViewType(position: Int): Int = if (viewMode == "list") TYPE_LIST else TYPE_CARD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_LIST) {
            val binding = ItemFundListRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ListVH(binding, onHoldingAction)
        } else {
            val binding = ItemFundCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            CardVH(binding, onToggleCollapse, onHoldingAction)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is CardVH) {
            holder.bind(item, collapsed.contains(item.code), holdings[item.code], isTradingDay, todayStr)
        } else if (holder is ListVH) {
            holder.bind(item, holdings[item.code], isTradingDay, todayStr)
        }
    }

    fun submitViewMode(mode: String) {
        if (viewMode == mode) return
        viewMode = mode
        notifyDataSetChanged()
    }

    fun submitCollapsed(value: Set<String>) {
        collapsed = value
        notifyDataSetChanged()
    }

    fun submitHoldings(value: Map<String, HoldingPosition>, tradingDay: Boolean, today: String) {
        holdings = value
        isTradingDay = tradingDay
        todayStr = today
        notifyDataSetChanged()
    }

    fun getFundAt(position: Int): FundData? {
        return currentList.getOrNull(position)
    }

    inner class ListVH(private val binding: ItemFundListRowBinding, private val onHoldingAction: (FundData) -> Unit) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FundData, holding: HoldingPosition?, tradingDay: Boolean, today: String) {
            binding.tvFundName.text = item.name
            binding.tvFundCode.text = item.code
            binding.tvNav.text = item.dwjz ?: "--"

            val change = item.gszzl ?: item.zzl ?: 0.0
            val up = change >= 0
            binding.tvChange.text = String.format("%+.2f%%", change)
            binding.tvChange.setTextColor(ContextCompat.getColor(binding.root.context, if (up) R.color.danger else R.color.success))

            val profit = PortfolioCalculator.getHoldingProfit(item, holding, tradingDay, today)
            binding.tvHoldingProfit.text = profit?.profitTotal?.let { String.format("%+.2f", it) } ?: "--"
            binding.tvHoldingProfit.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if ((profit?.profitTotal ?: 0.0) >= 0) R.color.danger else R.color.success
                )
            )

            binding.root.setOnClickListener(null)
            
            binding.root.setOnLongClickListener {
                onHoldingAction(item)
                true
            }
        }
    }

    class CardVH(
        private val binding: ItemFundCardBinding,
        private val onToggleCollapse: (String) -> Unit,
        private val onHoldingAction: (FundData) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val stockAdapter = StockHoldingAdapter().also {
            binding.recyclerHoldings.layoutManager = LinearLayoutManager(binding.root.context)
            binding.recyclerHoldings.adapter = it
        }

        fun bind(
            item: FundData,
            collapsed: Boolean,
            holding: HoldingPosition?,
            tradingDay: Boolean,
            today: String
        ) {
            binding.tvFundName.text = item.name
            binding.tvFundCode.text = item.code
            binding.tvNav.text = item.dwjz ?: "--"
            binding.tvEstimatedNav.text = item.gsz ?: "--"
            binding.tvNavDate.text = item.jzrq.orEmpty()
            binding.tvEstimatedTime.text = item.gztime.orEmpty()

            val change = item.gszzl ?: item.zzl ?: 0.0
            val up = change >= 0
            binding.tvChange.text = String.format("%+.2f%%", change)
            binding.tvChange.setTextColor(ContextCompat.getColor(binding.root.context, if (up) R.color.danger else R.color.success))
            binding.tvChange.setBackgroundResource(if (up) R.drawable.bg_badge_up else R.drawable.bg_badge_down)

            binding.btnExpand.setImageResource(if (collapsed) R.drawable.ic_expand else R.drawable.ic_collapse)
            binding.btnExpand.setOnClickListener { onToggleCollapse(item.code) }

            val showHoldings = !collapsed && item.holdings.isNotEmpty()
            binding.recyclerHoldings.isVisible = showHoldings
            if (showHoldings) stockAdapter.submitList(item.holdings.take(10)) else stockAdapter.submitList(emptyList())

            val profit = PortfolioCalculator.getHoldingProfit(item, holding, tradingDay, today)
            binding.layoutHoldingInfo.isVisible = profit != null
            if (profit != null && holding != null) {
                binding.tvHoldingShares.text = "份额 ${"%.2f".format(holding.share)}"
                binding.tvHoldingValue.text = "市值 ${"%.2f".format(profit.amount)}"
                binding.tvHoldingProfit.text = "${"%+.2f".format(profit.profitTotal ?: 0.0)}"
                binding.tvHoldingProfit.setTextColor(
                    ContextCompat.getColor(binding.root.context, if ((profit.profitTotal ?: 0.0) >= 0) R.color.danger else R.color.success)
                )
            }

            binding.root.setOnClickListener(null)
            
            binding.root.setOnLongClickListener {
                onHoldingAction(item)
                true
            }
        }
    }

    companion object {
        const val TYPE_CARD = 1
        const val TYPE_LIST = 2
    }
}
