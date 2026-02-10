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
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TradeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetTradeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private val fundCode by lazy { requireArguments().getString(ARG_CODE).orEmpty() }
    private val fundName by lazy { requireArguments().getString(ARG_NAME).orEmpty() }
    private val tradeType by lazy { requireArguments().getString(ARG_TYPE).orEmpty() }

    private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")
    private var selectedDate: LocalDate = LocalDate.now(chinaZone)
    private var referenceJob: Job? = null
    private var referencePrice: Double? = null

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
        val defaultAfter3pm = LocalTime.now(chinaZone) >= LocalTime.of(15, 0)
        binding.rbBuyAfter.isChecked = defaultAfter3pm
        binding.rbSellAfter.isChecked = defaultAfter3pm

        binding.btnBuyDate.text = selectedDate.format(DateTimeFormatter.ISO_DATE)
        binding.btnSellDate.text = selectedDate.format(DateTimeFormatter.ISO_DATE)
        binding.btnBuyDate.setOnClickListener { pickDate(true) }
        binding.btnSellDate.setOnClickListener { pickDate(false) }
        binding.groupBuyPeriod.setOnCheckedChangeListener { _, _ -> loadReferenceNav() }
        binding.groupSellPeriod.setOnCheckedChangeListener { _, _ -> loadReferenceNav() }

        binding.btnQuarter.setOnClickListener { setSellFraction(getAvailableSellShare(), 0.25) }
        binding.btnThird.setOnClickListener { setSellFraction(getAvailableSellShare(), 1.0 / 3.0) }
        binding.btnHalf.setOnClickListener { setSellFraction(getAvailableSellShare(), 0.5) }
        binding.btnAll.setOnClickListener { setSellFraction(getAvailableSellShare(), 1.0) }
        updateSellShareHint()

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
        val today = LocalDate.now(chinaZone)
        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                val text = selectedDate.format(DateTimeFormatter.ISO_DATE)
                if (isBuy) binding.btnBuyDate.text = text else binding.btnSellDate.text = text
                updateConfirmSummary(isBuy)
                loadReferenceNav()
            },
            now.year,
            now.monthValue - 1,
            now.dayOfMonth
        )
        dialog.datePicker.maxDate = today.atStartOfDay(chinaZone).toInstant().toEpochMilli()
        dialog.show()
    }

    private fun setSellFraction(totalShare: Double, fraction: Double) {
        if (totalShare <= 0) return
        binding.etSellShare.setText("%.2f".format(totalShare * fraction))
        binding.etSellShare.error = null
        updateConfirmSummary(false)
    }

    private fun submitTrade(isBuy: Boolean) {
        val fund = viewModel.funds.value.firstOrNull { it.code == fundCode } ?: return
        val date = selectedDate.format(DateTimeFormatter.ISO_DATE)
        val data = if (isBuy) {
            val amount = binding.etBuyAmount.text?.toString()?.toDoubleOrNull() ?: return
            if (amount <= 0) return
            val fee = binding.etBuyFeeRate.text?.toString()?.toDoubleOrNull() ?: 0.0
            val after3pm = binding.rbBuyAfter.isChecked
            TradeData(
                type = "buy",
                amount = amount,
                price = referencePrice,
                feeRate = fee,
                date = date,
                isAfter3pm = after3pm
            )
        } else {
            val share = binding.etSellShare.text?.toString()?.toDoubleOrNull() ?: return
            if (share <= 0) return
            val availableShare = getAvailableSellShare()
            if (share > availableShare + 1e-8) {
                binding.etSellShare.error = "卖出份额不能超过可用份额(${String.format("%.2f", availableShare)})"
                return
            }
            binding.etSellShare.error = null
            val feeText = binding.etSellFee.text?.toString().orEmpty()
            val feeMode = if (binding.rbFeeRate.isChecked) "rate" else "amount"
            val after3pm = binding.rbSellAfter.isChecked
            TradeData(
                type = "sell",
                share = share,
                price = referencePrice,
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

    private fun getAvailableSellShare(): Double {
        val holdingShare = viewModel.holdings.value[fundCode]?.share ?: 0.0
        val pendingSellShare = viewModel.pendingTrades.value
            .asSequence()
            .filter { it.fundCode == fundCode && it.type == "sell" }
            .sumOf { it.share ?: 0.0 }
        return (holdingShare - pendingSellShare).coerceAtLeast(0.0)
    }

    private fun updateSellShareHint() {
        if (tradeType != "sell") return
        val available = getAvailableSellShare()
        binding.etSellShare.hint = if (available > 0) {
            "最多可卖 ${"%.2f".format(available)} 份"
        } else {
            getString(R.string.sell_shares)
        }
    }

    private fun loadReferenceNav() {
        referenceJob?.cancel()
        referenceJob = viewLifecycleOwner.lifecycleScope.launch {
            val queryDate = if (isAfter3pmSelected()) selectedDate.plusDays(1) else selectedDate
            val value = viewModel.fetchSmartNetValue(fundCode, queryDate.format(DateTimeFormatter.ISO_DATE))
            if (value != null) {
                referencePrice = value.second
                binding.tvRefNav.text = "参考净值(${value.first}) ${"%.4f".format(value.second)}"
            } else {
                referencePrice = null
                binding.tvRefNav.text = "参考净值 暂无（将加入待处理）"
            }
            updateConfirmSummary(tradeType == "buy")
        }
    }

    private fun isAfter3pmSelected(): Boolean {
        return if (tradeType == "buy") binding.rbBuyAfter.isChecked else binding.rbSellAfter.isChecked
    }

    private fun updateConfirmSummary(isBuy: Boolean) {
        binding.tvConfirmSummary.text = if (isBuy) {
            "买入 ${binding.etBuyAmount.text?.toString().orEmpty()} 元，日期 ${selectedDate}"
        } else {
            "卖出 ${binding.etSellShare.text?.toString().orEmpty()} 份，日期 ${selectedDate}"
        }
    }

    override fun onDestroyView() {
        referenceJob?.cancel()
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
