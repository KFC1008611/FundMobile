package com.example.fundmobile.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.databinding.ItemAddGroupFundBinding
import com.example.fundmobile.databinding.SheetAddFundToGroupBinding
import com.example.fundmobile.ui.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddFundToGroupBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetAddFundToGroupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val groupId: String by lazy { requireArguments().getString(ARG_GROUP_ID).orEmpty() }

    private val adapter = FundSelectAdapter()

    override fun getTheme(): Int = R.style.ThemeOverlay_FundMobile_BottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetAddFundToGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val group = viewModel.groups.value.firstOrNull { it.id == groupId }
        val available = viewModel.funds.value.filterNot { group?.codes?.contains(it.code) == true }
        adapter.submit(available)

        binding.recyclerFunds.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFunds.adapter = adapter

        binding.btnAdd.setOnClickListener {
            viewModel.addFundsToGroup(groupId, adapter.selectedCodes())
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_GROUP_ID = "arg_group_id"

        fun newInstance(groupId: String): AddFundToGroupBottomSheet {
            return AddFundToGroupBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_GROUP_ID, groupId)
                }
            }
        }
    }

    private class FundSelectAdapter : RecyclerView.Adapter<FundSelectAdapter.VH>() {
        private val list = mutableListOf<FundData>()
        private val selected = mutableSetOf<String>()

        fun submit(data: List<FundData>) {
            list.clear()
            list.addAll(data)
            selected.clear()
            notifyDataSetChanged()
        }

        fun selectedCodes(): List<String> = selected.toList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemAddGroupFundBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(list[position])
        }

        inner class VH(private val binding: ItemAddGroupFundBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: FundData) {
                binding.tvNameCode.text = "${item.name} (${item.code})"
                binding.check.setOnCheckedChangeListener(null)
                binding.check.isChecked = selected.contains(item.code)
                binding.check.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selected.add(item.code) else selected.remove(item.code)
                }
                binding.root.setOnClickListener {
                    binding.check.isChecked = !binding.check.isChecked
                }
            }
        }
    }
}
