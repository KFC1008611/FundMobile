package com.example.fundmobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fundmobile.data.local.PrefsStore
import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.FundGroup
import com.example.fundmobile.data.model.HoldingPosition
import com.example.fundmobile.data.model.PendingTrade
import com.example.fundmobile.data.model.SearchResult
import com.example.fundmobile.data.repo.FundRepository
import com.example.fundmobile.domain.PortfolioCalculator
import com.example.fundmobile.domain.TradingDayChecker
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FundRepository(PrefsStore(application))
    private val chinaZone = ZoneId.of("Asia/Shanghai")

    val funds = MutableStateFlow<List<FundData>>(emptyList())
    val favorites = MutableStateFlow<Set<String>>(emptySet())
    val groups = MutableStateFlow<List<FundGroup>>(emptyList())
    val holdings = MutableStateFlow<Map<String, HoldingPosition>>(emptyMap())
    val pendingTrades = MutableStateFlow<List<PendingTrade>>(emptyList())
    val collapsedCodes = MutableStateFlow<Set<String>>(emptySet())
    val refreshing = MutableStateFlow(false)
    val refreshMs = MutableStateFlow(30000L)
    val currentTab = MutableStateFlow("all")
    val sortBy = MutableStateFlow("default")
    val sortOrder = MutableStateFlow("desc")
    val viewMode = MutableStateFlow("card")
    val isTradingDay = MutableStateFlow(true)

    val tabs: StateFlow<List<TabItem>> = groups
        .combine(currentTab) { g, current ->
            val list = buildList {
                add(TabItem("all", "全部"))
                add(TabItem("fav", "自选"))
                g.forEach { add(TabItem(it.id, it.name)) }
            }
            if (list.none { it.id == current }) {
                currentTab.value = "all"
            }
            list
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(TabItem("all", "全部"), TabItem("fav", "自选")))

    // combine only supports up to 5 flows, so we nest two combine calls
    private val filterInputs = combine(funds, favorites, groups, currentTab) { allFunds, fav, groupList, tab ->
        val filtered = when (tab) {
            "all" -> allFunds
            "fav" -> allFunds.filter { fav.contains(it.code) }
            else -> {
                val g = groupList.firstOrNull { it.id == tab }
                if (g == null) emptyList() else allFunds.filter { g.codes.contains(it.code) }
            }
        }
        filtered
    }

    val displayFunds: StateFlow<List<FundData>> = combine(
        filterInputs, sortBy, sortOrder, holdings, isTradingDay
    ) { filtered, by, order, holdingMap, tradingDay ->
        val todayStr = LocalDate.now(chinaZone).toString()
        val comparator = when (by) {
            "name" -> compareBy<FundData> { it.name }
            "yield" -> compareBy<FundData> { it.gszzl ?: it.zzl ?: 0.0 }
            "holding" -> compareBy<FundData> {
                PortfolioCalculator.getHoldingProfit(it, holdingMap[it.code], tradingDay, todayStr)?.profitTotal ?: 0.0
            }
            else -> compareBy { 0 }
        }

        if (by == "default") filtered else if (order == "asc") filtered.sortedWith(comparator) else filtered.sortedWith(comparator.reversed())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val portfolioSummary: StateFlow<PortfolioCalculator.GroupSummary?> = combine(
        displayFunds, holdings, isTradingDay
    ) { fundList, holdingMap, tradingDay ->
        val summary = PortfolioCalculator.calculateGroupSummary(
            funds = fundList,
            holdings = holdingMap,
            isTradingDay = tradingDay,
            todayStr = LocalDate.now(chinaZone).toString()
        )
        if (summary.hasHolding) summary else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var autoRefreshJob: Job? = null

    init {
        funds.value = repo.loadFunds()
        favorites.value = repo.loadFavorites()
        groups.value = repo.loadGroups()
        collapsedCodes.value = repo.loadCollapsedCodes()
        refreshMs.value = repo.loadRefreshMs()
        holdings.value = repo.loadHoldings()
        pendingTrades.value = repo.loadPendingTrades()
        viewMode.value = repo.loadViewMode()
        sortBy.value = repo.loadSortBy()
        sortOrder.value = repo.loadSortOrder()

        viewModelScope.launch {
            isTradingDay.value = TradingDayChecker.isTradingDay()
        }
        startAutoRefresh()
    }

    fun setCurrentTab(tab: String) {
        currentTab.value = tab
    }

    fun refreshAll() {
        viewModelScope.launch {
            refreshing.value = true
            try {
                val codes = funds.value.map { it.code }
                if (codes.isEmpty()) return@launch
                val refreshed = repo.fetchAndRefreshFunds(codes)
                funds.value = refreshed
                repo.saveFunds(refreshed)
            } finally {
                refreshing.value = false
            }
        }
    }

    suspend fun searchFunds(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        return repo.searchFunds(query)
    }

    suspend fun fetchSmartNetValue(code: String, date: String): Pair<String, Double>? {
        return repo.fetchSmartNetValue(code, date)
    }

    fun addFunds(codes: List<String>) {
        viewModelScope.launch {
            val mergedCodes = (funds.value.map { it.code } + codes).distinct()
            if (mergedCodes.isEmpty()) return@launch
            refreshing.value = true
            try {
                val refreshed = repo.fetchAndRefreshFunds(mergedCodes)
                funds.value = refreshed
                repo.saveFunds(refreshed)
            } finally {
                refreshing.value = false
            }
        }
    }

    fun removeFund(code: String) {
        val updatedFunds = funds.value.filterNot { it.code == code }
        funds.value = updatedFunds
        repo.saveFunds(updatedFunds)

        val updatedFav = favorites.value - code
        favorites.value = updatedFav
        repo.saveFavorites(updatedFav)

        val updatedGroups = groups.value.map { g ->
            g.copy(codes = g.codes.filterNot { it == code }.toMutableList())
        }
        groups.value = updatedGroups
        repo.saveGroups(updatedGroups)

        val updatedHoldings = holdings.value.toMutableMap().apply { remove(code) }
        holdings.value = updatedHoldings
        repo.saveHoldings(updatedHoldings)
    }

    fun toggleFavorite(code: String) {
        val updated = favorites.value.toMutableSet().apply {
            if (contains(code)) remove(code) else add(code)
        }
        favorites.value = updated
        repo.saveFavorites(updated)
    }

    fun toggleCollapse(code: String) {
        val updated = collapsedCodes.value.toMutableSet().apply {
            if (contains(code)) remove(code) else add(code)
        }
        collapsedCodes.value = updated
        repo.saveCollapsedCodes(updated)
    }

    fun setRefreshInterval(seconds: Int) {
        val clamped = seconds.coerceIn(5, 300)
        val ms = clamped * 1000L
        refreshMs.value = ms
        repo.saveRefreshMs(ms)
        startAutoRefresh()
    }

    fun setSort(by: String, order: String) {
        sortBy.value = by
        sortOrder.value = order
        repo.saveSortBy(by)
        repo.saveSortOrder(order)
    }

    fun setViewMode(mode: String) {
        viewMode.value = mode
        repo.saveViewMode(mode)
    }

    fun saveTrade(fund: FundData, data: TradeData) {
        val trade = PendingTrade(
            id = "${fund.code}-${System.currentTimeMillis()}",
            fundCode = fund.code,
            fundName = fund.name,
            type = data.type,
            share = data.share,
            amount = data.amount,
            feeRate = data.feeRate,
            feeMode = data.feeMode,
            feeValue = data.feeValue,
            date = data.date,
            isAfter3pm = data.isAfter3pm
        )
        val updated = pendingTrades.value + trade
        pendingTrades.value = updated
        repo.savePendingTrades(updated)
    }

    fun saveHolding(code: String, position: HoldingPosition?) {
        val updated = holdings.value.toMutableMap()
        if (position == null) updated.remove(code) else updated[code] = position
        holdings.value = updated
        repo.saveHoldings(updated)
    }

    fun addGroup(name: String) {
        if (name.isBlank()) return
        val updated = groups.value + FundGroup(
            id = "g-${System.currentTimeMillis()}",
            name = name.take(8)
        )
        groups.value = updated
        repo.saveGroups(updated)
    }

    fun removeGroup(id: String) {
        val updated = groups.value.filterNot { it.id == id }
        groups.value = updated
        repo.saveGroups(updated)
        if (currentTab.value == id) currentTab.value = "all"
    }

    fun updateGroups(newGroups: List<FundGroup>) {
        groups.value = newGroups
        repo.saveGroups(newGroups)
    }

    fun addFundsToGroup(groupId: String, codes: List<String>) {
        val updated = groups.value.map { g ->
            if (g.id == groupId) g.copy(codes = (g.codes + codes).distinct().toMutableList()) else g
        }
        groups.value = updated
        repo.saveGroups(updated)
    }

    fun removeFundFromGroup(groupId: String, code: String) {
        val updated = groups.value.map { g ->
            if (g.id == groupId) g.copy(codes = g.codes.filterNot { it == code }.toMutableList()) else g
        }
        groups.value = updated
        repo.saveGroups(updated)
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(refreshMs.value)
                if (funds.value.isNotEmpty()) refreshAll()
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
