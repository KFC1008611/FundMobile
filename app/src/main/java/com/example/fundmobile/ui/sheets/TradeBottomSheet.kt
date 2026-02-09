package com.example.fundmobile.ui.sheets

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.fundmobile.R
import com.example.fundmobile.databinding.SheetTradeBinding
import com.example.fundmobile.ui.MainViewModel
import com.example.fundmobile.ui.TradeData
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class TradeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetTradeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private val fundCode by lazy { requireArguments().getString(ARG_CODE).orEmpty() }
    private val fundName by lazy { requireArguments().getString(ARG_NAME).orEmpty() }
    private val tradeType by lazy { requireArguments().getString(ARG_TYPE).orEmpty() }

    private var selectedDate: LocalDate = LocalDate.now()

    override fun getTheme(): Int = R.style.ThemeOverlay_FundMobile_BottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetTradeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvFundNameCode.text = "$fundName  ($fundCode)"

        val isBuy = tradeType == "buy"
        binding.layoutBuy.isVisible = isBuy
        binding.layoutSell.isVisible = !isBuy

        binding.btnBuyDate.text = selectedDate.format(DateTimeFormatter.ISO_DATE)
        binding.btnSellDate.text = selectedDate.format(DateTimeFormatter.ISO_DATE)
        binding.btnBuyDate.setOnClickListener { pickDate(true) }
        binding.btnSellDate.setOnClickListener { pickDate(false) }

        val holding = viewModel.holdings.value[fundCode]
        binding.btnQuarter.setOnClickListener { setSellFraction(holding?.share ?: 0.0, 0.25) }
        binding.btnThird.setOnClickListener { setSellFraction(holding?.share ?: 0.0, 1.0 / 3.0) }
        binding.btnHalf.setOnClickListener { setSellFraction(holding?.share ?: 0.0, 0.5) }
        binding.btnAll.setOnClickListener { setSellFraction(holding?.share ?: 0.0, 1.0) }

        binding.groupSellFeeMode.setOnCheckedChangeListener { _, checkedId ->
            binding.etSellFee.hint = if (checkedId == binding.rbFeeRate.id) getString(R.string.sell_fee) else "卖出手续费金额"
        }

        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnConfirm.setOnClickListener {
            submitTrade(isBuy)
        }

        loadReferenceNav()
    }

    private fun pickDate(isBuy: Boolean) {
        val now = selectedDate
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                val text = selectedDate.format(DateTimeFormatter.ISO_DATE)
                if (isBuy) binding.btnBuyDate.text = text else binding.btnSellDate.text = text
                updateConfirmSummary(isBuy)
            },
            now.year,
            now.monthValue - 1,
            now.dayOfMonth
        ).show()
    }

    private fun setSellFraction(totalShare: Double, fraction: Double) {
        if (totalShare <= 0) return
        binding.etSellShare.setText("%.2f".format(totalShare * fraction))
        updateConfirmSummary(false)
    }

    private fun submitTrade(isBuy: Boolean) {
        val fund = viewModel.funds.value.firstOrNull { it.code == fundCode } ?: return
        val date = selectedDate.format(DateTimeFormatter.ISO_DATE)
        val data = if (isBuy) {
            val amount = binding.etBuyAmount.text?.toString()?.toDoubleOrNull() ?: return
            val fee = binding.etBuyFeeRate.text?.toString()?.toDoubleOrNull() ?: 0.0
            val after3pm = binding.rbBuyAfter.isChecked
            TradeData(
                type = "buy",
                amount = amount,
                feeRate = fee,
                date = date,
                isAfter3pm = after3pm
            )
        } else {
            val share = binding.etSellShare.text?.toString()?.toDoubleOrNull() ?: return
            val feeText = binding.etSellFee.text?.toString().orEmpty()
            val feeMode = if (binding.rbFeeRate.isChecked) "rate" else "amount"
            val after3pm = binding.rbSellAfter.isChecked
            TradeData(
                type = "sell",
                share = share,
                feeRate = if (feeMode == "rate") feeText.toDoubleOrNull() else null,
                feeMode = feeMode,
                feeValue = feeText,
                date = date,
                isAfter3pm = after3pm
            )
        }
        viewModel.saveTrade(fund, data)
        dismissAllowingStateLoss()
    }

    private fun loadReferenceNav() {
        viewLifecycleOwner.lifecycleScope.launch {
            val value = viewModel.fetchSmartNetValue(fundCode, selectedDate.format(DateTimeFormatter.BASIC_ISO_DATE))
            if (value != null) {
                binding.tvRefNav.text = "参考净值(${value.first}) ${"%.4f".format(value.second)}"
            } else {
                val fund = viewModel.funds.value.firstOrNull { it.code == fundCode }
                binding.tvRefNav.text = "参考净值 ${fund?.gsz ?: fund?.dwjz ?: "--"}"
            }
            updateConfirmSummary(tradeType == "buy")
        }
    }

    private fun updateConfirmSummary(isBuy: Boolean) {
        binding.tvConfirmSummary.text = if (isBuy) {
            "买入 ${binding.etBuyAmount.text?.toString().orEmpty()} 元，日期 ${selectedDate}"
        } else {
            "卖出 ${binding.etSellShare.text?.toString().orEmpty()} 份，日期 ${selectedDate}"
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_CODE = "arg_code"
        private const val ARG_NAME = "arg_name"
        private const val ARG_TYPE = "arg_type"

        fun newBuy(code: String, name: String) = newInstance(code, name, "buy")
        fun newSell(code: String, name: String) = newInstance(code, name, "sell")

        fun newInstance(code: String, name: String, type: String): TradeBottomSheet {
            return TradeBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_CODE, code)
                    putString(ARG_NAME, name)
                    putString(ARG_TYPE, type)
                }
            }
        }
    }
}
