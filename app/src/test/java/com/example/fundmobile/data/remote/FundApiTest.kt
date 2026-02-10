package com.example.fundmobile.data.remote

import com.google.gson.Gson
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FundApiTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var originalClient: OkHttpClient

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        originalClient = HttpClient.client
        HttpClient.client = OkHttpClient()
    }

    @After
    fun teardown() {
        HttpClient.client = originalClient
        mockServer.shutdown()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun mockWebServer_jsonpAndGsonComposition_parsesFundGzPayload() = runTest {
        val body = "jsonpgz({\"fundcode\":\"161725\",\"name\":\"招商中证白酒\",\"dwjz\":\"1.5\",\"gsz\":\"1.52\",\"gszzl\":\"1.33\",\"gztime\":\"2024-01-15 15:00\",\"jzrq\":\"2024-01-14\"});"
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val url = mockServer.url("/js/161725.js")
        val raw = HttpClient.getString(url.toString())
        val json = JsonpParser.extractJson(raw)
        val result = Gson().fromJson(json, FundGzResult::class.java)

        val request = mockServer.takeRequest()
        assertEquals("/js/161725.js", request.path)
        assertEquals("161725", result.fundcode)
        assertEquals("招商中证白酒", result.name)
        assertEquals("1.5", result.dwjz)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchShanghaiIndexDate_withStubClient_extractsDate() = runTest {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val parts = (0..30).joinToString("~") { idx -> if (idx == 30) today else "x$idx" }
        val response = "v_sh000001=\"$parts\";"

        HttpClient.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(response.toResponseBody())
                    .build()
            }
            .build()

        val indexDate = FundApi.fetchShanghaiIndexDate()

        assertNotNull(indexDate)
        assertEquals(today, indexDate)
    }

    @Test
    fun fetchStockQuotes_emptyInput_returnsEmptyMap() = runTest {
        val result = FundApi.fetchStockQuotes(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchStockQuotes_shanghaiB9Prefix_usesShSymbol() = runTest {
        HttpClient.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                assertTrue("Should contain s_sh900001", url.contains("s_sh900001"))
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("v_s_sh900001=\"1~测试股~900001~1.00~0.01~1.01~100~50~~\";".toResponseBody())
                    .build()
            }
            .build()

        val result = FundApi.fetchStockQuotes(listOf("900001"))
        assertEquals(1.01, result["900001"]!!, 0.001)
    }

    @Test
    fun fetchStockQuotes_hk5DigitCode_usesHkSymbol() = runTest {
        HttpClient.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                assertTrue("Should contain s_hk00700", url.contains("s_hk00700"))
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("v_s_hk00700=\"1~腾讯控股~00700~350.00~5.00~2.50~100~50~~\";".toResponseBody())
                    .build()
            }
            .build()

        val result = FundApi.fetchStockQuotes(listOf("00700"))
        assertEquals(2.50, result["00700"]!!, 0.001)
    }

    @Test
    fun fetchFundData_withHoldingQuotes_calculatesEstimatedValuationFields() = runTest {
        HttpClient.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("fundgz.1234567.com.cn/js/161725.js") -> {
                        """jsonpgz({"fundcode":"161725","name":"基金161725","dwjz":"1.5000","gsz":"1.5100","gztime":"2024-01-15 14:30","jzrq":"2024-01-15","gszzl":"0.66"});"""
                    }
                    url.contains("qt.gtimg.cn/q=jj161725") -> {
                        ""
                    }
                    url.contains("FundArchivesDatas.aspx?type=jjcc&code=161725") -> {
                        """var apidata={content:"<table><thead><tr><th>股票代码</th><th>股票名称</th><th>占净值比例</th></tr></thead><tbody><tr><td>600519</td><td>贵州茅台</td><td>10.00%</td></tr><tr><td>000858</td><td>五粮液</td><td>5.00%</td></tr></tbody></table>"};"""
                    }
                    url.contains("qt.gtimg.cn/q=s_sh600519,s_sz000858") -> {
                        """
                        v_s_sh600519="1~贵州茅台~600519~1.00~0.00~2.00~0~0~~";
                        v_s_sz000858="1~五粮液~000858~1.00~0.00~-1.00~0~0~~";
                        """.trimIndent()
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

        val fund = FundApi.fetchFundData("161725")

        assertEquals(1.0, fund.estGszzl!!, 0.0001)
        assertEquals(1.515, fund.estGsz!!, 0.0001)
        assertEquals(1.0, fund.estPricedCoverage, 0.0001)
    }

    @Test
    fun fetchFundData_fallbackWithoutDwjz_throws() = runTest {
        HttpClient.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("fundgz.1234567.com.cn/js/999999.js") -> "not_jsonp"
                    url.contains("qt.gtimg.cn/q=jj999999") -> "v_jj999999=\"1~测试基金~999999~x~x~~x~0.00~2024-01-15 15:00:00~\";"
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

        val result = runCatching { FundApi.fetchFundData("999999") }
        assertTrue(result.isFailure)
    }

    @Test
    fun fetchFundData_fallbackUsesTencentName_whenSearchMisses() = runTest {
        HttpClient.client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("fundgz.1234567.com.cn/js/888888.js") -> "not_jsonp"
                    url.contains("qt.gtimg.cn/q=jj888888") -> "v_jj888888=\"1~腾讯基金名~888888~x~x~1.2345~x~0.12~2024-01-15 15:00:00~\";"
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

        val fund = FundApi.fetchFundData("888888")
        assertEquals("腾讯基金名", fund.name)
        assertEquals("1.2345", fund.dwjz)
        assertTrue(fund.noValuation)
    }
}
