package com.example.fundmobile.data.remote

import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

object HttpClient {
    var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val memoryCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            cookieStore.getOrPut(host) { mutableListOf() }.apply {
                cookies.forEach { newCookie ->
                    removeAll { it.name == newCookie.name }
                    add(newCookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore.entries
                .filter { (host, _) -> url.host == host || url.host.endsWith(".$host") || host.endsWith(".${url.host}") }
                .flatMap { it.value }
                .filter { !it.expiresAt.let { exp -> exp < System.currentTimeMillis() } }
        }
    }

    var baiduClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cookieJar(memoryCookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
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

    suspend fun getString(url: String, headers: Map<String, String>): String {
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.addHeader(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: throw IOException("Empty body")
                String(bytes, Charsets.UTF_8)
            }
        }
    }

    suspend fun getStringWithCookieClient(url: String, headers: Map<String, String>): String {
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.addHeader(k, v) }
            baiduClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: throw IOException("Empty body")
                String(bytes, Charsets.UTF_8)
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
