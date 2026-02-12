package com.example.fundmobile.ui

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.fundmobile.ui.fund.FundListFragment

class MainPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    private val tabs = mutableListOf<TabItem>()

    fun submitTabs(newTabs: List<TabItem>) {
        tabs.clear()
        tabs.addAll(newTabs)
        notifyDataSetChanged()
    }

    fun getTab(position: Int): TabItem = tabs[position]

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return FundListFragment.newInstance(tabs[position].id)
    }

    override fun getItemId(position: Int): Long {
        return tabs[position].id.hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.id.hashCode().toLong() == itemId }
    }
}
