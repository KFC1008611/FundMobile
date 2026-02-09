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
}
