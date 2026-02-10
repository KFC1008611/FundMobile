package com.example.fundmobile.ui

import android.app.Application
import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.FundGroup
import com.example.fundmobile.data.model.HoldingPosition
import com.example.fundmobile.data.remote.HttpClient
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(quote.toResponseBody())
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

        viewModel.removeFund(code)

        assertTrue(viewModel.funds.value.none { it.code == code })
        assertFalse(viewModel.favorites.value.contains(code))
        assertFalse(viewModel.groups.value.first().codes.contains(code))
        assertFalse(viewModel.holdings.value.containsKey(code))
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
    fun displayFunds_allTab_showsFavoritesAndHoldings() {
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
        assertFalse(displayed.contains("003456"))
    }

    @Test
    fun displayFunds_allTab_excludesFundNeitherFavNorHolding() {
        viewModel.funds.value = listOf(sampleFund("161725"))
        viewModel.favorites.value = emptySet()
        viewModel.holdings.value = emptyMap()
        viewModel.setCurrentTab("all")

        assertTrue(viewModel.displayFunds.value.isEmpty())
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
    fun setCurrentTab_holdingTab_works() {
        viewModel.setCurrentTab("holding")
        assertEquals("holding", viewModel.currentTab.value)
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
}
