package com.example.fundmobile.ui.pages

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.data.model.MetalPrice
import com.example.fundmobile.databinding.ItemMetalPriceBinding

class MetalPriceAdapter : ListAdapter<MetalPrice, MetalPriceAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<MetalPrice>() {
        override fun areItemsTheSame(oldItem: MetalPrice, newItem: MetalPrice): Boolean = oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: MetalPrice, newItem: MetalPrice): Boolean = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMetalPriceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val binding: ItemMetalPriceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MetalPrice) {
            binding.tvMetalName.text = item.name
            binding.tvMetalPrice.text = item.price
            binding.tvUnit.text = item.unit
            binding.tvChangePct.text = item.changePct
            binding.tvChangePct.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (item.changePct.startsWith("-")) R.color.success else R.color.danger
                )
            )
            binding.tvOpenPrice.text = item.openPrice
            binding.tvHighPrice.text = item.highPrice
            binding.tvLowPrice.text = item.lowPrice
            binding.tvPrevClose.text = item.prevClose
            binding.tvUpdateTime.text = item.updateTime
        }
    }
}
