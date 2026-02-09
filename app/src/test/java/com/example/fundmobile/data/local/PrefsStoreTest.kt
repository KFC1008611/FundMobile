package com.example.fundmobile.data.local

import android.app.Application
import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.FundGroup
import com.example.fundmobile.data.model.HoldingPosition
import com.example.fundmobile.data.model.PendingTrade
import com.example.fundmobile.data.model.StockHolding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PrefsStoreTest {
    private lateinit var store: PrefsStore

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("fund_mobile", Application.MODE_PRIVATE).edit().clear().apply()
        store = PrefsStore(context)
    }

    @Test
    fun saveFunds_loadFunds_roundTrip() {
        val funds = listOf(sampleFund(code = "161725"), sampleFund(code = "110022"))
        store.saveFunds(funds)
        assertEquals(funds, store.loadFunds())
    }

    @Test
    fun loadFunds_empty_returnsEmptyList() {
        assertTrue(store.loadFunds().isEmpty())
    }

    @Test
    fun saveFunds_withHoldings_preservesNestedData() {
        val funds = listOf(
            sampleFund(
                code = "161725",
                holdings = listOf(StockHolding("600519", "贵州茅台", "9.87%", 1.23))
            )
        )
        store.saveFunds(funds)
        val loaded = store.loadFunds()
        assertEquals("600519", loaded.first().holdings.first().code)
        assertEquals("9.87%", loaded.first().holdings.first().weight)
        assertEquals(1.23, loaded.first().holdings.first().change!!, 1e-6)
    }

    @Test
    fun saveFavorites_loadFavorites_roundTrip() {
        val favorites = setOf("161725", "110022")
        store.saveFavorites(favorites)
        assertEquals(favorites, store.loadFavorites())
    }

    @Test
    fun loadFavorites_empty_returnsEmptySet() {
        assertTrue(store.loadFavorites().isEmpty())
    }

    @Test
    fun saveGroups_loadGroups_roundTrip() {
        val groups = listOf(FundGroup("g1", "白酒", mutableListOf()), FundGroup("g2", "科技", mutableListOf("161725")))
        store.saveGroups(groups)
        assertEquals(groups, store.loadGroups())
    }

    @Test
    fun saveGroups_withCodes_preservesCodes() {
        val groups = listOf(FundGroup("g1", "组合", mutableListOf("161725", "110022")))
        store.saveGroups(groups)
        assertEquals(listOf("161725", "110022"), store.loadGroups().first().codes)
    }

    @Test
    fun saveCollapsedCodes_loadCollapsedCodes_roundTrip() {
        val collapsed = setOf("161725", "110022")
        store.saveCollapsedCodes(collapsed)
        assertEquals(collapsed, store.loadCollapsedCodes())
    }

    @Test
    fun saveRefreshMs_loadRefreshMs_roundTrip() {
        store.saveRefreshMs(60000L)
        assertEquals(60000L, store.loadRefreshMs())
    }

    @Test
    fun loadRefreshMs_default_is30000() {
        assertEquals(30000L, store.loadRefreshMs())
    }

    @Test
    fun saveHoldings_loadHoldings_roundTrip() {
        val holdings = mapOf(
            "161725" to HoldingPosition(100.0, 1.23),
            "110022" to HoldingPosition(50.0, 2.34)
        )
        store.saveHoldings(holdings)
        assertEquals(holdings, store.loadHoldings())
    }

    @Test
    fun loadHoldings_empty_returnsEmptyMap() {
        assertTrue(store.loadHoldings().isEmpty())
    }

    @Test
    fun savePendingTrades_loadPendingTrades_roundTrip() {
        val trades = listOf(
            PendingTrade(
                id = "t1",
                fundCode = "161725",
                fundName = "招商中证白酒",
                type = "buy",
                share = null,
                amount = 1000.0,
                feeRate = 0.15,
                feeMode = "rate",
                feeValue = "0.15",
                date = "2024-01-15",
                isAfter3pm = false,
                timestamp = 12345L
            )
        )
        store.savePendingTrades(trades)
        assertEquals(trades, store.loadPendingTrades())
    }

    @Test
    fun saveViewMode_loadViewMode_roundTrip() {
        store.saveViewMode("list")
        assertEquals("list", store.loadViewMode())
    }

    @Test
    fun loadViewMode_default_isCard() {
        assertEquals("card", store.loadViewMode())
    }

    @Test
    fun saveSortBy_loadSortBy_roundTrip() {
        store.saveSortBy("yield")
        assertEquals("yield", store.loadSortBy())
    }

    @Test
    fun loadSortBy_default_isDefault() {
        assertEquals("default", store.loadSortBy())
    }

    @Test
    fun saveSortOrder_loadSortOrder_roundTrip() {
        store.saveSortOrder("asc")
        assertEquals("asc", store.loadSortOrder())
    }

    @Test
    fun loadSortOrder_default_isDesc() {
        assertEquals("desc", store.loadSortOrder())
    }

    @Test
    fun overwriteExistingData_replacesOldData() {
        store.saveFavorites(setOf("161725", "110022"))
        store.saveFavorites(setOf("000001"))
        assertEquals(setOf("000001"), store.loadFavorites())
    }

    private fun sampleFund(
        code: String,
        holdings: List<StockHolding> = emptyList()
    ): FundData {
        return FundData(
            code = code,
            name = "基金$code",
            dwjz = "1.5000",
            gsz = "1.5200",
            gztime = "2024-01-15 14:30",
            jzrq = "2024-01-14",
            gszzl = 1.33,
            zzl = 1.25,
            noValuation = false,
            holdings = holdings
        )
    }
}
