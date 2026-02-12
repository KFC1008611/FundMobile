package com.example.fundmobile.ui.fund

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.HoldingPosition
import com.example.fundmobile.databinding.ItemFundListRowBinding
import com.example.fundmobile.domain.PortfolioCalculator
import java.time.ZoneId
import java.time.ZonedDateTime

class FundAdapter(
    private val onFundClick: (FundData) -> Unit,
    private val onHoldingAction: (FundData) -> Unit
) : ListAdapter<FundData, FundAdapter.ListVH>(Diff) {

    private var holdings: Map<String, HoldingPosition> = emptyMap()
    private var isTradingDay: Boolean = true
    private var todayStr: String = ""
    private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")

    object Diff : DiffUtil.ItemCallback<FundData>() {
        override fun areItemsTheSame(oldItem: FundData, newItem: FundData): Boolean = oldItem.code == newItem.code
        override fun areContentsTheSame(oldItem: FundData, newItem: FundData): Boolean = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListVH {
        val binding = ItemFundListRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ListVH(binding, onFundClick, onHoldingAction)
    }

    override fun onBindViewHolder(holder: ListVH, position: Int) {
        val item = getItem(position)
        holder.bind(item, holdings[item.code], isTradingDay, todayStr)
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

    inner class ListVH(
        private val binding: ItemFundListRowBinding,
        private val onFundClick: (FundData) -> Unit,
        private val onHoldingAction: (FundData) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
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

            binding.root.setOnClickListener { onFundClick(item) }
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
}
