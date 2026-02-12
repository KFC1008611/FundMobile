package com.example.fundmobile.data.remote

import com.example.fundmobile.data.model.GoldChartPoint
import com.example.fundmobile.data.model.GoldHistory
import com.example.fundmobile.data.model.MarketIndex
import com.example.fundmobile.data.model.MetalPrice
import com.example.fundmobile.data.model.NewsItem
import com.example.fundmobile.data.model.ShangHaiMinute
import com.example.fundmobile.data.model.VolumeData
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarketApiTest {

    private lateinit var originalClient: OkHttpClient
    private lateinit var originalBaiduClient: OkHttpClient

    @Before
    fun setup() {
        originalClient = HttpClient.client
        originalBaiduClient = HttpClient.baiduClient
    }

    @After
    fun teardown() {
        HttpClient.client = originalClient
        HttpClient.baiduClient = originalBaiduClient
    }

    private fun stubClient(handler: (String) -> String) {
        val interceptedClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = handler(url)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()
        HttpClient.client = interceptedClient
        HttpClient.baiduClient = interceptedClient
    }

    // ============================================================
    // fetchRealTimeMetals
    // ============================================================

    @Test
    fun fetchRealTimeMetals_validResponse_parsesAllThreeMetals() = runTest {
        stubClient { _ ->
            """var quote_json = {
                "JO_71": {
                    "showName": "中国黄金基础金价",
                    "q63": 825.50,
                    "q70": 5.30,
                    "q80": 0.65,
                    "q1": 820.00,
                    "q3": 828.00,
                    "q4": 818.50,
                    "q2": 820.20,
                    "time": 1739350800000,
                    "unit": "元/克"
                },
                "JO_92233": {
                    "showName": "周大福金价",
                    "q63": 876.00,
                    "q70": 10.00,
                    "q80": 1.15,
                    "q1": 866.00,
                    "q3": 880.00,
                    "q4": 864.00,
                    "q2": 866.00,
                    "time": 1739350800000,
                    "unit": "元/克"
                },
                "JO_92232": {
                    "showName": "周生生金价",
                    "q63": 878.00,
                    "q70": 8.00,
                    "q80": 0.92,
                    "q1": 870.00,
                    "q3": 882.00,
                    "q4": 868.00,
                    "q2": 870.00,
                    "time": 1739350800000,
                    "unit": "元/克"
                }
            };"""
        }

        val result = MarketApi.fetchRealTimeMetals()
        assertEquals(3, result.size)

        val chinaGold = result[0]
        assertEquals("中国黄金基础金价", chinaGold.name)
        assertEquals("825.50", chinaGold.price)
        assertEquals("5.30", chinaGold.changeAmount)
        assertEquals("0.65%", chinaGold.changePct)
        assertEquals("820", chinaGold.openPrice)
        assertEquals("828", chinaGold.highPrice)
        assertEquals("818.50", chinaGold.lowPrice)
        assertEquals("820.20", chinaGold.prevClose)
        assertEquals("元/克", chinaGold.unit)
        assertTrue(chinaGold.updateTime.isNotBlank())

        assertEquals("周大福金价", result[1].name)
        assertEquals("876", result[1].price)
        assertEquals("周生生金价", result[2].name)
    }

    @Test
    fun fetchRealTimeMetals_integerPrice_formatsWithoutDecimals() = runTest {
        stubClient { _ ->
            """var quote_json = {
                "JO_71": {
                    "showName": "黄金",
                    "q63": 800,
                    "q70": 5,
                    "q80": 1,
                    "q1": 795,
                    "q3": 805,
                    "q4": 790,
                    "q2": 795,
                    "time": 1739350800000,
                    "unit": "元/克"
                },
                "JO_92233": {
                    "showName": "周大福",
                    "q63": 850,
                    "q70": 10,
                    "q80": 2,
                    "q1": 840,
                    "q3": 860,
                    "q4": 835,
                    "q2": 840,
                    "time": 1739350800000,
                    "unit": "元/克"
                },
                "JO_92232": {
                    "showName": "周生生",
                    "q63": 855,
                    "q70": 8,
                    "q80": 1,
                    "q1": 847,
                    "q3": 860,
                    "q4": 845,
                    "q2": 847,
                    "time": 1739350800000,
                    "unit": "元/克"
                }
            };"""
        }

        val result = MarketApi.fetchRealTimeMetals()
        assertEquals(3, result.size)
        assertEquals("800", result[0].price)
        assertEquals("5", result[0].changeAmount)
    }

    @Test
    fun fetchRealTimeMetals_stringPriceField_usesStringDirectly() = runTest {
        stubClient { _ ->
            """var quote_json = {
                "JO_71": {
                    "showName": "黄金",
                    "q63": "825.50",
                    "q70": "5.30",
                    "q80": "0.65",
                    "q1": "820.00",
                    "q3": "828.00",
                    "q4": "818.50",
                    "q2": "820.20",
                    "time": 1739350800000,
                    "unit": "元/克"
                },
                "JO_92233": {
                    "showName": "周大福",
                    "q63": "876.00",
                    "q70": "10.00",
                    "q80": "1.15",
                    "q1": "866.00",
                    "q3": "880.00",
                    "q4": "864.00",
                    "q2": "866.00",
                    "time": 1739350800000,
                    "unit": "元/克"
                },
                "JO_92232": {
                    "showName": "周生生",
                    "q63": "878.00",
                    "q70": "8.00",
                    "q80": "0.92",
                    "q1": "870.00",
                    "q3": "882.00",
                    "q4": "868.00",
                    "q2": "870.00",
                    "time": 1739350800000,
                    "unit": "元/克"
                }
            };"""
        }

        val result = MarketApi.fetchRealTimeMetals()
        assertEquals(3, result.size)
        assertEquals("825.50", result[0].price)
    }

    @Test
    fun fetchRealTimeMetals_missingCode_skipsIt() = runTest {
        stubClient { _ ->
            """var quote_json = {
                "JO_71": {
                    "showName": "中国黄金",
                    "q63": 825.50,
                    "q70": 5.30,
                    "q80": 0.65,
                    "q1": 820.00,
                    "q3": 828.00,
                    "q4": 818.50,
                    "q2": 820.20,
                    "time": 1739350800000,
                    "unit": "元/克"
                },
                "JO_92232": {
                    "showName": "周生生",
                    "q63": 878.00,
                    "q70": 8.00,
                    "q80": 0.92,
                    "q1": 870.00,
                    "q3": 882.00,
                    "q4": 868.00,
                    "q2": 870.00,
                    "time": 1739350800000,
                    "unit": "元/克"
                }
            };"""
        }

        val result = MarketApi.fetchRealTimeMetals()
        assertEquals(2, result.size)
        assertEquals("中国黄金", result[0].name)
        assertEquals("周生生", result[1].name)
    }

    @Test
    fun fetchRealTimeMetals_missingField_returnsNA() = runTest {
        stubClient { _ ->
            """var quote_json = {
                "JO_71": {
                    "showName": "黄金",
                    "q63": 825.50,
                    "q80": 0.65,
                    "q1": 820.00,
                    "q3": 828.00,
                    "q4": 818.50,
                    "q2": 820.20,
                    "time": 1739350800000,
                    "unit": "元/克"
                },
                "JO_92233": {
                    "showName": "周大福",
                    "q63": 876.00,
                    "q70": 10.00,
                    "q80": 1.15,
                    "q1": 866.00,
                    "q3": 880.00,
                    "q4": 864.00,
                    "q2": 866.00,
                    "time": 1739350800000,
                    "unit": "元/克"
                },
                "JO_92232": {
                    "showName": "周生生",
                    "q63": 878.00,
                    "q70": 8.00,
                    "q80": 0.92,
                    "q1": 870.00,
                    "q3": 882.00,
                    "q4": 868.00,
                    "q2": 870.00,
                    "time": 1739350800000,
                    "unit": "元/克"
                }
            };"""
        }

        val result = MarketApi.fetchRealTimeMetals()
        assertEquals(3, result.size)
        assertEquals("N/A", result[0].changeAmount)
    }

    @Test
    fun fetchRealTimeMetals_zeroTimestamp_emptyUpdateTime() = runTest {
        stubClient { _ ->
            """var quote_json = {
                "JO_71": {
                    "showName": "黄金",
                    "q63": 825.50,
                    "q70": 5.30,
                    "q80": 0.65,
                    "q1": 820.00,
                    "q3": 828.00,
                    "q4": 818.50,
                    "q2": 820.20,
                    "time": 0,
                    "unit": "元/克"
                },
                "JO_92233": {
                    "showName": "周大福",
                    "q63": 876.00,
                    "q70": 10.00,
                    "q80": 1.15,
                    "q1": 866.00,
                    "q3": 880.00,
                    "q4": 864.00,
                    "q2": 866.00,
                    "time": 0,
                    "unit": "元/克"
                },
                "JO_92232": {
                    "showName": "周生生",
                    "q63": 878.00,
                    "q70": 8.00,
                    "q80": 0.92,
                    "q1": 870.00,
                    "q3": 882.00,
                    "q4": 868.00,
                    "q2": 870.00,
                    "time": 0,
                    "unit": "元/克"
                }
            };"""
        }

        val result = MarketApi.fetchRealTimeMetals()
        assertEquals(3, result.size)
        assertEquals("", result[0].updateTime)
    }

    @Test
    fun fetchRealTimeMetals_invalidJson_returnsEmptyList() = runTest {
        stubClient { _ -> "not json at all" }
        val result = MarketApi.fetchRealTimeMetals()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchRealTimeMetals_httpError_returnsEmptyList() = runTest {
        HttpClient.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("error".toResponseBody())
                    .build()
            }
            .build()
        val result = MarketApi.fetchRealTimeMetals()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchRealTimeMetals_requestContainsGoldHeaders() = runTest {
        var capturedReferer: String? = null
        HttpClient.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                capturedReferer = chain.request().header("referer")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""var quote_json = {};""".toResponseBody())
                    .build()
            }
            .build()

        MarketApi.fetchRealTimeMetals()
        assertEquals("https://quote.cngold.org/gjs/gjhj.html", capturedReferer)
    }

    // ============================================================
    // fetchGoldHistory
    // ============================================================

    @Test
    fun fetchGoldHistory_validResponse_parsesAndReversesOrder() = runTest {
        stubClient { url ->
            when {
                url.contains("JO_52683") -> """var quote_json = {
                    "data": [
                        {"time": 1739264400000, "q1": 825.00, "q70": 5.00},
                        {"time": 1739178000000, "q1": 820.00, "q70": -3.00}
                    ]
                };"""
                url.contains("JO_42660") -> """var quote_json = {
                    "data": [
                        {"time": 1739264400000, "q1": 876.00, "q70": 10.00},
                        {"time": 1739178000000, "q1": 866.00, "q70": -2.00}
                    ]
                };"""
                else -> ""
            }
        }

        val result = MarketApi.fetchGoldHistory()
        assertEquals(2, result.size)
        assertEquals("820", result[0].chinaGoldPrice)
        assertEquals("866", result[0].chowTaiFookPrice)
        assertEquals("-3", result[0].chinaGoldChange)
        assertEquals("-2", result[0].chowTaiFookChange)

        assertEquals("825", result[1].chinaGoldPrice)
        assertEquals("876", result[1].chowTaiFookPrice)
    }

    @Test
    fun fetchGoldHistory_missingChowTaiFookEntry_showsNA() = runTest {
        stubClient { url ->
            when {
                url.contains("JO_52683") -> """var quote_json = {
                    "data": [
                        {"time": 1739264400000, "q1": 825.00, "q70": 5.00},
                        {"time": 1739178000000, "q1": 820.00, "q70": -3.00}
                    ]
                };"""
                url.contains("JO_42660") -> """var quote_json = {
                    "data": [
                        {"time": 1739264400000, "q1": 876.00, "q70": 10.00}
                    ]
                };"""
                else -> ""
            }
        }

        val result = MarketApi.fetchGoldHistory()
        assertEquals(2, result.size)
        assertEquals("N/A", result[0].chowTaiFookPrice)
        assertEquals("N/A", result[0].chowTaiFookChange)
    }

    @Test
    fun fetchGoldHistory_emptyDataArray_returnsEmptyList() = runTest {
        stubClient { _ -> """var quote_json = {"data": []};""" }
        val result = MarketApi.fetchGoldHistory()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchGoldHistory_invalidJson_returnsEmptyList() = runTest {
        stubClient { _ -> "garbage" }
        val result = MarketApi.fetchGoldHistory()
        assertTrue(result.isEmpty())
    }

    // ============================================================
    // fetchGoldIntraday
    // ============================================================

    @Test
    fun fetchGoldIntraday_validResponse_parsesPoints() = runTest {
        stubClient { _ ->
            """var hq_str_ml = {
                "data": [
                    {"date": 1739350800000, "price": 876.5},
                    {"date": 1739351400000, "price": 877.0},
                    {"date": 1739352000000, "price": 878.2}
                ]
            };"""
        }

        val result = MarketApi.fetchGoldIntraday()
        assertEquals(3, result.size)
        assertEquals(876.5, result[0].price, 0.001)
        assertEquals(877.0, result[1].price, 0.001)
        assertEquals(878.2, result[2].price, 0.001)
        assertTrue(result[0].date.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun fetchGoldIntraday_filtersPriceMinus1() = runTest {
        stubClient { _ ->
            """var hq_str_ml = {
                "data": [
                    {"date": 1739350800000, "price": 876.5},
                    {"date": 1739351400000, "price": -1},
                    {"date": 1739352000000, "price": 878.2}
                ]
            };"""
        }

        val result = MarketApi.fetchGoldIntraday()
        assertEquals(2, result.size)
        assertEquals(876.5, result[0].price, 0.001)
        assertEquals(878.2, result[1].price, 0.001)
    }

    @Test
    fun fetchGoldIntraday_nullPrice_skipsEntry() = runTest {
        stubClient { _ ->
            """var hq_str_ml = {
                "data": [
                    {"date": 1739350800000},
                    {"date": 1739352000000, "price": 878.2}
                ]
            };"""
        }

        val result = MarketApi.fetchGoldIntraday()
        assertEquals(1, result.size)
        assertEquals(878.2, result[0].price, 0.001)
    }

    @Test
    fun fetchGoldIntraday_nullDate_skipsEntry() = runTest {
        stubClient { _ ->
            """var hq_str_ml = {
                "data": [
                    {"price": 876.5},
                    {"date": 1739352000000, "price": 878.2}
                ]
            };"""
        }

        val result = MarketApi.fetchGoldIntraday()
        assertEquals(1, result.size)
    }

    @Test
    fun fetchGoldIntraday_emptyData_returnsEmpty() = runTest {
        stubClient { _ -> """var hq_str_ml = {"data": []};""" }
        val result = MarketApi.fetchGoldIntraday()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchGoldIntraday_noDataField_returnsEmpty() = runTest {
        stubClient { _ -> """var hq_str_ml = {};""" }
        val result = MarketApi.fetchGoldIntraday()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchGoldIntraday_invalidJson_returnsEmpty() = runTest {
        stubClient { _ -> "broken response" }
        val result = MarketApi.fetchGoldIntraday()
        assertTrue(result.isEmpty())
    }

    // ============================================================
    // fetchGlobalIndices
    // ============================================================

    @Test
    fun fetchGlobalIndices_validResponse_parsesAsiaAndAmerica() = runTest {
        stubClient { url ->
            when {
                url.contains("market=asia") -> """{
                    "ResultCode": "0",
                    "Result": {
                        "list": [
                            {"name": "上证指数", "lastPrice": "3350.12", "ratio": "0.85%"},
                            {"name": "深证成指", "lastPrice": "10200.50", "ratio": "-0.32%"}
                        ]
                    }
                }"""
                url.contains("market=america") -> """{
                    "ResultCode": "0",
                    "Result": {
                        "list": [
                            {"name": "纳斯达克", "lastPrice": "19800.25", "ratio": "1.20%"},
                            {"name": "道琼斯", "lastPrice": "44500.00", "ratio": "0.55%"}
                        ]
                    }
                }"""
                url.contains("quotation_index_minute") && url.contains("399006") -> """{
                    "ResultCode": "0",
                    "Result": {
                        "cur": {"price": "2100.50", "ratio": "0.45%"}
                    }
                }"""
                else -> ""
            }
        }

        val result = MarketApi.fetchGlobalIndices()
        assertEquals(5, result.size)
        assertEquals("上证指数", result[0].name)
        assertEquals("深证成指", result[1].name)
        assertEquals("创业板指", result[2].name)
        assertEquals("2100.50", result[2].value)
        assertEquals("0.45%", result[2].changePct)
        assertEquals("纳斯达克", result[3].name)
        assertEquals("道琼斯", result[4].name)
    }

    @Test
    fun fetchGlobalIndices_cybFetchFails_stillReturnsOtherIndices() = runTest {
        stubClient { url ->
            when {
                url.contains("market=asia") -> """{
                    "ResultCode": "0",
                    "Result": {
                        "list": [
                            {"name": "上证指数", "lastPrice": "3350.12", "ratio": "0.85%"}
                        ]
                    }
                }"""
                url.contains("market=america") -> """{
                    "ResultCode": "0",
                    "Result": {
                        "list": [
                            {"name": "纳斯达克", "lastPrice": "19800.25", "ratio": "1.20%"}
                        ]
                    }
                }"""
                url.contains("399006") -> """{
                    "ResultCode": "1",
                    "Result": {}
                }"""
                else -> ""
            }
        }

        val result = MarketApi.fetchGlobalIndices()
        assertEquals(2, result.size)
        assertEquals("上证指数", result[0].name)
        assertEquals("纳斯达克", result[1].name)
    }

    @Test
    fun fetchGlobalIndices_nonZeroResultCode_skipsMarket() = runTest {
        stubClient { url ->
            when {
                url.contains("market=asia") -> """{"ResultCode": "1", "Result": {}}"""
                url.contains("market=america") -> """{
                    "ResultCode": "0",
                    "Result": {
                        "list": [{"name": "纳斯达克", "lastPrice": "19800.25", "ratio": "1.20%"}]
                    }
                }"""
                url.contains("399006") -> """{"ResultCode": "1", "Result": {}}"""
                else -> ""
            }
        }

        val result = MarketApi.fetchGlobalIndices()
        assertEquals(1, result.size)
        assertEquals("纳斯达克", result[0].name)
    }

    @Test
    fun fetchGlobalIndices_invalidJson_returnsEmpty() = runTest {
        stubClient { _ -> "garbage" }
        val result = MarketApi.fetchGlobalIndices()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchGlobalIndices_requestContainsBaiduHeaders() = runTest {
        var capturedOrigin: String? = null
        var capturedAccept: String? = null
        val interceptedClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.url.toString().contains("market=asia")) {
                    capturedOrigin = req.header("origin")
                    capturedAccept = req.header("accept")
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"ResultCode":"1","Result":{}}""".toResponseBody())
                    .build()
            }
            .build()
        HttpClient.client = interceptedClient
        HttpClient.baiduClient = interceptedClient

        MarketApi.fetchGlobalIndices()
        assertEquals("https://gushitong.baidu.com", capturedOrigin)
        assertEquals("application/vnd.finance-web.v1+json", capturedAccept)
    }

    @Test
    fun fetchGlobalIndices_cybInsertedAtIndex2_withLessThan2Items() = runTest {
        stubClient { url ->
            when {
                url.contains("market=asia") -> """{
                    "ResultCode": "0",
                    "Result": {
                        "list": [{"name": "上证指数", "lastPrice": "3350.12", "ratio": "0.85%"}]
                    }
                }"""
                url.contains("market=america") -> """{"ResultCode": "1", "Result": {}}"""
                url.contains("399006") -> """{
                    "ResultCode": "0",
                    "Result": {
                        "cur": {"price": "2100.50", "ratio": "0.45%"}
                    }
                }"""
                else -> ""
            }
        }

        val result = MarketApi.fetchGlobalIndices()
        assertEquals(2, result.size)
        assertEquals("上证指数", result[0].name)
        assertEquals("创业板指", result[1].name)
    }

    // ============================================================
    // fetchShanghaiMinute
    // ============================================================

    @Test
    fun fetchShanghaiMinute_validResponse_parsesMinuteData() = runTest {
        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "newMarketData": {
                        "marketData": [{
                            "p": "0,09:30,3350.12,10.50,0.31,50000,350000;0,09:31,3351.00,11.38,0.34,60000,420000"
                        }]
                    }
                }
            }"""
        }

        val result = MarketApi.fetchShanghaiMinute()
        assertEquals(2, result.size)

        assertEquals("09:30", result[0].time)
        assertEquals("3350.12", result[0].price)
        assertEquals("10.50", result[0].changeAmount)
        assertEquals("0.31%", result[0].changePct)
        assertTrue(result[0].volume.contains("万手"))
        assertTrue(result[0].amount.contains("亿"))

        assertEquals("09:31", result[1].time)
        assertEquals("3351.00", result[1].price)
    }

    @Test
    fun fetchShanghaiMinute_volumeFormatting_correctConversion() = runTest {
        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "newMarketData": {
                        "marketData": [{
                            "p": "0,09:30,3350.12,10.50,0.31,100000,100000000"
                        }]
                    }
                }
            }"""
        }

        val result = MarketApi.fetchShanghaiMinute()
        assertEquals(1, result.size)
        assertEquals("10.00万手", result[0].volume)
        assertEquals("1.00亿", result[0].amount)
    }

    @Test
    fun fetchShanghaiMinute_shortSegment_skipped() = runTest {
        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "newMarketData": {
                        "marketData": [{
                            "p": "0,09:30,3350.12;0,09:31,3351.00,11.38,0.34,60000,420000"
                        }]
                    }
                }
            }"""
        }

        val result = MarketApi.fetchShanghaiMinute()
        assertEquals(1, result.size)
        assertEquals("09:31", result[0].time)
    }

    @Test
    fun fetchShanghaiMinute_nonZeroResultCode_returnsEmpty() = runTest {
        stubClient { _ -> """{"ResultCode": "1", "Result": {}}""" }
        val result = MarketApi.fetchShanghaiMinute()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchShanghaiMinute_missingMarketData_returnsEmpty() = runTest {
        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "newMarketData": {}
                }
            }"""
        }
        val result = MarketApi.fetchShanghaiMinute()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchShanghaiMinute_invalidJson_returnsEmpty() = runTest {
        stubClient { _ -> "broken" }
        val result = MarketApi.fetchShanghaiMinute()
        assertTrue(result.isEmpty())
    }

    // ============================================================
    // fetchVolumeData
    // ============================================================

    @Test
    fun fetchVolumeData_validResponse_parsesAndReversesOrder() = runTest {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        }.format(java.util.Date())

        val yesterday = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        }.format(java.util.Date(System.currentTimeMillis() - 86400000L))

        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "trend": [
                        {"content": [
                            {"marketDate": "$today", "data": {"amount": "10500"}},
                            {"marketDate": "$yesterday", "data": {"amount": "9800"}}
                        ]},
                        {"content": [
                            {"marketDate": "$today", "data": {"amount": "5200"}},
                            {"marketDate": "$yesterday", "data": {"amount": "4900"}}
                        ]},
                        {"content": [
                            {"marketDate": "$today", "data": {"amount": "4300"}},
                            {"marketDate": "$yesterday", "data": {"amount": "4100"}}
                        ]},
                        {"content": [
                            {"marketDate": "$today", "data": {"amount": "1000"}},
                            {"marketDate": "$yesterday", "data": {"amount": "800"}}
                        ]}
                    ]
                }
            }"""
        }

        val result = MarketApi.fetchVolumeData()
        assertTrue(result.isNotEmpty())
        val dates = result.map { it.date }
        val todayIdx = dates.indexOf(today)
        val yesterdayIdx = dates.indexOf(yesterday)
        if (todayIdx >= 0 && yesterdayIdx >= 0) {
            assertTrue("yesterday should come before today in reversed list", yesterdayIdx < todayIdx)
        }

        val todayEntry = result.find { it.date == today }
        assertNotNull(todayEntry)
        assertEquals("10500亿", todayEntry!!.total)
        assertEquals("5200亿", todayEntry.sh)
        assertEquals("4300亿", todayEntry.sz)
        assertEquals("1000亿", todayEntry.bj)
    }

    @Test
    fun fetchVolumeData_missingDateInTrend_skipsDate() = runTest {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        }.format(java.util.Date())

        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "trend": [
                        {"content": [{"marketDate": "2020-01-01", "data": {"amount": "100"}}]},
                        {"content": []},
                        {"content": []},
                        {"content": []}
                    ]
                }
            }"""
        }

        val result = MarketApi.fetchVolumeData()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchVolumeData_nonZeroResultCode_returnsEmpty() = runTest {
        stubClient { _ -> """{"ResultCode": "1", "Result": {}}""" }
        val result = MarketApi.fetchVolumeData()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchVolumeData_invalidJson_returnsEmpty() = runTest {
        stubClient { _ -> "not json" }
        val result = MarketApi.fetchVolumeData()
        assertTrue(result.isEmpty())
    }

    // ============================================================
    // fetchNews
    // ============================================================

    @Test
    fun fetchNews_validResponse_parsesNewsItems() = runTest {
        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "content": {
                        "list": [
                            {
                                "title": "央行下调存款准备金率",
                                "publish_time": "1739350800",
                                "evaluate": "利好"
                            },
                            {
                                "title": "某公司业绩不及预期",
                                "publish_time": "1739350200",
                                "evaluate": "利空"
                            }
                        ]
                    }
                }
            }"""
        }

        val result = MarketApi.fetchNews(2)
        assertEquals(2, result.size)
        assertEquals("央行下调存款准备金率", result[0].content)
        assertEquals("利好", result[0].evaluate)
        assertTrue(result[0].time.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))

        assertEquals("某公司业绩不及预期", result[1].content)
        assertEquals("利空", result[1].evaluate)
    }

    @Test
    fun fetchNews_blankTitle_fallsBackToContentItems() = runTest {
        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "content": {
                        "list": [
                            {
                                "title": "",
                                "content": {
                                    "items": [
                                        {"data": "从内容字段获取的新闻标题"}
                                    ]
                                },
                                "publish_time": "1739350800",
                                "evaluate": ""
                            }
                        ]
                    }
                }
            }"""
        }

        val result = MarketApi.fetchNews(1)
        assertEquals(1, result.size)
        assertEquals("从内容字段获取的新闻标题", result[0].content)
    }

    @Test
    fun fetchNews_blankTitleAndNoContent_skipsItem() = runTest {
        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "content": {
                        "list": [
                            {
                                "title": "",
                                "publish_time": "1739350800",
                                "evaluate": ""
                            },
                            {
                                "title": "有效新闻",
                                "publish_time": "1739350200",
                                "evaluate": "利好"
                            }
                        ]
                    }
                }
            }"""
        }

        val result = MarketApi.fetchNews(2)
        assertEquals(1, result.size)
        assertEquals("有效新闻", result[0].content)
    }

    @Test
    fun fetchNews_invalidPublishTime_usesRawString() = runTest {
        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "content": {
                        "list": [
                            {
                                "title": "测试新闻",
                                "publish_time": "not_a_number",
                                "evaluate": ""
                            }
                        ]
                    }
                }
            }"""
        }

        val result = MarketApi.fetchNews(1)
        assertEquals(1, result.size)
        assertEquals("not_a_number", result[0].time)
    }

    @Test
    fun fetchNews_emptyEvaluate_returnsEmptyString() = runTest {
        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "content": {
                        "list": [
                            {
                                "title": "中性新闻",
                                "publish_time": "1739350800",
                                "evaluate": ""
                            }
                        ]
                    }
                }
            }"""
        }

        val result = MarketApi.fetchNews(1)
        assertEquals(1, result.size)
        assertEquals("", result[0].evaluate)
    }

    @Test
    fun fetchNews_nonZeroResultCode_returnsEmpty() = runTest {
        stubClient { _ -> """{"ResultCode": "1", "Result": {}}""" }
        val result = MarketApi.fetchNews()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchNews_missingListField_returnsEmpty() = runTest {
        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "content": {}
                }
            }"""
        }
        val result = MarketApi.fetchNews()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchNews_invalidJson_returnsEmpty() = runTest {
        stubClient { _ -> "not json" }
        val result = MarketApi.fetchNews()
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchNews_emptyList_returnsEmpty() = runTest {
        stubClient { _ ->
            """{
                "ResultCode": "0",
                "Result": {
                    "content": {
                        "list": []
                    }
                }
            }"""
        }
        val result = MarketApi.fetchNews()
        assertTrue(result.isEmpty())
    }
}
