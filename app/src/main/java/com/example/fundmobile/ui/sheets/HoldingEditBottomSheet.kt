package com.example.fundmobile.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.example.fundmobile.R
import com.example.fundmobile.data.model.HoldingPosition
import com.example.fundmobile.databinding.SheetHoldingEditBinding
import com.example.fundmobile.ui.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HoldingEditBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetHoldingEditBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private val fundCode: String by lazy { requireArguments().getString(ARG_CODE).orEmpty() }
    private val fundName: String by lazy { requireArguments().getString(ARG_NAME).orEmpty() }

    override fun getTheme(): Int = R.style.ThemeOverlay_FundMobile_BottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetHoldingEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fund = viewModel.funds.value.firstOrNull { it.code == fundCode }
        val nav = fund?.gsz?.toDoubleOrNull() ?: fund?.dwjz?.toDoubleOrNull() ?: 0.0
        val holding = viewModel.holdings.value[fundCode]

        binding.tvCurrentNav.text = "$fundName 当前净值: ${"%.4f".format(nav)}"
        if (holding != null) {
            binding.etShareCount.setText("%.2f".format(holding.share))
            binding.etCostPrice.setText("%.4f".format(holding.cost))
            val amount = holding.share * nav
            val profit = (nav - holding.cost) * holding.share
            binding.etHoldingAmount.setText("%.2f".format(amount))
            binding.etHoldingProfit.setText("%.2f".format(profit))
        }

        binding.tabMode.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                val amountMode = tab?.position == 0
                binding.layoutAmountMode.isVisible = amountMode
                binding.layoutShareMode.isVisible = !amountMode
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) = Unit
        })

        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnSave.setOnClickListener {
            val amountMode = binding.tabMode.selectedTabPosition == 0
            val position = if (amountMode) {
                val amount = binding.etHoldingAmount.text?.toString()?.toDoubleOrNull()
                val profit = binding.etHoldingProfit.text?.toString()?.toDoubleOrNull() ?: 0.0
                if (amount == null || amount <= 0 || nav <= 0.0) {
                    null
                } else {
                    val share = amount / nav
                    val cost = nav - (profit / share)
                    HoldingPosition(share = share, cost = cost)
                }
            } else {
                val share = binding.etShareCount.text?.toString()?.toDoubleOrNull()
                val cost = binding.etCostPrice.text?.toString()?.toDoubleOrNull()
                if (share == null || cost == null || share <= 0 || cost <= 0) null else HoldingPosition(share, cost)
            }

            viewModel.saveHolding(fundCode, position)
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_CODE = "arg_code"
        private const val ARG_NAME = "arg_name"

        fun newInstance(code: String, name: String): HoldingEditBottomSheet {
            return HoldingEditBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_CODE, code)
                    putString(ARG_NAME, name)
                }
            }
        }
    }
}
