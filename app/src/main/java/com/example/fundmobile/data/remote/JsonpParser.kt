package com.example.fundmobile.data.remote

object JsonpParser {
    fun extractJson(response: String): String {
        val start = response.indexOf('(')
        val end = response.lastIndexOf(')')
        if (start == -1 || end == -1 || end <= start) {
            throw IllegalArgumentException("Invalid JSONP response")
        }
        return response.substring(start + 1, end)
    }

    fun extractVarContent(response: String, varName: String): String? {
        val match = Regex("""$varName\s*=\s*\{""").find(response) ?: return null
        val openBrace = response.indexOf('{', match.range.first)
        if (openBrace < 0) return null

        var depth = 1
        var i = openBrace + 1
        while (i < response.length && depth > 0) {
            when (response[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        if (depth != 0) return null
        return response.substring(openBrace, i)
    }

    fun extractQuotedValue(response: String, varName: String): String? {
        val pattern = Regex("""$varName\s*=\s*"([^"]*)"""")
        val match = pattern.find(response) ?: return null
        return match.groupValues[1]
    }
}
