package com.example.fundmobile.ui.sheets

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.fundmobile.R
import com.example.fundmobile.data.model.SearchResult
import com.example.fundmobile.databinding.ItemSearchResultBinding
import com.example.fundmobile.databinding.SheetAddFundBinding
import com.example.fundmobile.ui.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddFundBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetAddFundBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private val selectedCodes = linkedSetOf<String>()
    private var searchJob: Job? = null
    private val adapter = SearchAdapter(
        onCheck = { result, checked ->
            if (checked) selectedCodes.add(result.CODE) else selectedCodes.remove(result.CODE)
            renderSelectedChips()
        },
        isAdded = { code -> viewModel.funds.value.any { it.code == code } },
        isSelected = { code -> selectedCodes.contains(code) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ThemeOverlay_FundMobile_BottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetAddFundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSearchResults.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                val keyword = s?.toString().orEmpty().trim()
                if (keyword.isBlank()) {
                    adapter.submitList(emptyList())
                    return
                }
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300)
                    binding.progressSearch.isVisible = true
                    val result = runCatching { viewModel.searchFunds(keyword) }.getOrDefault(emptyList())
                    adapter.submitList(result)
                    binding.progressSearch.isVisible = false
                }
            }
        })

        binding.btnAddSelected.setOnClickListener {
            if (selectedCodes.isNotEmpty()) {
                viewModel.addFunds(selectedCodes.toList())
            }
            dismissAllowingStateLoss()
        }
    }

    private fun renderSelectedChips() {
        binding.chipGroupSelected.removeAllViews()
        binding.selectedChipBar.isVisible = selectedCodes.isNotEmpty()
        selectedCodes.forEach { code ->
            val chip = Chip(requireContext()).apply {
                text = code
                setChipBackgroundColorResource(R.color.primary_10)
                setTextColor(requireContext().getColor(R.color.primary))
                isCloseIconVisible = true
                closeIconTint = android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.primary))
                setOnCloseIconClickListener {
                    selectedCodes.remove(code)
                    adapter.notifyDataSetChanged()
                    renderSelectedChips()
                }
            }
            binding.chipGroupSelected.addView(chip)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private class SearchAdapter(
        private val onCheck: (SearchResult, Boolean) -> Unit,
        private val isAdded: (String) -> Boolean,
        private val isSelected: (String) -> Boolean
    ) : ListAdapter<SearchResult, SearchAdapter.VH>(Diff) {

        object Diff : DiffUtil.ItemCallback<SearchResult>() {
            override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean = oldItem.CODE == newItem.CODE
            override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean = oldItem == newItem
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding, onCheck, isAdded, isSelected)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        class VH(
            private val binding: ItemSearchResultBinding,
            private val onCheck: (SearchResult, Boolean) -> Unit,
            private val isAdded: (String) -> Boolean,
            private val isSelected: (String) -> Boolean
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: SearchResult) {
                val added = isAdded(item.CODE)
                binding.tvFundName.text = item.NAME
                binding.tvFundCodeType.text = "${item.CODE}  ${item.TYPE.orEmpty()}"
                binding.checkSelect.isEnabled = !added
                binding.checkSelect.isChecked = isSelected(item.CODE)
                binding.tvAdded.isVisible = added
                binding.checkSelect.isVisible = !added

                binding.root.setOnClickListener {
                    if (added) return@setOnClickListener
                    val next = !binding.checkSelect.isChecked
                    binding.checkSelect.isChecked = next
                    onCheck(item, next)
                }
                binding.checkSelect.setOnCheckedChangeListener { _, checked ->
                    if (!added) onCheck(item, checked)
                }
            }
        }
    }
}
