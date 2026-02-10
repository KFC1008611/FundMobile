package com.example.fundmobile.ui.sheets

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.data.model.FundGroup
import com.example.fundmobile.databinding.ItemGroupManageBinding
import com.example.fundmobile.databinding.SheetGroupManageBinding
import com.example.fundmobile.ui.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class GroupManageBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetGroupManageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val adapter = GroupAdapter()

    override fun getTheme(): Int = R.style.ThemeOverlay_FundMobile_BottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetGroupManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter.submit(viewModel.groups.value.toMutableList())
        adapter.onDelete = { group ->
            if (group.codes.isNotEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.delete))
                    .setMessage(getString(R.string.confirm_delete_group, group.name))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        adapter.removeGroup(group)
                    }
                    .show()
            } else {
                adapter.removeGroup(group)
            }
        }

        binding.recyclerGroups.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerGroups.adapter = adapter

        binding.btnAddGroup.setOnClickListener {
            val next = FundGroup(id = "g-${System.currentTimeMillis()}", name = "")
            adapter.addGroup(next)
        }

        binding.btnSaveGroups.setOnClickListener {
            val updated = adapter.current()
                .map { it.copy(name = it.name.trim().take(8)) }
                .filter { it.name.isNotBlank() }
            viewModel.updateGroups(updated)
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private class GroupAdapter : RecyclerView.Adapter<GroupAdapter.VH>() {
        private val list = mutableListOf<FundGroup>()
        var onDelete: ((FundGroup) -> Unit)? = null

        fun submit(data: MutableList<FundGroup>) {
            list.clear()
            list.addAll(data)
            notifyDataSetChanged()
        }

        fun addGroup(group: FundGroup) {
            list.add(group)
            notifyItemInserted(list.lastIndex)
        }

        fun removeGroup(group: FundGroup) {
            val idx = list.indexOfFirst { it.id == group.id }
            if (idx >= 0) {
                list.removeAt(idx)
                notifyItemRemoved(idx)
            }
        }

        fun current(): List<FundGroup> = list.toList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemGroupManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(list[position])
        }

        inner class VH(private val binding: ItemGroupManageBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: FundGroup) {
                binding.etGroupName.setText(item.name)
                binding.etGroupName.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val text = binding.etGroupName.text?.toString().orEmpty().trim().take(8)
                        val idx = bindingAdapterPosition
                        if (idx != RecyclerView.NO_POSITION) {
                            list[idx] = list[idx].copy(name = text)
                        }
                    }
                }
                binding.btnDeleteGroup.setOnClickListener {
                    onDelete?.invoke(item)
                }
            }
        }
    }
}
