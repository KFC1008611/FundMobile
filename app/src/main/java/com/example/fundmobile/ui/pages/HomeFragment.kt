package com.example.fundmobile.ui.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fundmobile.R
import com.example.fundmobile.databinding.FragmentHomeBinding
import com.example.fundmobile.ui.MainPagerAdapter
import com.example.fundmobile.ui.MainViewModel
import com.example.fundmobile.ui.sheets.AddFundBottomSheet
import com.example.fundmobile.ui.sheets.GroupManageBottomSheet
import com.example.fundmobile.ui.sheets.SettingsBottomSheet
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()
    
    private lateinit var pagerAdapter: MainPagerAdapter
    private var tabMediator: TabLayoutMediator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        binding.fabAddFund.setOnClickListener {
            AddFundBottomSheet().show(childFragmentManager, "addFund")
        }

        binding.btnRefresh.setOnClickListener { viewModel.refreshAll() }
        binding.btnSettings.setOnClickListener { SettingsBottomSheet().show(childFragmentManager, "settings") }
        binding.btnSort.setOnClickListener { showSortMenu() }
        binding.tvAppName.setOnLongClickListener {
            GroupManageBottomSheet().show(childFragmentManager, "groups")
            true
        }

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in 0 until pagerAdapter.itemCount) {
                    viewModel.setCurrentTab(pagerAdapter.getTab(position).id)
                }
            }
        })

        observeUi()
    }

    private fun observeUi() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tabs.collect { tabs ->
                        val selectedId = viewModel.currentTab.value
                        pagerAdapter.submitTabs(tabs)
                        tabMediator?.detach()
                        tabMediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                            tab.text = tabs[position].title
                        }.also { it.attach() }

                        val index = tabs.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
                        if (binding.viewPager.currentItem != index) {
                            binding.viewPager.setCurrentItem(index, false)
                        }
                    }
                }

                launch {
                    viewModel.refreshing.collect { refreshing ->
                        binding.refreshLine.isVisible = refreshing
                    }
                }

                launch {
                    viewModel.autoRefreshError.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showSortMenu() {
        val popup = PopupMenu(requireContext(), binding.btnSort)
        popup.menu.add(Menu.NONE, 1, 1, getString(R.string.sort_default))
        popup.menu.add(Menu.NONE, 2, 2, getString(R.string.sort_by_name))
        popup.menu.add(Menu.NONE, 3, 3, getString(R.string.sort_by_yield))
        popup.menu.add(Menu.NONE, 4, 4, getString(R.string.sort_by_holding))
        popup.menu.add(Menu.NONE, 5, 5, "切换升降序")
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                1 -> viewModel.setSort("default", "desc")
                2 -> viewModel.setSort("name", viewModel.sortOrder.value)
                3 -> viewModel.setSort("yield", viewModel.sortOrder.value)
                4 -> viewModel.setSort("holding", viewModel.sortOrder.value)
                5 -> viewModel.setSort(viewModel.sortBy.value, if (viewModel.sortOrder.value == "asc") "desc" else "asc")
            }
            true
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        tabMediator?.detach()
        tabMediator = null
    }
}
