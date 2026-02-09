package com.example.fundmobile.data.local

import android.content.Context
import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.FundGroup
import com.example.fundmobile.data.model.HoldingPosition
import com.example.fundmobile.data.model.PendingTrade
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("fund_mobile", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val KEY_FUNDS = "funds"
        const val KEY_FAVORITES = "favorites"
        const val KEY_GROUPS = "groups"
        const val KEY_COLLAPSED_CODES = "collapsedCodes"
        const val KEY_REFRESH_MS = "refreshMs"
        const val KEY_HOLDINGS = "holdings"
        const val KEY_PENDING_TRADES = "pendingTrades"
        const val KEY_VIEW_MODE = "viewMode"
        const val KEY_SORT_BY = "sortBy"
        const val KEY_SORT_ORDER = "sortOrder"
    }

    fun saveFunds(funds: List<FundData>) {
        prefs.edit().putString(KEY_FUNDS, gson.toJson(funds)).apply()
    }

    fun loadFunds(): List<FundData> {
        val raw = prefs.getString(KEY_FUNDS, null) ?: return emptyList()
        val type = object : TypeToken<List<FundData>>() {}.type
        return runCatching { gson.fromJson<List<FundData>>(raw, type) ?: emptyList() }.getOrDefault(emptyList())
    }

    fun saveFavorites(favorites: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
    }

    fun loadFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()
    }

    fun saveGroups(groups: List<FundGroup>) {
        prefs.edit().putString(KEY_GROUPS, gson.toJson(groups)).apply()
    }

    fun loadGroups(): List<FundGroup> {
        val raw = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
        val type = object : TypeToken<List<FundGroup>>() {}.type
        return runCatching { gson.fromJson<List<FundGroup>>(raw, type) ?: emptyList() }.getOrDefault(emptyList())
    }

    fun saveCollapsedCodes(codes: Set<String>) {
        prefs.edit().putStringSet(KEY_COLLAPSED_CODES, codes).apply()
    }

    fun loadCollapsedCodes(): Set<String> {
        return prefs.getStringSet(KEY_COLLAPSED_CODES, emptySet())?.toSet() ?: emptySet()
    }

    fun saveRefreshMs(ms: Long) {
        prefs.edit().putLong(KEY_REFRESH_MS, ms).apply()
    }

    fun loadRefreshMs(): Long {
        return prefs.getLong(KEY_REFRESH_MS, 30000L)
    }

    fun saveHoldings(holdings: Map<String, HoldingPosition>) {
        prefs.edit().putString(KEY_HOLDINGS, gson.toJson(holdings)).apply()
    }

    fun loadHoldings(): Map<String, HoldingPosition> {
        val raw = prefs.getString(KEY_HOLDINGS, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, HoldingPosition>>() {}.type
        return runCatching {
            gson.fromJson<Map<String, HoldingPosition>>(raw, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    fun savePendingTrades(trades: List<PendingTrade>) {
        prefs.edit().putString(KEY_PENDING_TRADES, gson.toJson(trades)).apply()
    }

    fun loadPendingTrades(): List<PendingTrade> {
        val raw = prefs.getString(KEY_PENDING_TRADES, null) ?: return emptyList()
        val type = object : TypeToken<List<PendingTrade>>() {}.type
        return runCatching { gson.fromJson<List<PendingTrade>>(raw, type) ?: emptyList() }.getOrDefault(emptyList())
    }

    fun saveViewMode(mode: String) {
        prefs.edit().putString(KEY_VIEW_MODE, mode).apply()
    }

    fun loadViewMode(): String {
        return prefs.getString(KEY_VIEW_MODE, "card") ?: "card"
    }

    fun saveSortBy(sortBy: String) {
        prefs.edit().putString(KEY_SORT_BY, sortBy).apply()
    }

    fun loadSortBy(): String {
        return prefs.getString(KEY_SORT_BY, "default") ?: "default"
    }

    fun saveSortOrder(order: String) {
        prefs.edit().putString(KEY_SORT_ORDER, order).apply()
    }

    fun loadSortOrder(): String {
        return prefs.getString(KEY_SORT_ORDER, "desc") ?: "desc"
    }
}
