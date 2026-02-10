package com.example.fundmobile.data.remote

import com.example.fundmobile.data.model.StockHolding
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FundApiParsingTest {

    @Test
    fun parseHoldingsHtml_realEastmoneyFormat() {
        val html = """
            <table>
              <thead>
                <tr><th>序号</th><th>股票代码</th><th>股票名称</th><th>占净值比例</th></tr>
              </thead>
              <tbody>
                <tr><td>1</td><td>600519</td><td>贵州茅台</td><td>9.87%</td></tr>
                <tr><td>2</td><td>000858</td><td>五粮液</td><td>8.12%</td></tr>
              </tbody>
            </table>
        """.trimIndent()

        val result = parseHoldingsHtmlLikeFundApi(html)

        assertEquals(2, result.size)
        assertEquals(StockHolding("600519", "贵州茅台", "9.87%", null), result[0])
        assertEquals(StockHolding("000858", "五粮液", "8.12%", null), result[1])
    }

    @Test
    fun parseHoldingsHtml_emptyTable_returnsEmpty() {
        val html = "<table><thead><tr><th>股票代码</th></tr></thead><tbody></tbody></table>"
        assertEquals(emptyList<StockHolding>(), parseHoldingsHtmlLikeFundApi(html))
    }

    @Test
    fun parseHoldingsHtml_withNoDataMessage_returnsEmpty() {
        val html = "<div>暂无数据</div>"
        assertEquals(emptyList<StockHolding>(), parseHoldingsHtmlLikeFundApi(html))
    }

    @Test
    fun parseNetValueFromHtml_findsCorrectDate() {
        val html = """
            <table>
              <tr><th>净值日期</th><th>单位净值</th></tr>
              <tr><td>2024-01-14</td><td>1.4010</td></tr>
              <tr><td>2024-01-15</td><td>1.5000</td></tr>
            </table>
        """.trimIndent()

        val result = parseNetValueFromHtmlLikeFundApi(html, "2024-01-15")
        assertNotNull(result)
        assertEquals(1.5, result!!, 1e-6)
    }

    @Test
    fun parseNetValueFromHtml_noData_returnsNull() {
        val html = "<div>暂无数据</div>"
        assertNull(parseNetValueFromHtmlLikeFundApi(html, "2024-01-15"))
    }

    @Test
    fun toTencentStockSymbol_shanghaiCode() {
        assertEquals("s_sh600000", toTencentStockSymbolLikeFundApi("600000"))
    }

    @Test
    fun toTencentStockSymbol_shenzhenCode() {
        assertEquals("s_sz000001", toTencentStockSymbolLikeFundApi("000001"))
    }

    @Test
    fun toTencentStockSymbol_bseCode() {
        assertEquals("s_bj430047", toTencentStockSymbolLikeFundApi("430047"))
    }

    @Test
    fun toTencentStockSymbol_unknown6DigitPrefix_defaultsToShenzhen() {
        assertEquals("s_sz200001", toTencentStockSymbolLikeFundApi("200001"))
    }

    @Test
    fun toTencentStockSymbol_invalidCode() {
        assertNull(toTencentStockSymbolLikeFundApi("abc"))
    }

    @Test
    fun parseHoldingsHtml_withoutCodeAndNameHeader_doesNotInferName() {
        val html = """
            <table>
              <thead>
                <tr><th>序号</th><th>说明</th><th>占净值比例</th></tr>
              </thead>
              <tbody>
                <tr><td>1</td><td>说明文本</td><td>9.87%</td></tr>
              </tbody>
            </table>
        """.trimIndent()

        val result = parseHoldingsHtmlLikeFundApi(html)
        assertEquals(1, result.size)
        assertEquals("", result[0].code)
        assertEquals("", result[0].name)
        assertEquals("9.87%", result[0].weight)
    }

    private fun parseHoldingsHtmlLikeFundApi(html: String): List<StockHolding> {
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

    private fun parseNetValueFromHtmlLikeFundApi(html: String, date: String): Double? {
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

    private fun toTencentStockSymbolLikeFundApi(code: String): String? {
        val clean = code.trim()
        if (!Regex("^\\d{6}$").matches(clean)) return null
        return when {
            clean.startsWith("6") || clean.startsWith("9") -> "s_sh$clean"
            clean.startsWith("0") || clean.startsWith("3") -> "s_sz$clean"
            clean.startsWith("4") || clean.startsWith("8") -> "s_bj$clean"
            else -> "s_sz$clean"
        }
    }
}
