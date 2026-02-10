package com.example.fundmobile.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.activityViewModels
import com.example.fundmobile.R
import com.example.fundmobile.databinding.SheetSettingsBinding
import com.example.fundmobile.ui.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun getTheme(): Int = R.style.ThemeOverlay_FundMobile_BottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isDark = viewModel.darkMode.value
        binding.switchDarkMode.isChecked = isDark
        binding.tvThemeDesc.setText(if (isDark) R.string.theme_dark else R.string.theme_light)

        binding.switchDarkMode.setOnCheckedChangeListener { _, checked ->
            binding.tvThemeDesc.setText(if (checked) R.string.theme_dark else R.string.theme_light)
            viewModel.setDarkMode(checked)
            val nightMode = if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            dismissAllowingStateLoss()
            activity?.window?.decorView?.postDelayed({
                AppCompatDelegate.setDefaultNightMode(nightMode)
            }, 300)
        }

        binding.etRefreshSeconds.setText((viewModel.refreshMs.value / 1000L).toString())
        binding.btnMinus.setOnClickListener { adjust(-5) }
        binding.btnPlus.setOnClickListener { adjust(5) }
        binding.btnSave.setOnClickListener {
            val value = binding.etRefreshSeconds.text?.toString()?.toIntOrNull() ?: 30
            viewModel.setRefreshInterval(value)
            dismiss()
        }
    }

    private fun adjust(delta: Int) {
        val current = binding.etRefreshSeconds.text?.toString()?.toIntOrNull() ?: 30
        val next = (current + delta).coerceIn(5, 300)
        binding.etRefreshSeconds.setText(next.toString())
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
