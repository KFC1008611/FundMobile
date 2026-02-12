package com.example.fundmobile.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.fundmobile.R
import com.example.fundmobile.databinding.ActivityMainBinding
import com.example.fundmobile.ui.pages.HomeFragment
import com.example.fundmobile.ui.pages.IndicesFragment
import com.example.fundmobile.ui.pages.MetalsFragment
import com.example.fundmobile.ui.pages.NewsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(application) }

    private val homeFragment = HomeFragment()
    private val metalsFragment = MetalsFragment()
    private val indicesFragment = IndicesFragment()
    private val newsFragment = NewsFragment()

    private var activeFragment: Fragment = homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        val targetNightMode = if (viewModel.darkMode.value) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
            AppCompatDelegate.setDefaultNightMode(targetNightMode)
        }

        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(insets.left, insets.top, insets.right, 0)
            binding.bottomNav.setPadding(0, 0, 0, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        if (savedInstanceState == null) {
            setupFragments()
        } else {
            val fm = supportFragmentManager
            activeFragment = fm.findFragmentByTag("home") ?: homeFragment
        }
        
        setupBottomNav()
    }

    private fun setupFragments() {
        supportFragmentManager.beginTransaction()
            .add(R.id.nav_host_fragment, homeFragment, "home")
            .add(R.id.nav_host_fragment, metalsFragment, "metals")
            .hide(metalsFragment)
            .add(R.id.nav_host_fragment, indicesFragment, "indices")
            .hide(indicesFragment)
            .add(R.id.nav_host_fragment, newsFragment, "news")
            .hide(newsFragment)
            .commit()
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_home -> homeFragment
                R.id.nav_metals -> metalsFragment
                R.id.nav_indices -> indicesFragment
                R.id.nav_news -> newsFragment
                else -> return@setOnItemSelectedListener false
            }
            switchFragment(target)
            true
        }
    }

    private fun switchFragment(target: Fragment) {
        if (activeFragment == target) return
        
        if (!target.isAdded) {
        }

        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .show(target)
            .commit()
        activeFragment = target
    }
}
