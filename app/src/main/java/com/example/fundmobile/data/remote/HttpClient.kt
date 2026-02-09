package com.example.fundmobile.data.remote

import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object HttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getString(url: String, charset: Charset = Charsets.UTF_8): String {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: throw IOException("Empty body")
                String(bytes, charset)
            }
        }
    }

    suspend fun getStringAutoCharset(url: String): String {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: throw IOException("Empty body")
                val contentType = response.header("Content-Type") ?: ""
                val charset = if (
                    contentType.contains("gbk", ignoreCase = true) ||
                    contentType.contains("gb2312", ignoreCase = true)
                ) {
                    Charset.forName("GBK")
                } else {
                    Charsets.UTF_8
                }
                String(bytes, charset)
            }
        }
    }
}
