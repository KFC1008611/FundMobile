package com.example.fundmobile.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonpParserTest {

    @Test
    fun extractJson_normalJsonp_returnsJson() {
        val input = "jsonpgz({\"fundcode\":\"161725\",\"name\":\"招商中证白酒\",\"dwjz\":\"1.5\",\"gsz\":\"1.52\",\"gszzl\":\"1.33\",\"gztime\":\"2024-01-15 15:00\",\"jzrq\":\"2024-01-14\"});"
        val expected = "{\"fundcode\":\"161725\",\"name\":\"招商中证白酒\",\"dwjz\":\"1.5\",\"gsz\":\"1.52\",\"gszzl\":\"1.33\",\"gztime\":\"2024-01-15 15:00\",\"jzrq\":\"2024-01-14\"}"
        assertEquals(expected, JsonpParser.extractJson(input))
    }

    @Test
    fun extractJson_callbackWithName_returnsJson() {
        val input = "cb({\"Datas\":[{\"CODE\":\"161725\",\"NAME\":\"招商中证白酒\"}]})"
        val expected = "{\"Datas\":[{\"CODE\":\"161725\",\"NAME\":\"招商中证白酒\"}]}"
        assertEquals(expected, JsonpParser.extractJson(input))
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractJson_noParens_throwsException() {
        JsonpParser.extractJson("just plain text")
    }

    @Test
    fun extractJson_emptyParens_returnsEmptyString() {
        assertEquals("", JsonpParser.extractJson("func()"))
    }

    @Test
    fun extractJson_nestedParens_returnsCorrectJson() {
        val input = "callback({\"key\":\"val(ue)\"})"
        val expected = "{\"key\":\"val(ue)\"}"
        assertEquals(expected, JsonpParser.extractJson(input))
    }

    @Test
    fun extractVarContent_normalApidata_returnsContent() {
        val input = "var apidata={ content:\"<table>...</table>\",records:10,pages:1,curpage:1};"
        val expected = "{ content:\"<table>...</table>\",records:10,pages:1,curpage:1}"
        assertEquals(expected, JsonpParser.extractVarContent(input, "apidata"))
    }

    @Test
    fun extractVarContent_noMatch_returnsNull() {
        assertNull(JsonpParser.extractVarContent("var other = {}", "apidata"))
    }

    @Test
    fun extractVarContent_nestedBraces_handlesCorrectly() {
        val input = "var apidata={a:{b:{c:1}},d:2};"
        assertEquals("{a:{b:{c:1}},d:2}", JsonpParser.extractVarContent(input, "apidata"))
    }

    @Test
    fun extractQuotedValue_tencentQuote_returnsValue() {
        val input = "v_jj161725=\"1~招商中证白酒~161725~1.52~0.02~1.33~2024-01-15~2024-01-14~1.50\";"
        val expected = "1~招商中证白酒~161725~1.52~0.02~1.33~2024-01-15~2024-01-14~1.50"
        assertEquals(expected, JsonpParser.extractQuotedValue(input, "v_jj161725"))
    }

    @Test
    fun extractQuotedValue_emptyQuoted_returnsEmpty() {
        val input = "v_jj000000=\"\";"
        assertEquals("", JsonpParser.extractQuotedValue(input, "v_jj000000"))
    }

    @Test
    fun extractQuotedValue_noMatch_returnsNull() {
        val input = "something_else=\"abc\""
        assertNull(JsonpParser.extractQuotedValue(input, "v_jj999999"))
    }
}
