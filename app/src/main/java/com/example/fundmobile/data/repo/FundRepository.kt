package com.example.fundmobile.data.repo

import com.example.fundmobile.data.local.PrefsStore
import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.FundGroup
import com.example.fundmobile.data.model.HoldingPosition
import com.example.fundmobile.data.model.PendingTrade
import com.example.fundmobile.data.model.SearchResult
import com.example.fundmobile.data.remote.FundApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class FundRepository(private val prefsStore: PrefsStore) {
    suspend fun fetchAndRefreshFunds(codes: List<String>): List<FundData> = coroutineScope {
        val semaphore = Semaphore(4)
        val funds = codes
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { code ->
                async {
                    semaphore.withPermit {
                        runCatching { FundApi.fetchFundData(code) }.getOrNull()
                    }
                }
            }
            .awaitAll()
            .filterNotNull()

        saveFunds(funds)
        funds
    }

    suspend fun searchFunds(query: String): List<SearchResult> {
        return FundApi.searchFunds(query)
    }

    suspend fun fetchSmartNetValue(code: String, startDate: String): Pair<String, Double>? {
        return FundApi.fetchSmartFundNetValue(code, startDate)
    }

    fun saveFunds(funds: List<FundData>) = prefsStore.saveFunds(funds)
    fun loadFunds(): List<FundData> = prefsStore.loadFunds()

    fun saveFavorites(favorites: Set<String>) = prefsStore.saveFavorites(favorites)
    fun loadFavorites(): Set<String> = prefsStore.loadFavorites()

    fun saveGroups(groups: List<FundGroup>) = prefsStore.saveGroups(groups)
    fun loadGroups(): List<FundGroup> = prefsStore.loadGroups()

    fun saveCollapsedCodes(codes: Set<String>) = prefsStore.saveCollapsedCodes(codes)
    fun loadCollapsedCodes(): Set<String> = prefsStore.loadCollapsedCodes()

    fun saveRefreshMs(ms: Long) = prefsStore.saveRefreshMs(ms)
    fun loadRefreshMs(): Long = prefsStore.loadRefreshMs()

    fun saveHoldings(holdings: Map<String, HoldingPosition>) = prefsStore.saveHoldings(holdings)
    fun loadHoldings(): Map<String, HoldingPosition> = prefsStore.loadHoldings()

    fun savePendingTrades(trades: List<PendingTrade>) = prefsStore.savePendingTrades(trades)
    fun loadPendingTrades(): List<PendingTrade> = prefsStore.loadPendingTrades()

    fun saveViewMode(mode: String) = prefsStore.saveViewMode(mode)
    fun loadViewMode(): String = prefsStore.loadViewMode()

    fun saveSortBy(sortBy: String) = prefsStore.saveSortBy(sortBy)
    fun loadSortBy(): String = prefsStore.loadSortBy()

    fun saveSortOrder(order: String) = prefsStore.saveSortOrder(order)
    fun loadSortOrder(): String = prefsStore.loadSortOrder()
}
