package com.example.fundmobile.ui.pages

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.data.model.MarketIndex
import com.example.fundmobile.databinding.ItemMarketIndexBinding

class IndexAdapter : ListAdapter<MarketIndex, IndexAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<MarketIndex>() {
        override fun areItemsTheSame(oldItem: MarketIndex, newItem: MarketIndex): Boolean = oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: MarketIndex, newItem: MarketIndex): Boolean = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMarketIndexBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val binding: ItemMarketIndexBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MarketIndex) {
            binding.tvIndexName.text = item.name
            binding.tvIndexValue.text = item.value
            binding.tvIndexChange.text = item.changePct
            binding.tvIndexChange.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (item.changePct.startsWith("-")) R.color.success else R.color.danger
                )
            )
        }
    }
}
