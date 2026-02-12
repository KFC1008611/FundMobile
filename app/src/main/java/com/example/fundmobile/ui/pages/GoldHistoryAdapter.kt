package com.example.fundmobile.ui.pages

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.data.model.GoldHistory
import com.example.fundmobile.databinding.ItemGoldHistoryBinding

class GoldHistoryAdapter : ListAdapter<GoldHistory, GoldHistoryAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<GoldHistory>() {
        override fun areItemsTheSame(oldItem: GoldHistory, newItem: GoldHistory): Boolean = oldItem.date == newItem.date

        override fun areContentsTheSame(oldItem: GoldHistory, newItem: GoldHistory): Boolean = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGoldHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val binding: ItemGoldHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GoldHistory) {
            binding.tvDate.text = item.date
            binding.tvChinaGoldPrice.text = item.chinaGoldPrice
            binding.tvChowTaiFookPrice.text = item.chowTaiFookPrice
            binding.tvChinaGoldChange.text = item.chinaGoldChange
            binding.tvChowTaiFookChange.text = item.chowTaiFookChange

            val chinaColor = if (item.chinaGoldChange.startsWith("-")) R.color.success else R.color.danger
            val chowTaiFookColor = if (item.chowTaiFookChange.startsWith("-")) R.color.success else R.color.danger

            binding.tvChinaGoldChange.setTextColor(ContextCompat.getColor(binding.root.context, chinaColor))
            binding.tvChowTaiFookChange.setTextColor(ContextCompat.getColor(binding.root.context, chowTaiFookColor))
        }
    }
}
