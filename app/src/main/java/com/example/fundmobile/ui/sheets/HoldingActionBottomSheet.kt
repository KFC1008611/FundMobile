package com.example.fundmobile.ui.sheets

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.example.fundmobile.R
import com.example.fundmobile.databinding.SheetHoldingActionBinding
import com.example.fundmobile.ui.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HoldingActionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetHoldingActionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private val fundCode: String by lazy { requireArguments().getString(ARG_CODE).orEmpty() }
    private val fundName: String by lazy { requireArguments().getString(ARG_NAME).orEmpty() }

    override fun getTheme(): Int = R.style.ThemeOverlay_FundMobile_BottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetHoldingActionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvFundNameCode.text = "$fundName  ($fundCode)"

        binding.btnBuy.setOnClickListener {
            TradeBottomSheet.newInstance(fundCode, fundName, "buy").show(parentFragmentManager, "tradeBuy")
            dismiss()
        }
        binding.btnSell.setOnClickListener {
            TradeBottomSheet.newInstance(fundCode, fundName, "sell").show(parentFragmentManager, "tradeSell")
            dismiss()
        }
        binding.btnEditHolding.setOnClickListener {
            HoldingEditBottomSheet.newInstance(fundCode, fundName).show(parentFragmentManager, "holdingEdit")
            dismiss()
        }
        binding.btnClearHolding.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.clear_holding))
                .setMessage(getString(R.string.confirm_clear_holding, fundName))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.saveHolding(fundCode, null)
                }
                .show()
            dismiss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_CODE = "arg_code"
        private const val ARG_NAME = "arg_name"

        fun newInstance(code: String, name: String): HoldingActionBottomSheet {
            return HoldingActionBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_CODE, code)
                    putString(ARG_NAME, name)
                }
            }
        }
    }
}
