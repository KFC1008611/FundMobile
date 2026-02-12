package com.example.fundmobile.ui.pages

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.data.model.NewsItem
import com.example.fundmobile.databinding.ItemNewsBinding

class NewsAdapter : ListAdapter<NewsItem, NewsAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<NewsItem>() {
        override fun areItemsTheSame(oldItem: NewsItem, newItem: NewsItem): Boolean {
            return oldItem.time == newItem.time && oldItem.content == newItem.content
        }

        override fun areContentsTheSame(oldItem: NewsItem, newItem: NewsItem): Boolean = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val binding: ItemNewsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NewsItem) {
            binding.tvTime.text = item.time
            binding.tvContent.text = item.content

            if (item.evaluate.isNotBlank()) {
                val ctx = binding.root.context
                val corner = 4f * ctx.resources.displayMetrics.density

                binding.tvEvaluate.visibility = View.VISIBLE
                binding.tvEvaluate.text = item.evaluate

                when (item.evaluate) {
                    "利好" -> {
                        binding.tvEvaluate.setTextColor(ContextCompat.getColor(ctx, R.color.danger))
                        binding.tvEvaluate.background = GradientDrawable().apply {
                            setColor(ContextCompat.getColor(ctx, R.color.danger_10))
                            cornerRadius = corner
                        }
                    }

                    "利空" -> {
                        binding.tvEvaluate.setTextColor(ContextCompat.getColor(ctx, R.color.success))
                        binding.tvEvaluate.background = GradientDrawable().apply {
                            setColor(ContextCompat.getColor(ctx, R.color.success_10))
                            cornerRadius = corner
                        }
                    }

                    else -> {
                        binding.tvEvaluate.setTextColor(ContextCompat.getColor(ctx, R.color.warning))
                        binding.tvEvaluate.background = GradientDrawable().apply {
                            setColor(ContextCompat.getColor(ctx, R.color.warning_10))
                            cornerRadius = corner
                        }
                    }
                }
            } else {
                binding.tvEvaluate.visibility = View.GONE
            }
        }
    }
}
