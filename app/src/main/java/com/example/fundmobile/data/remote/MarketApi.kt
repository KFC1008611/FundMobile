package com.example.fundmobile.data.remote

import com.example.fundmobile.data.model.GoldChartPoint
import com.example.fundmobile.data.model.GoldHistory
import com.example.fundmobile.data.model.MarketIndex
import com.example.fundmobile.data.model.MetalPrice
import com.example.fundmobile.data.model.NewsItem
import com.example.fundmobile.data.model.ShangHaiMinute
import com.example.fundmobile.data.model.VolumeData
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object MarketApi {
    private val gson = Gson()

    private val goldHeaders = mapOf(
        "accept" to "*/*",
        "accept-language" to "zh-CN,zh;q=0.9",
        "referer" to "https://quote.cngold.org/gjs/gjhj.html",
        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"
    )

    private val baiduHeaders = mapOf(
        "accept" to "application/vnd.finance-web.v1+json",
        "accept-language" to "zh-CN,zh;q=0.9",
        "acs-token" to "1769925606098_1770001866425_B6lkFxZg0PzQhmCXjMfTJUxYBn+en+J7W6a8XGyGMqfxPfIv2RgeZG8wimRzlhAxlZlErxq7wN5rVnCfPj6s/UNiA1a1hfyItpnMrru1lzDxUcicsi2ngKjmVCdUfqRZTcHPnfDWrt4phJcS7Ue+Sh6Ru/GVG+1McDUmf/d52zDv5Q6QM7CAJfHDqsCMP65SNjo63Xljm+aAIzDzKErfG+LOR706MJaZGY2o/hGcESyOy3FcWv+pYNFUjpV3M5sMFNEDa50fWh4J9PZpQDxDQLNhr9LSYunQUxe6wtNEGds85p9V6/yU6v+jA9q0h9/OyQJ/ZuD1lP0VPEACEc4qJvfItxhuK9MfKM+j6Spc/N6Qomh6pZYt6iLJjJp652xIqZurCmxem2Z3Vqu+mcZ9FN1l0qU6dx4hkaTZk3850FE/n6YW+HL74Mp8L+YR/Q2VMV3ARkSzPHgOS9iA6rBAaBiJf2Ni/BTHNSyFxJJjazI=",
        "origin" to "https://gushitong.baidu.com",
        "priority" to "u=1, i",
        "referer" to "https://gushitong.baidu.com/",
        "sec-ch-ua" to "\"Google Chrome\";v=\"143\", \"Chromium\";v=\"143\", \"Not A(Brand\";v=\"24\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"Windows\"",
        "sec-fetch-dest" to "empty",
        "sec-fetch-mode" to "cors",
        "sec-fetch-site" to "same-site",
        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
    )

    private val warmupHeaders = mapOf(
        "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
        "referer" to "https://gushitong.baidu.com/"
    )

    @Volatile
    private var warmedUp = false

    suspend fun warmUpBaiduSession() {
        if (warmedUp) return
        runCatching {
            HttpClient.getStringWithCookieClient(
                "https://gushitong.baidu.com/index/ab-000001",
                warmupHeaders
            )
        }
        warmedUp = true
    }

    private suspend fun baiduGet(url: String): String {
        warmUpBaiduSession()
        return HttpClient.getStringWithCookieClient(url, baiduHeaders)
    }

    suspend fun fetchRealTimeMetals(): List<MetalPrice> {
        return runCatching {
            val url = "https://api.jijinhao.com/quoteCenter/realTime.htm?codes=JO_71,JO_92233,JO_92232&_=${System.currentTimeMillis()}"
            val raw = HttpClient.getString(url, goldHeaders)
            val json = raw.removePrefix("var quote_json = ").trimEnd(';')
            val obj = JsonParser.parseString(json).asJsonObject

            val codes = listOf("JO_71", "JO_92233", "JO_92232")
            codes.mapNotNull { code ->
                val item = obj.getAsJsonObject(code) ?: return@mapNotNull null
                val timestamp = item.get("time")?.asLong ?: 0L
                val timeStr = formatTimestamp(timestamp)
                MetalPrice(
                    name = item.get("showName")?.asString ?: code,
                    price = formatNum(item, "q63"),
                    changeAmount = formatNum(item, "q70"),
                    changePct = formatNum(item, "q80") + "%",
                    openPrice = formatNum(item, "q1"),
                    highPrice = formatNum(item, "q3"),
                    lowPrice = formatNum(item, "q4"),
                    prevClose = formatNum(item, "q2"),
                    updateTime = timeStr,
                    unit = item.get("unit")?.asString ?: ""
                )
            }
        }.getOrElse { emptyList() }
    }

    suspend fun fetchGoldHistory(): List<GoldHistory> {
        return runCatching {
            coroutineScope {
                val chinaGoldDef = async { fetchHistoryData("JO_52683") }
                val chowTaiFookDef = async { fetchHistoryData("JO_42660") }

                val chinaGold = chinaGoldDef.await()
                val chowTaiFook = chowTaiFookDef.await()

                chinaGold.mapIndexed { index, item ->
                    val ctf = chowTaiFook.getOrNull(index)
                    GoldHistory(
                        date = formatTimestamp(item.get("time")?.asLong ?: 0L, "yyyy-MM-dd"),
                        chinaGoldPrice = formatNum(item, "q1"),
                        chowTaiFookPrice = ctf?.let { formatNum(it, "q1") } ?: "N/A",
                        chinaGoldChange = formatNum(item, "q70"),
                        chowTaiFookChange = ctf?.let { formatNum(it, "q70") } ?: "N/A"
                    )
                }.reversed()
            }
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchHistoryData(code: String): List<JsonObject> {
        val url = "https://api.jijinhao.com/quoteCenter/history.htm?code=$code&style=3&pageSize=10&needField=128,129,70&currentPage=1&_=${System.currentTimeMillis()}"
        val raw = HttpClient.getString(url, goldHeaders)
        val json = raw.removePrefix("var quote_json = ").trimEnd(';')
        val obj = JsonParser.parseString(json).asJsonObject
        val dataArray = obj.getAsJsonArray("data") ?: return emptyList()
        return dataArray.map { it.asJsonObject }
    }

    suspend fun fetchGoldIntraday(): List<GoldChartPoint> {
        return runCatching {
            val url = "https://api.jijinhao.com/sQuoteCenter/todayMin.htm?code=JO_92233&isCalc=true&_=${System.currentTimeMillis()}"
            val raw = HttpClient.getString(url, goldHeaders)
            val json = raw.removePrefix("var hq_str_ml = ").trimEnd(';')
            val obj = JsonParser.parseString(json).asJsonObject
            val dataArray = obj.getAsJsonArray("data") ?: return@runCatching emptyList()
            dataArray.mapNotNull { element ->
                val item = element.asJsonObject
                val price = item.get("price")?.asDouble ?: return@mapNotNull null
                if (price == -1.0) return@mapNotNull null
                val timestamp = item.get("date")?.asLong ?: return@mapNotNull null
                GoldChartPoint(
                    date = formatTimestamp(timestamp, "HH:mm"),
                    price = price
                )
            }
        }.getOrElse { emptyList() }
    }

    suspend fun fetchGlobalIndices(): List<MarketIndex> {
        return runCatching {
            val result = mutableListOf<MarketIndex>()

            for (market in listOf("asia", "america")) {
                val url = "https://finance.pae.baidu.com/api/getbanner?market=$market&finClientType=pc"
                val raw = baiduGet(url)
                val root = JsonParser.parseString(raw).asJsonObject
                if (root.get("ResultCode")?.asString != "0") continue
                val list = root.getAsJsonObject("Result")?.getAsJsonArray("list") ?: continue
                for (item in list) {
                    val obj = item.asJsonObject
                    result.add(
                        MarketIndex(
                            name = obj.get("name")?.asString ?: "",
                            value = obj.get("lastPrice")?.asString ?: "",
                            changePct = obj.get("ratio")?.asString ?: "0%"
                        )
                    )
                }
            }

            // 插入创业板指到第3位
            val cyb = fetchCybIndex()
            if (cyb != null && result.size >= 2) {
                result.add(2, cyb)
            } else if (cyb != null) {
                result.add(cyb)
            }

            result.toList()
        }.getOrElse { emptyList() }
    }

    private suspend fun fetchCybIndex(): MarketIndex? {
        return runCatching {
            val url = "https://finance.pae.baidu.com/vapi/v1/getquotation?srcid=5353&all=1&pointType=string&group=quotation_index_minute&query=399006&code=399006&market_type=ab&newFormat=1&name=创业板指&finClientType=pc"
            val raw = baiduGet(url)
            val root = JsonParser.parseString(raw).asJsonObject
            if (root.get("ResultCode")?.asString != "0") return@runCatching null
            val cur = root.getAsJsonObject("Result")?.getAsJsonObject("cur") ?: return@runCatching null
            MarketIndex(
                name = "创业板指",
                value = cur.get("price")?.asString ?: "",
                changePct = cur.get("ratio")?.asString ?: "0%"
            )
        }.getOrNull()
    }

    suspend fun fetchShanghaiMinute(): List<ShangHaiMinute> {
        return runCatching {
            val url = "https://finance.pae.baidu.com/vapi/v1/getquotation?srcid=5353&all=1&pointType=string&group=quotation_index_minute&query=000001&code=000001&market_type=ab&newFormat=1&name=上证指数&finClientType=pc"
            val raw = baiduGet(url)
            val root = JsonParser.parseString(raw).asJsonObject
            if (root.get("ResultCode")?.asString != "0") return@runCatching emptyList()
            val marketDataStr = root.getAsJsonObject("Result")
                ?.getAsJsonObject("newMarketData")
                ?.getAsJsonArray("marketData")
                ?.get(0)?.asJsonObject
                ?.get("p")?.asString ?: return@runCatching emptyList()

            marketDataStr.split(";").mapNotNull { segment ->
                val parts = segment.split(",")
                if (parts.size < 7) return@mapNotNull null
                val fields = parts.drop(1)
                val volume = runCatching {
                    val v = fields.getOrNull(4)?.toDouble() ?: 0.0
                    "%.2f万手".format(v / 10000)
                }.getOrDefault(fields.getOrElse(4) { "" })

                val amount = runCatching {
                    val a = fields.getOrNull(5)?.toDouble() ?: 0.0
                    "%.2f亿".format(a / 10000 / 10000)
                }.getOrDefault(fields.getOrElse(5) { "" })

                ShangHaiMinute(
                    time = fields.getOrElse(0) { "" },
                    price = fields.getOrElse(1) { "" },
                    changeAmount = fields.getOrElse(2) { "" },
                    changePct = fields.getOrElse(3) { "" } + "%",
                    volume = volume,
                    amount = amount
                )
            }
        }.getOrElse { emptyList() }
    }

    suspend fun fetchVolumeData(): List<VolumeData> {
        return runCatching {
            val url = "https://finance.pae.baidu.com/sapi/v1/metrictrend?financeType=index&market=ab&code=000001&targetType=market&metric=amount&finClientType=pc"
            val raw = baiduGet(url)
            val root = JsonParser.parseString(raw).asJsonObject
            if (root.get("ResultCode")?.asString != "0") return@runCatching emptyList()
            val trend = root.getAsJsonObject("Result")?.getAsJsonArray("trend")
                ?: return@runCatching emptyList()

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            sdf.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            val dates = (0..7).map { sdf.format(Date(System.currentTimeMillis() - it * 86400000L)) }

            val result = mutableListOf<VolumeData>()
            for (dateStr in dates) {
                val total = findAmount(trend.get(0)?.asJsonObject, dateStr)
                val sh = findAmount(trend.get(1)?.asJsonObject, dateStr)
                val sz = findAmount(trend.get(2)?.asJsonObject, dateStr)
                val bj = findAmount(trend.get(3)?.asJsonObject, dateStr)
                if (total != null) {
                    result.add(VolumeData(dateStr, "${total}亿", "${sh}亿", "${sz}亿", "${bj}亿"))
                }
            }
            result.reversed()
        }.getOrElse { emptyList() }
    }

    private fun findAmount(trendItem: JsonObject?, date: String): String? {
        val content = trendItem?.getAsJsonArray("content") ?: return null
        for (entry in content) {
            val obj = entry.asJsonObject
            if (obj.get("marketDate")?.asString == date) {
                return obj.getAsJsonObject("data")?.get("amount")?.asString
            }
        }
        return null
    }

    suspend fun fetchNews(count: Int = 30): List<NewsItem> {
        return runCatching {
            val url = "https://finance.pae.baidu.com/selfselect/expressnews?rn=$count&pn=0&tag=A股&finClientType=pc"
            val raw = baiduGet(url)
            val root = JsonParser.parseString(raw).asJsonObject
            if (root.get("ResultCode")?.asString != "0") return@runCatching emptyList()
            val list = root.getAsJsonObject("Result")
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("list") ?: return@runCatching emptyList()

            list.mapNotNull { element ->
                val item = element.asJsonObject
                var title = item.get("title")?.asString ?: ""
                if (title.isBlank()) {
                    title = item.getAsJsonObject("content")
                        ?.getAsJsonArray("items")
                        ?.get(0)?.asJsonObject
                        ?.get("data")?.asString ?: ""
                }
                if (title.isBlank()) return@mapNotNull null

                val publishTime = item.get("publish_time")?.asString ?: ""
                val timeStr = runCatching {
                    formatTimestamp(publishTime.toLong() * 1000, "HH:mm:ss")
                }.getOrDefault(publishTime)

                val evaluate = item.get("evaluate")?.asString ?: ""

                NewsItem(
                    time = timeStr,
                    content = title,
                    evaluate = evaluate
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun formatTimestamp(millis: Long, pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
        if (millis <= 0) return ""
        val sdf = SimpleDateFormat(pattern, Locale.CHINA)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        return sdf.format(Date(millis))
    }

    private fun formatNum(obj: JsonObject, key: String): String {
        val element = obj.get(key) ?: return "N/A"
        return if (element.isJsonPrimitive) {
            val prim = element.asJsonPrimitive
            if (prim.isNumber) {
                val d = prim.asDouble
                if (d == d.toLong().toDouble()) d.toLong().toString()
                else "%.2f".format(d)
            } else {
                prim.asString
            }
        } else {
            "N/A"
        }
    }
}
