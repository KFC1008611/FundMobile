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
import java.time.ZoneId
import java.time.ZonedDateTime

class FundAdapter(
    private val onToggleCollapse: (String) -> Unit,
    private val onHoldingAction: (FundData) -> Unit
) : ListAdapter<FundData, RecyclerView.ViewHolder>(Diff) {

    private var viewMode: String = "card"
    private var collapsed: Set<String> = emptySet()
    private var holdings: Map<String, HoldingPosition> = emptyMap()
    private var isTradingDay: Boolean = true
    private var todayStr: String = ""
    private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")

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
            val display = resolveDisplayQuote(item, tradingDay, today)
            binding.tvNav.text = display.navText

            val change = display.change
            if (change == null) {
                binding.tvChange.text = "--"
                binding.tvChange.setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_secondary))
            } else {
                val up = change >= 0
                binding.tvChange.text = String.format("%+.2f%%", change)
                binding.tvChange.setTextColor(ContextCompat.getColor(binding.root.context, if (up) R.color.danger else R.color.success))
            }

            val profit = PortfolioCalculator.getHoldingProfit(item, holding, tradingDay, today)
            binding.tvHoldingProfit.text = profit?.profitTotal?.let { String.format("%+.2f", it) } ?: "--"
            val holdingColor = when {
                profit?.profitTotal == null -> R.color.text_secondary
                profit.profitTotal >= 0 -> R.color.danger
                else -> R.color.success
            }
            binding.tvHoldingProfit.setTextColor(ContextCompat.getColor(binding.root.context, holdingColor))

            binding.root.setOnClickListener(null)
            
            binding.root.setOnLongClickListener {
                onHoldingAction(item)
                true
            }
        }
    }

    inner class CardVH(
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

            val display = resolveDisplayQuote(item, tradingDay, today)
            val change = display.change
            if (change == null) {
                binding.tvChange.text = "--"
                binding.tvChange.setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_secondary))
                binding.tvChange.setBackgroundResource(R.drawable.bg_badge)
            } else {
                val up = change >= 0
                binding.tvChange.text = String.format("%+.2f%%", change)
                binding.tvChange.setTextColor(ContextCompat.getColor(binding.root.context, if (up) R.color.danger else R.color.success))
                binding.tvChange.setBackgroundResource(if (up) R.drawable.bg_badge_up else R.drawable.bg_badge_down)
            }

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

    private data class DisplayQuote(
        val navText: String,
        val change: Double?
    )

    private fun resolveDisplayQuote(item: FundData, tradingDay: Boolean, today: String): DisplayQuote {
        val isAfter9 = ZonedDateTime.now(chinaZone).hour >= 9
        val hasTodayData = item.jzrq == today
        val shouldUseValuation = tradingDay && isAfter9 && !hasTodayData

        if (!shouldUseValuation || item.noValuation) {
            return DisplayQuote(
                navText = item.dwjz ?: "--",
                change = item.zzl ?: item.gszzl
            )
        }

        if (item.estPricedCoverage > 0.05 && item.estGsz != null) {
            return DisplayQuote(
                navText = String.format("%.4f", item.estGsz),
                change = item.estGszzl
            )
        }

        return DisplayQuote(
            navText = item.gsz ?: item.dwjz ?: "--",
            change = item.gszzl ?: item.zzl
        )
    }

    companion object {
        const val TYPE_CARD = 1
        const val TYPE_LIST = 2
    }
}
