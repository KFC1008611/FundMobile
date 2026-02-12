package com.example.fundmobile.ui.pages

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.data.model.VolumeData
import com.example.fundmobile.databinding.ItemVolumeDataBinding

class VolumeAdapter : ListAdapter<VolumeData, VolumeAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<VolumeData>() {
        override fun areItemsTheSame(oldItem: VolumeData, newItem: VolumeData): Boolean = oldItem.date == newItem.date
        override fun areContentsTheSame(oldItem: VolumeData, newItem: VolumeData): Boolean = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemVolumeDataBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val binding: ItemVolumeDataBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VolumeData) {
            binding.tvDate.text = if (item.date.length >= 10) item.date.substring(5) else item.date
            binding.tvTotal.text = item.total
            binding.tvSh.text = item.sh
            binding.tvSz.text = item.sz
            binding.tvBj.text = item.bj
        }
    }
}
