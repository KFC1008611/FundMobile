package com.example.fundmobile.data.remote

import com.example.fundmobile.data.model.FundData
import com.example.fundmobile.data.model.NavHistoryEntry
import com.example.fundmobile.data.model.SearchResult
import com.example.fundmobile.data.model.StockHolding
import com.google.gson.Gson
import kotlin.math.abs
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.Jsoup

object FundApi {
    private val gson = Gson()
    private val chinaZone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private const val HISTORY_BASE_URL = "https://fundf10.eastmoney.com/F10DataApi.aspx"
    private const val PER_PAGE_LIMIT = 49
    const val MAX_HISTORY_PAGES = 20

    suspend fun fetchFundGz(code: String): FundGzResult? {
        return runCatching {
            val url = "https://fundgz.1234567.com.cn/js/$code.js?rt=${System.currentTimeMillis()}"
            val raw = HttpClient.getString(url)
            val json = JsonpParser.extractJson(raw)
            gson.fromJson(json, FundGzResult::class.java)
        }.getOrNull()
    }

    suspend fun fetchTencentFundQuote(code: String): TencentFundQuote? {
        return runCatching {
            val raw = HttpClient.getStringAutoCharset("https://qt.gtimg.cn/q=jj$code")
            val value = JsonpParser.extractQuotedValue(raw, "v_jj$code") ?: return@runCatching null
            val parts = value.split("~")
            val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            val dwjz = parts.getOrNull(5)?.takeIf { it.isNotBlank() }
            val zzl = parts.getOrNull(7)?.toDoubleOrNull()
            val jzrq = parts.getOrNull(8)?.take(10)?.takeIf { it.isNotBlank() }
            TencentFundQuote(name = name, dwjz = dwjz, zzl = zzl, jzrq = jzrq)
        }.getOrNull()
    }

    suspend fun fetchFundHoldings(code: String): List<StockHolding> {
        return runCatching {
            val url = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=jjcc&code=$code&topline=10&year=&month=&_=${System.currentTimeMillis()}"
            val raw = HttpClient.getString(url)
            val varContent = JsonpParser.extractVarContent(raw, "apidata") ?: return@runCatching emptyList()
            val html = extractContentField(varContent) ?: return@runCatching emptyList()
            parseHoldingsHtml(html)
        }.getOrElse { emptyList() }
    }

    suspend fun fetchStockQuotes(stockCodes: List<String>): Map<String, Double> {
        if (stockCodes.isEmpty()) return emptyMap()

        val symbolToCode = stockCodes.mapNotNull { code ->
            val symbol = toTencentStockSymbol(code) ?: return@mapNotNull null
            symbol to code
        }.toMap()

        if (symbolToCode.isEmpty()) return emptyMap()

        return runCatching {
            val query = symbolToCode.keys.joinToString(",")
            val raw = HttpClient.getStringAutoCharset("https://qt.gtimg.cn/q=$query")
            buildMap {
                symbolToCode.forEach { (symbol, code) ->
                    val value = JsonpParser.extractQuotedValue(raw, "v_$symbol") ?: return@forEach
                    val change = value.split("~").getOrNull(5)?.toDoubleOrNull() ?: return@forEach
                    put(code, change)
                }
            }
        }.getOrElse { emptyMap() }
    }

    suspend fun searchFunds(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            val callback = "cb"
            val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
            val url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=$encoded&callback=$callback&_=${System.currentTimeMillis()}"
            val raw = HttpClient.getString(url)
            val json = JsonpParser.extractJson(raw)
            val wrapper = gson.fromJson(json, SearchWrapper::class.java)
            wrapper.Datas.orEmpty().filter {
                val category = it.CATEGORY?.toString()
                category == "700" || it.CATEGORYDESC == "基金"
            }
        }.getOrElse { emptyList() }
    }

    suspend fun fetchFundNetValue(code: String, date: String): Double? {
        return runCatching {
            val url = "https://fundf10.eastmoney.com/F10DataApi.aspx?type=lsjz&code=$code&page=1&per=1&sdate=$date&edate=$date"
            val raw = HttpClient.getString(url)
            val varContent = JsonpParser.extractVarContent(raw, "apidata") ?: return@runCatching null
            val html = extractContentField(varContent) ?: return@runCatching null
            parseNetValueFromHtml(html, date)
        }.getOrNull()
    }

    /**
     * 分页拉取基金历史净值。
     * EastMoney API per 参数最大有效值为 49，超过会回退到默认 ~20 条。
     * 本方法自动翻页合并所有数据，[maxPages] 防止无限翻页。
     */
    suspend fun fetchFundNetHistory(
        code: String,
        startDate: String? = null,
        endDate: String? = null,
        maxPages: Int = MAX_HISTORY_PAGES
    ): List<NavHistoryEntry> {
        return runCatching {
            val allEntries = mutableListOf<NavHistoryEntry>()
            var currentPage = 1
            var totalPages = 1

            while (currentPage <= totalPages && currentPage <= maxPages) {
                val url = buildString {
                    append("$HISTORY_BASE_URL?type=lsjz&code=$code&page=$currentPage&per=$PER_PAGE_LIMIT")
                    if (startDate != null) append("&sdate=$startDate")
                    if (endDate != null) append("&edate=$endDate")
                }
                val raw = HttpClient.getString(url)
                val varContent = JsonpParser.extractVarContent(raw, "apidata") ?: break
                val html = extractContentField(varContent) ?: break

                val entries = parseNetHistoryHtml(html)
                if (entries.isEmpty()) break
                allEntries.addAll(entries)

                if (currentPage == 1) {
                    totalPages = parseTotalPages(varContent)
                }
                currentPage++
            }
            allEntries
        }.getOrElse { emptyList() }
    }

    internal fun parseTotalPages(varContent: String): Int {
        val match = Regex("""pages\s*:\s*(\d+)""").find(varContent)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }

    suspend fun fetchSmartFundNetValue(code: String, startDate: String): Pair<String, Double>? {
        val start = runCatching { LocalDate.parse(startDate, dateFmt) }.getOrNull() ?: return null
        val today = LocalDate.now(chinaZone)
        var current = start

        repeat(30) {
            if (current.isAfter(today)) return null
            val dateStr = current.format(dateFmt)
            val value = fetchFundNetValue(code, dateStr)
            if (value != null) return dateStr to value
            current = current.plusDays(1)
        }
        return null
    }

    suspend fun fetchShanghaiIndexDate(): String? {
        return runCatching {
            val raw = HttpClient.getStringAutoCharset("https://qt.gtimg.cn/q=sh000001&_t=${System.currentTimeMillis()}")
            val value = JsonpParser.extractQuotedValue(raw, "v_sh000001") ?: return@runCatching null
            value.split("~").getOrNull(30)?.take(8)?.takeIf { it.length == 8 }
        }.getOrNull()
    }

    suspend fun fetchFundData(code: String): FundData {
        val gzData = fetchFundGz(code)
        if (gzData?.fundcode.isNullOrBlank()) {
            return fetchFundDataFallback(code)
        }

        return coroutineScope {
            val tencentDeferred = async { fetchTencentFundQuote(code) }
            val holdingsDeferred = async {
                val holdings = fetchFundHoldings(code)
                if (holdings.isEmpty()) return@async holdings
                val quotes = fetchStockQuotes(holdings.map { it.code })
                holdings.map { holding ->
                    val change = quotes[holding.code]
                    holding.copy(change = change)
                }
            }

            val tencent = runCatching { tencentDeferred.await() }.getOrNull()
            val holdings = runCatching { holdingsDeferred.await() }.getOrDefault(emptyList())

            var dwjz = gzData?.dwjz
            var jzrq = gzData?.jzrq
            var zzl: Double? = null

            if (!tencent?.dwjz.isNullOrBlank()) {
                val tencentDate = tencent?.jzrq
                val baseDate = jzrq
                val shouldUseTencent = !tencentDate.isNullOrBlank() &&
                    (baseDate.isNullOrBlank() || tencentDate >= baseDate)
                if (shouldUseTencent) {
                    dwjz = tencent?.dwjz
                    jzrq = tencentDate
                    zzl = tencent?.zzl
                }
            }

            val estimate = calculateEstimatedValuation(
                baseNav = gzData?.dwjz?.toDoubleOrNull(),
                holdings = holdings
            )

            FundData(
                code = gzData?.fundcode ?: code,
                name = gzData?.name ?: code,
                dwjz = dwjz,
                gsz = gzData?.gsz,
                gztime = gzData?.gztime,
                jzrq = jzrq,
                gszzl = gzData?.gszzl?.toDoubleOrNull(),
                zzl = zzl,
                estGsz = estimate?.first,
                estGszzl = estimate?.second,
                estPricedCoverage = estimate?.third ?: 0.0,
                noValuation = false,
                holdings = holdings
            )
        }
    }

    suspend fun fetchFundDataFallback(code: String): FundData {
        val quote = fetchTencentFundQuote(code)
        val dwjz = quote?.dwjz?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("未能获取到基金数据")
        val name = searchFunds(code).firstOrNull { it.CODE == code }?.NAME
            ?: quote.name
            ?: "未知基金($code)"
        return FundData(
            code = code,
            name = name,
            dwjz = dwjz,
            gsz = null,
            gztime = null,
            jzrq = quote?.jzrq,
            gszzl = null,
            zzl = quote?.zzl,
            estGsz = null,
            estGszzl = null,
            estPricedCoverage = 0.0,
            noValuation = true,
            holdings = emptyList()
        )
    }

    private fun calculateEstimatedValuation(
        baseNav: Double?,
        holdings: List<StockHolding>
    ): Triple<Double, Double, Double>? {
        val nav = baseNav ?: return null
        if (nav <= 0.0 || holdings.isEmpty()) return null

        var weightedChangeSum = 0.0
        var weightedCoverage = 0.0
        var totalWeight = 0.0
        var knownWeight = 0.0

        holdings.forEach { holding ->
            val weight = parseWeightPercent(holding.weight) ?: return@forEach
            if (weight <= 0.0) return@forEach
            totalWeight += weight

            val change = holding.change
            if (change != null && change.isFinite()) {
                weightedChangeSum += weight * change
                knownWeight += weight
            }
            weightedCoverage += weight
        }

        if (knownWeight <= 0.0 || weightedCoverage <= 0.0) return null

        val estimatedRate = weightedChangeSum / knownWeight
        val estimatedNav = nav * (1.0 + estimatedRate / 100.0)
        val coverage = if (totalWeight > 0.0) {
            (knownWeight / totalWeight).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        if (!estimatedNav.isFinite() || abs(estimatedNav) <= 1e-12) return null
        return Triple(estimatedNav, estimatedRate, coverage)
    }

    private fun parseWeightPercent(weightText: String): Double? {
        if (weightText.isBlank()) return null
        val match = Regex("([\\d.]+)").find(weightText) ?: return null
        return match.groupValues.getOrNull(1)?.toDoubleOrNull()
    }

    private fun parseHoldingsHtml(html: String): List<StockHolding> {
        if (html.isBlank() || html.contains("暂无数据")) return emptyList()
        return runCatching {
            val document = Jsoup.parse(html)
            val headers = document.select("thead tr th").map { it.text().replace("\\s+".toRegex(), "") }
            var idxCode = -1
            var idxName = -1
            var idxWeight = -1

            headers.forEachIndexed { index, text ->
                if (idxCode < 0 && (text.contains("股票代码") || text.contains("证券代码"))) idxCode = index
                if (idxName < 0 && (text.contains("股票名称") || text.contains("证券名称"))) idxName = index
                if (idxWeight < 0 && (text.contains("占净值比例") || text.contains("占比"))) idxWeight = index
            }

            val rows = document.select("tbody tr").ifEmpty { document.select("tr") }
            rows.mapNotNull { row ->
                val cells = row.select("td")
                if (cells.isEmpty()) return@mapNotNull null
                val texts = cells.map { it.text().trim() }

                val codeText = when {
                    idxCode >= 0 && idxCode < texts.size -> texts[idxCode]
                    else -> texts.firstOrNull { Regex("^\\d{6}$").matches(it) }
                }.orEmpty()

                val code = Regex("(\\d{6})").find(codeText)?.groupValues?.getOrNull(1) ?: codeText

                val name = when {
                    idxName >= 0 && idxName < texts.size -> texts[idxName]
                    else -> if (code.isNotBlank()) {
                        texts.firstOrNull { it.isNotBlank() && it != code && !it.contains("%") }.orEmpty()
                    } else {
                        ""
                    }
                }

                val weightText = when {
                    idxWeight >= 0 && idxWeight < texts.size -> texts[idxWeight]
                    else -> texts.firstOrNull { it.contains("%") }.orEmpty()
                }
                val weight = Regex("([\\d.]+)\\s*%")
                    .find(weightText)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let { "$it%" }
                    ?: weightText

                if (code.isBlank() && name.isBlank() && weight.isBlank()) {
                    null
                } else {
                    StockHolding(code = code, name = name, weight = weight, change = null)
                }
            }.take(10)
        }.getOrElse { emptyList() }
    }

    private fun parseNetValueFromHtml(html: String, date: String): Double? {
        if (html.isBlank() || html.contains("暂无数据")) return null
        return runCatching {
            val document = Jsoup.parse(html)
            for (row in document.select("tr")) {
                val tds = row.select("td")
                if (tds.size >= 2 && tds[0].text().trim() == date) {
                    return@runCatching tds[1].text().trim().toDoubleOrNull()
                }
            }
            null
        }.getOrNull()
    }

    private fun parseNetHistoryHtml(html: String): List<NavHistoryEntry> {
        if (html.isBlank() || html.contains("暂无数据")) return emptyList()
        return runCatching {
            val document = Jsoup.parse(html)
            val rows = document.select("tbody tr").ifEmpty { document.select("tr") }
            rows.mapNotNull { row ->
                val tds = row.select("td")
                if (tds.size < 2) return@mapNotNull null
                val date = tds[0].text().trim()
                val nav = tds[1].text().trim().toDoubleOrNull() ?: return@mapNotNull null
                if (date.isBlank()) return@mapNotNull null
                NavHistoryEntry(date = date, nav = nav)
            }
        }.getOrElse { emptyList() }
    }

    private fun extractContentField(varContent: String): String? {
        val regex = Regex("""content\s*:\s*"((?:\\\\.|[^"\\\\])*)""", RegexOption.DOT_MATCHES_ALL)
        val raw = regex.find(varContent)?.groupValues?.getOrNull(1) ?: return null
        return runCatching {
            gson.fromJson("\"$raw\"", String::class.java)
        }.recoverCatching {
            raw.replace("\\\\\"", "\"").replace("\\\\/", "/")
        }.getOrNull()
    }

    private fun toTencentStockSymbol(code: String): String? {
        val clean = code.trim()
        if (Regex("^\\d{5}$").matches(clean)) return "s_hk$clean"
        if (!Regex("^\\d{6}$").matches(clean)) return null
        return when {
            clean.startsWith("6") || clean.startsWith("9") -> "s_sh$clean"
            clean.startsWith("0") || clean.startsWith("3") -> "s_sz$clean"
            clean.startsWith("4") || clean.startsWith("8") -> "s_bj$clean"
            else -> "s_sz$clean"
        }
    }

    private data class SearchWrapper(val Datas: List<SearchResult>?)
}

data class FundGzResult(
    val fundcode: String?,
    val name: String?,
    val dwjz: String?,
    val gsz: String?,
    val gztime: String?,
    val jzrq: String?,
    val gszzl: String?
)

data class TencentFundQuote(
    val name: String?,
    val dwjz: String?,
    val zzl: Double?,
    val jzrq: String?
)
