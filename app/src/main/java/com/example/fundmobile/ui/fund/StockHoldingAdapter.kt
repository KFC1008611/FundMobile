package com.example.fundmobile.ui.fund

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.data.model.StockHolding
import com.example.fundmobile.databinding.ItemStockHoldingBinding

class StockHoldingAdapter : ListAdapter<StockHolding, StockHoldingAdapter.HoldingVH>(Diff) {

    object Diff : DiffUtil.ItemCallback<StockHolding>() {
        override fun areItemsTheSame(oldItem: StockHolding, newItem: StockHolding): Boolean = oldItem.code == newItem.code
        override fun areContentsTheSame(oldItem: StockHolding, newItem: StockHolding): Boolean = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HoldingVH {
        val binding = ItemStockHoldingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HoldingVH(binding)
    }

    override fun onBindViewHolder(holder: HoldingVH, position: Int) = holder.bind(getItem(position))

    class HoldingVH(private val binding: ItemStockHoldingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StockHolding) {
            binding.tvStockName.text = item.name
            binding.tvStockCode.text = item.code
            binding.tvWeight.text = item.weight
            val change = item.change ?: 0.0
            val up = change >= 0
            binding.tvChange.text = String.format("%+.2f%%", change)
            binding.tvChange.setTextColor(
                ContextCompat.getColor(binding.root.context, if (up) R.color.danger else R.color.success)
            )
        }
    }
}
