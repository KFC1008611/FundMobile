package com.example.fundmobile.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fundmobile.R
import com.example.fundmobile.databinding.ActivityMainBinding
import com.example.fundmobile.ui.sheets.AddFundBottomSheet
import com.example.fundmobile.ui.sheets.GroupManageBottomSheet
import com.example.fundmobile.ui.sheets.SettingsBottomSheet
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(application) }
    private lateinit var pagerAdapter: MainPagerAdapter
    private var tabMediator: TabLayoutMediator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 应用保存的主题设置
        AppCompatDelegate.setDefaultNightMode(
            if (viewModel.darkMode.value) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        
        // 启用 edge-to-edge 显示模式
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置系统栏内边距
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        binding.fabAddFund.setOnClickListener {
            AddFundBottomSheet().show(supportFragmentManager, "addFund")
        }

        binding.btnRefresh.setOnClickListener { viewModel.refreshAll() }
        binding.btnSettings.setOnClickListener { SettingsBottomSheet().show(supportFragmentManager, "settings") }
        binding.btnSort.setOnClickListener { showSortMenu() }
        binding.btnViewMode.setOnClickListener { toggleViewMode() }
        binding.tvAppName.setOnLongClickListener {
            GroupManageBottomSheet().show(supportFragmentManager, "groups")
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                    viewModel.viewMode.collect { mode ->
                        binding.btnViewMode.setImageResource(
                            if (mode == "card") R.drawable.ic_card_view else R.drawable.ic_list_view
                        )
                    }
                }
            }
        }
    }

    private fun toggleViewMode() {
        viewModel.setViewMode(if (viewModel.viewMode.value == "card") "list" else "card")
    }

    private fun showSortMenu() {
        val popup = PopupMenu(this, binding.btnSort)
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
}
