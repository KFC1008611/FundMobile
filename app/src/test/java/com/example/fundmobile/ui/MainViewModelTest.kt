package com.example.fundmobile.ui

import android.app.Application
import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.FundGroup
import com.example.fundmobile.data.model.HoldingPosition
import com.example.fundmobile.data.remote.HttpClient
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val testDispatcher = UnconfinedTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: MainViewModel
    private lateinit var originalClient: OkHttpClient

    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("fund_mobile", Application.MODE_PRIVATE).edit().clear().apply()

        originalClient = HttpClient.client
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val quote = "v_sh000001=\"${(0..30).joinToString("~") { i -> if (i == 30) today else "x$i" }}\";"
        HttpClient.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("qt.gtimg.cn/q=sh000001") -> quote
                    url.contains("F10DataApi.aspx") -> {
                        val date = Regex("""[?&]sdate=([^&]+)""").find(url)?.groupValues?.getOrNull(1) ?: "2024-01-15"
                        val nav = if (date.endsWith("-16")) "1.5300" else "1.5200"
                        """var apidata={content:"<table><tr><td>$date</td><td>$nav</td></tr></table>"};"""
                    }
                    else -> ""
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()

        viewModel = MainViewModel(app)
    }

    @After
    fun tearDown() {
        var type: Class<*>? = viewModel.javaClass
        while (type != null) {
            val method = runCatching { type.getDeclaredMethod("clear") }.getOrNull()
            if (method != null) {
                method.isAccessible = true
                method.invoke(viewModel)
                break
            }
            type = type.superclass
        }
        HttpClient.client = originalClient
    }

    @Test
    fun initialState_currentTabIsAll() {
        assertEquals("all", viewModel.currentTab.value)
    }

    @Test
    fun setCurrentTab_updatesTab() {
        viewModel.setCurrentTab("fav")
        assertEquals("fav", viewModel.currentTab.value)
    }

    @Test
    fun toggleFavorite_addsToFavorites() {
        viewModel.toggleFavorite("161725")
        assertTrue(viewModel.favorites.value.contains("161725"))
    }

    @Test
    fun toggleFavorite_removesFromFavorites() {
        viewModel.toggleFavorite("161725")
        viewModel.toggleFavorite("161725")
        assertFalse(viewModel.favorites.value.contains("161725"))
    }

    @Test
    fun toggleCollapse_addsToCollapsed() {
        viewModel.toggleCollapse("161725")
        assertTrue(viewModel.collapsedCodes.value.contains("161725"))
    }

    @Test
    fun toggleCollapse_removesFromCollapsed() {
        viewModel.toggleCollapse("161725")
        viewModel.toggleCollapse("161725")
        assertFalse(viewModel.collapsedCodes.value.contains("161725"))
    }

    @Test
    fun addGroup_addsNewGroup() {
        viewModel.addGroup("白酒")
        assertTrue(viewModel.groups.value.any { it.name == "白酒" })
    }

    @Test
    fun addGroup_blankName_doesNothing() {
        val before = viewModel.groups.value.size
        viewModel.addGroup("  ")
        assertEquals(before, viewModel.groups.value.size)
    }

    @Test
    fun addGroup_truncatesNameTo8Chars() {
        viewModel.addGroup("1234567890")
        assertEquals("12345678", viewModel.groups.value.last().name)
    }

    @Test
    fun removeGroup_removesGroupAndResetsTab() {
        val group = FundGroup(id = "g-fixed", name = "测试组")
        viewModel.updateGroups(listOf(group))
        viewModel.setCurrentTab("g-fixed")

        viewModel.removeGroup("g-fixed")

        assertTrue(viewModel.groups.value.none { it.id == "g-fixed" })
        assertEquals("all", viewModel.currentTab.value)
    }

    @Test
    fun updateGroups_replacesAllGroups() {
        val groups = listOf(FundGroup("g1", "组1"), FundGroup("g2", "组2"))
        viewModel.updateGroups(groups)
        assertEquals(groups, viewModel.groups.value)
    }

    @Test
    fun addFundsToGroup_addsCodesDistinctly() {
        viewModel.updateGroups(listOf(FundGroup("g1", "组1", mutableListOf("161725"))))
        viewModel.addFundsToGroup("g1", listOf("161725", "110022"))

        assertEquals(listOf("161725", "110022"), viewModel.groups.value.first().codes)
    }

    @Test
    fun addFunds_onlySuccessfulCodesBecomeFavorites() {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val indexQuote = "v_sh000001=\"${(0..30).joinToString("~") { i -> if (i == 30) today else "x$i" }}\";"
        val validGz = """jsonpgz({"fundcode":"161725","name":"基金161725","dwjz":"1.5000","gsz":"1.5200","gztime":"2024-01-15 15:00","jzrq":"2024-01-14","gszzl":"1.33"});"""
        val customClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("qt.gtimg.cn/q=sh000001") -> indexQuote
                    url.contains("fundgz.1234567.com.cn/js/161725.js") -> validGz
                    else -> ""
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()

        val previous = HttpClient.client
        HttpClient.client = customClient
        try {
            viewModel.addFunds(listOf("161725", "999999"))
            waitForAsyncWork()
            assertTrue(viewModel.favorites.value.contains("161725"))
            assertFalse(viewModel.favorites.value.contains("999999"))
        } finally {
            HttpClient.client = previous
        }
    }

    @Test
    fun removeFundFromGroup_removesCode() {
        viewModel.updateGroups(listOf(FundGroup("g1", "组1", mutableListOf("161725", "110022"))))
        viewModel.removeFundFromGroup("g1", "110022")

        assertEquals(listOf("161725"), viewModel.groups.value.first().codes)
    }

    @Test
    fun saveHolding_addsHolding() {
        viewModel.saveHolding("161725", HoldingPosition(100.0, 1.2))
        assertEquals(HoldingPosition(100.0, 1.2), viewModel.holdings.value["161725"])
    }

    @Test
    fun saveHolding_null_removesHolding() {
        viewModel.saveHolding("161725", HoldingPosition(100.0, 1.2))
        viewModel.saveHolding("161725", null)
        assertFalse(viewModel.holdings.value.containsKey("161725"))
    }

    @Test
    fun setRefreshInterval_clampsTo5min() {
        viewModel.setRefreshInterval(1)
        assertEquals(5000L, viewModel.refreshMs.value)
    }

    @Test
    fun setRefreshInterval_clampsTo300max() {
        viewModel.setRefreshInterval(999)
        assertEquals(300000L, viewModel.refreshMs.value)
    }

    @Test
    fun setRefreshInterval_updatesMs() {
        viewModel.setRefreshInterval(60)
        assertEquals(60000L, viewModel.refreshMs.value)
    }

    @Test
    fun setSort_updatesSortByAndOrder() {
        viewModel.setSort("yield", "asc")
        assertEquals("yield", viewModel.sortBy.value)
        assertEquals("asc", viewModel.sortOrder.value)
    }

    @Test
    fun setViewMode_updatesViewMode() {
        viewModel.setViewMode("list")
        assertEquals("list", viewModel.viewMode.value)
    }

    @Test
    fun removeFund_removesFromAllCollections() {
        val code = "161725"
        viewModel.funds.value = listOf(sampleFund(code), sampleFund("110022"))
        viewModel.favorites.value = setOf(code)
        viewModel.groups.value = listOf(FundGroup("g1", "组1", mutableListOf(code, "110022")))
        viewModel.holdings.value = mapOf(code to HoldingPosition(100.0, 1.2))
        viewModel.pendingTrades.value = listOf(
            com.example.fundmobile.data.model.PendingTrade(
                id = "p1",
                fundCode = code,
                fundName = "基金$code",
                type = "buy",
                share = null,
                amount = 100.0,
                feeRate = 0.0,
                feeMode = null,
                feeValue = null,
                date = "2024-01-15",
                isAfter3pm = false
            )
        )

        viewModel.removeFund(code)

        assertTrue(viewModel.funds.value.none { it.code == code })
        assertFalse(viewModel.favorites.value.contains(code))
        assertFalse(viewModel.groups.value.first().codes.contains(code))
        assertFalse(viewModel.holdings.value.containsKey(code))
        assertTrue(viewModel.pendingTrades.value.none { it.fundCode == code })
    }

    @Test
    fun tabs_includeHoldingTab() {
        val tabIds = viewModel.tabs.value.map { it.id }
        assertEquals(listOf("all", "holding", "fav"), tabIds)
    }

    @Test
    fun tabs_holdingTabTitleIsCorrect() {
        val holdingTab = viewModel.tabs.value.first { it.id == "holding" }
        assertEquals("持有", holdingTab.title)
    }

    @Test
    fun tabs_orderWithGroups() {
        viewModel.updateGroups(listOf(FundGroup("g1", "白酒")))
        val tabIds = viewModel.tabs.value.map { it.id }
        assertEquals(listOf("all", "holding", "fav", "g1"), tabIds)
    }

    @Test
    fun displayFunds_allTab_showsAllAddedFunds() {
        val fund1 = sampleFund("161725")
        val fund2 = sampleFund("110022")
        val fund3 = sampleFund("003456")
        viewModel.funds.value = listOf(fund1, fund2, fund3)
        viewModel.favorites.value = setOf("161725")
        viewModel.holdings.value = mapOf("110022" to HoldingPosition(100.0, 1.2))
        viewModel.setCurrentTab("all")

        val displayed = viewModel.displayFunds.value.map { it.code }
        assertTrue(displayed.contains("161725"))
        assertTrue(displayed.contains("110022"))
        assertTrue(displayed.contains("003456"))
    }

    @Test
    fun displayFunds_allTab_showsFundEvenIfNeitherFavNorHolding() {
        viewModel.funds.value = listOf(sampleFund("161725"))
        viewModel.favorites.value = emptySet()
        viewModel.holdings.value = emptyMap()
        viewModel.setCurrentTab("all")

        assertEquals(1, viewModel.displayFunds.value.size)
        assertEquals("161725", viewModel.displayFunds.value[0].code)
    }

    @Test
    fun displayFunds_allTab_deduplicatesFavAndHoldingOverlap() {
        viewModel.funds.value = listOf(sampleFund("161725"))
        viewModel.favorites.value = setOf("161725")
        viewModel.holdings.value = mapOf("161725" to HoldingPosition(50.0, 1.0))
        viewModel.setCurrentTab("all")

        val displayed = viewModel.displayFunds.value
        assertEquals(1, displayed.size)
        assertEquals("161725", displayed[0].code)
    }

    @Test
    fun displayFunds_holdingTab_showsOnlyFundsWithPositiveShares() {
        val fund1 = sampleFund("161725")
        val fund2 = sampleFund("110022")
        viewModel.funds.value = listOf(fund1, fund2)
        viewModel.favorites.value = setOf("161725", "110022")
        viewModel.holdings.value = mapOf(
            "161725" to HoldingPosition(100.0, 1.2),
            "110022" to HoldingPosition(0.0, 0.0)
        )
        viewModel.setCurrentTab("holding")

        val displayed = viewModel.displayFunds.value.map { it.code }
        assertTrue(displayed.contains("161725"))
        assertFalse(displayed.contains("110022"))
    }

    @Test
    fun displayFunds_holdingTab_excludesZeroShareHoldings() {
        val fund1 = sampleFund("161725")
        viewModel.funds.value = listOf(fund1)
        viewModel.favorites.value = setOf("161725")
        viewModel.holdings.value = mapOf("161725" to HoldingPosition(0.0, 1.2))
        viewModel.setCurrentTab("holding")

        val displayed = viewModel.displayFunds.value.map { it.code }
        assertFalse(displayed.contains("161725"))
    }

    @Test
    fun displayFunds_holdingTab_emptyWhenNoHoldings() {
        viewModel.funds.value = listOf(sampleFund("161725"))
        viewModel.favorites.value = setOf("161725")
        viewModel.holdings.value = emptyMap()
        viewModel.setCurrentTab("holding")

        assertTrue(viewModel.displayFunds.value.isEmpty())
    }

    @Test
    fun displayFunds_holdingTab_disappearsWhenShareDropsToZero() {
        viewModel.funds.value = listOf(sampleFund("161725"))
        viewModel.favorites.value = setOf("161725")
        viewModel.holdings.value = mapOf("161725" to HoldingPosition(100.0, 1.0))
        viewModel.setCurrentTab("holding")
        assertEquals(1, viewModel.displayFunds.value.size)

        viewModel.saveHolding("161725", HoldingPosition(0.0, 1.0))

        assertTrue(viewModel.displayFunds.value.isEmpty())
    }

    @Test
    fun displayFunds_favTab_showsFavoritesOnly() {
        val fund1 = sampleFund("161725")
        val fund2 = sampleFund("110022")
        viewModel.funds.value = listOf(fund1, fund2)
        viewModel.favorites.value = setOf("161725")
        viewModel.holdings.value = mapOf("110022" to HoldingPosition(100.0, 1.2))
        viewModel.setCurrentTab("fav")

        val displayed = viewModel.displayFunds.value.map { it.code }
        assertTrue(displayed.contains("161725"))
        assertFalse(displayed.contains("110022"))
    }

    @Test
    fun displayFunds_favTab_showsFundWithHoldingIfAlsoFavorited() {
        viewModel.funds.value = listOf(sampleFund("161725"))
        viewModel.favorites.value = setOf("161725")
        viewModel.holdings.value = mapOf("161725" to HoldingPosition(100.0, 1.2))
        viewModel.setCurrentTab("fav")

        val displayed = viewModel.displayFunds.value.map { it.code }
        assertTrue(displayed.contains("161725"))
    }

    @Test
    fun displayFunds_holdingAndFav_fundWithBothAppearsInBothTabs() {
        viewModel.funds.value = listOf(sampleFund("161725"))
        viewModel.favorites.value = setOf("161725")
        viewModel.holdings.value = mapOf("161725" to HoldingPosition(100.0, 1.2))

        viewModel.setCurrentTab("holding")
        assertTrue(viewModel.displayFunds.value.any { it.code == "161725" })

        viewModel.setCurrentTab("fav")
        assertTrue(viewModel.displayFunds.value.any { it.code == "161725" })
    }

    @Test
    fun displayFunds_sortByHoldingDesc_noHoldingStaysBehindNegativeHolding() {
        val holdingFund = sampleFund("161725")
        val noHoldingFund = sampleFund("110022")
        viewModel.funds.value = listOf(holdingFund, noHoldingFund)
        viewModel.holdings.value = mapOf("161725" to HoldingPosition(100.0, 2.0))
        viewModel.setCurrentTab("all")
        viewModel.setSort("holding", "desc")

        val displayed = viewModel.displayFunds.value.map { it.code }
        assertEquals(listOf("161725", "110022"), displayed)
    }

    @Test
    fun displayFunds_sortByName_usesChineseLocaleOrdering() {
        val fundA = sampleFund("161725").copy(name = "中欧成长")
        val fundB = sampleFund("110022").copy(name = "阿尔法精选")
        val fundC = sampleFund("003456").copy(name = "百度指数")
        viewModel.funds.value = listOf(fundA, fundB, fundC)
        viewModel.setCurrentTab("all")

        viewModel.setSort("name", "asc")

        val orderedNames = viewModel.displayFunds.value.map { it.name }
        assertEquals(listOf("阿尔法精选", "百度指数", "中欧成长"), orderedNames)
    }

    @Test
    fun setCurrentTab_holdingTab_works() {
        viewModel.setCurrentTab("holding")
        assertEquals("holding", viewModel.currentTab.value)
    }

    @Test
    fun saveTrade_buy_createsNewHolding() {
        val fund = sampleFund("161725")
        viewModel.funds.value = listOf(fund)
        viewModel.favorites.value = setOf("161725")

        val tradeData = TradeData(
            type = "buy",
            amount = 10000.0,
            price = 1.52,
            feeRate = 0.15,
            date = "2024-01-15",
            isAfter3pm = false
        )
        viewModel.saveTrade(fund, tradeData)
        waitForAsyncWork()

        val holding = viewModel.holdings.value["161725"]
        assertTrue(holding != null)
        assertTrue(holding!!.share > 0)
        assertTrue(holding.cost > 0)
    }

    @Test
    fun saveTrade_buy_updatesExistingHolding() {
        val fund = sampleFund("161725")
        viewModel.funds.value = listOf(fund)
        viewModel.saveHolding("161725", HoldingPosition(100.0, 1.4))

        val tradeData = TradeData(
            type = "buy",
            amount = 10000.0,
            price = 1.52,
            feeRate = 0.0,
            date = "2024-01-15",
            isAfter3pm = false
        )
        viewModel.saveTrade(fund, tradeData)
        waitForAsyncWork()

        val holding = viewModel.holdings.value["161725"]
        assertTrue(holding != null)
        assertTrue(holding!!.share > 100.0)
    }

    @Test
    fun saveTrade_sell_reducesShares() {
        val fund = sampleFund("161725")
        viewModel.funds.value = listOf(fund)
        viewModel.saveHolding("161725", HoldingPosition(1000.0, 1.4))

        val tradeData = TradeData(
            type = "sell",
            share = 500.0,
            price = 1.52,
            date = "2024-01-15",
            isAfter3pm = false
        )
        viewModel.saveTrade(fund, tradeData)
        waitForAsyncWork()

        val holding = viewModel.holdings.value["161725"]
        assertTrue(holding != null)
        assertEquals(500.0, holding!!.share, 0.01)
        assertEquals(1.4, holding.cost, 0.001)
    }

    @Test
    fun saveTrade_sellAll_removesHolding() {
        val fund = sampleFund("161725")
        viewModel.funds.value = listOf(fund)
        viewModel.saveHolding("161725", HoldingPosition(500.0, 1.4))

        val tradeData = TradeData(
            type = "sell",
            share = 500.0,
            price = 1.52,
            date = "2024-01-15",
            isAfter3pm = false
        )
        viewModel.saveTrade(fund, tradeData)
        waitForAsyncWork()

        assertFalse(viewModel.holdings.value.containsKey("161725"))
    }

    @Test
    fun saveTrade_buy_withPrice_doesNotCreatePendingTrade() {
        val fund = sampleFund("161725")
        viewModel.funds.value = listOf(fund)

        val tradeData = TradeData(
            type = "buy",
            amount = 10000.0,
            price = 1.52,
            feeRate = 0.15,
            date = "2024-01-15",
            isAfter3pm = false
        )
        viewModel.saveTrade(fund, tradeData)
        waitForAsyncWork()

        assertEquals(0, viewModel.pendingTrades.value.size)
    }

    @Test
    fun saveTrade_buy_withoutPrice_createsPendingTrade() {
        val fund = FundData(
            code = "161725",
            name = "基金161725",
            dwjz = null,
            gsz = null,
            gztime = null,
            jzrq = null,
            gszzl = null,
            zzl = null,
            noValuation = true,
            holdings = emptyList()
        )
        viewModel.funds.value = listOf(fund)

        val tradeData = TradeData(
            type = "buy",
            amount = 10000.0,
            feeRate = 0.15,
            date = "2024-01-15",
            isAfter3pm = false
        )
        viewModel.saveTrade(fund, tradeData)
        waitForAsyncWork()

        assertEquals(1, viewModel.pendingTrades.value.size)
        assertEquals("buy", viewModel.pendingTrades.value[0].type)
        assertFalse(viewModel.holdings.value.containsKey("161725"))
    }

    @Test
    fun saveTrade_futureDate_ignored() {
        val fund = sampleFund("161725")
        viewModel.funds.value = listOf(fund)
        viewModel.saveHolding("161725", HoldingPosition(100.0, 1.4))

        val futureDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString()
        val tradeData = TradeData(
            type = "sell",
            share = 10.0,
            price = 1.52,
            date = futureDate,
            isAfter3pm = false
        )
        viewModel.saveTrade(fund, tradeData)
        waitForAsyncWork()

        assertEquals(100.0, viewModel.holdings.value["161725"]?.share ?: 0.0, 0.001)
        assertTrue(viewModel.pendingTrades.value.isEmpty())
    }

    @Test
    fun saveTrade_buy_feeFormulaMatchesOriginal() {
        val fund = sampleFund("161725")
        viewModel.funds.value = listOf(fund)

        val tradeData = TradeData(
            type = "buy",
            amount = 10000.0,
            price = 1.52,
            feeRate = 1.5,
            date = "2024-01-15",
            isAfter3pm = false
        )
        viewModel.saveTrade(fund, tradeData)
        waitForAsyncWork()

        val holding = viewModel.holdings.value["161725"]!!
        val nav = 1.52
        val expectedNetAmount = 10000.0 / (1 + 1.5 / 100.0)
        val expectedShare = expectedNetAmount / nav
        assertEquals(expectedShare, holding.share, 0.01)
        val expectedCost = 10000.0 / expectedShare
        assertEquals(expectedCost, holding.cost, 0.001)
    }

    @Test
    fun saveTrade_buy_withPrice_usesProvidedNav() {
        val fund = sampleFund("161725")
        viewModel.funds.value = listOf(fund)

        val tradeData = TradeData(
            type = "buy",
            amount = 1530.0,
            price = 1.53,
            feeRate = 0.0,
            date = "2024-01-15",
            isAfter3pm = true
        )
        viewModel.saveTrade(fund, tradeData)
        waitForAsyncWork()

        val holding = viewModel.holdings.value["161725"]!!
        assertEquals(1000.0, holding.share, 0.01)
    }

    @Test
    fun refreshAll_partialFailure_keepsPreviousFund() {
        val fundA = sampleFund("161725")
        val fundB = sampleFund("110022")
        viewModel.funds.value = listOf(fundA, fundB)

        val today = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val indexQuote = "v_sh000001=\"${(0..30).joinToString("~") { i -> if (i == 30) today else "x$i" }}\";"
        val validGz = """jsonpgz({"fundcode":"161725","name":"刷新后161725","dwjz":"1.6000","gsz":"1.6200","gztime":"2024-01-15 15:00","jzrq":"2024-01-15","gszzl":"1.25"});"""

        val previous = HttpClient.client
        HttpClient.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("qt.gtimg.cn/q=sh000001") -> indexQuote
                    url.contains("fundgz.1234567.com.cn/js/161725.js") -> validGz
                    url.contains("fundgz.1234567.com.cn/js/110022.js") -> "not_jsonp"
                    url.contains("qt.gtimg.cn/q=jj110022") -> "v_jj110022=\"1~\";"
                    url.contains("FundSearchAPI.ashx") -> "cb({\"Datas\":[]})"
                    else -> ""
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()

        try {
            viewModel.refreshAll()
            waitForAsyncWork()

            val fundsByCode = viewModel.funds.value.associateBy { it.code }
            assertNotNull(fundsByCode["161725"])
            assertNotNull(fundsByCode["110022"])
            assertEquals("刷新后161725", fundsByCode["161725"]!!.name)
            assertEquals("基金110022", fundsByCode["110022"]!!.name)
        } finally {
            HttpClient.client = previous
        }
    }

    @Test
    fun refreshAll_concurrentCalls_doNotOverlapRefreshExecution() {
        val fund = sampleFund("161725")
        viewModel.funds.value = listOf(fund)
        val fundGzHitCount = AtomicInteger(0)
        val validGz = """jsonpgz({"fundcode":"161725","name":"基金161725","dwjz":"1.5000","gsz":"1.5200","gztime":"2024-01-15 15:00","jzrq":"2024-01-14","gszzl":"1.33"});"""

        val previous = HttpClient.client
        HttpClient.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("fundgz.1234567.com.cn/js/161725.js") -> {
                        fundGzHitCount.incrementAndGet()
                        Thread.sleep(200)
                        validGz
                    }
                    url.contains("qt.gtimg.cn/q=jj161725") -> {
                        "v_jj161725=\"1~基金161725~161725~x~x~1.5000~x~0.10~2024-01-14 15:00:00~\";"
                    }
                    url.contains("FundArchivesDatas.aspx?type=jjcc&code=161725") -> {
                        "var apidata={content:\"<table><tbody></tbody></table>\"};"
                    }
                    else -> ""
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()

        try {
            viewModel.refreshAll()
            viewModel.refreshAll()
            Thread.sleep(700)
            assertEquals(1, fundGzHitCount.get())
        } finally {
            HttpClient.client = previous
        }
    }

    private fun sampleFund(code: String): FundData {
        return FundData(
            code = code,
            name = "基金$code",
            dwjz = "1.5",
            gsz = "1.52",
            gztime = "2024-01-15 14:30",
            jzrq = "2024-01-14",
            gszzl = 1.33,
            zzl = 1.11,
            noValuation = false,
            holdings = emptyList()
        )
    }

    private fun waitForAsyncWork() {
        Thread.sleep(120)
    }
}
