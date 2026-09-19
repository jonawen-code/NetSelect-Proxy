package com.netselect.proxy.optimizer

import com.netselect.proxy.model.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RealConnectionTester {

    private val testUrls = listOf(
        "https://cp.cloudflare.com/generate_204",
        "https://www.gstatic.com/generate_204"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    /**
     * Tests real HTTP 204 connection latency.
     */
    suspend fun testRealDelay(node: ProxyNode): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        for (url in testUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                val response = client.newCall(request).execute()
                val duration = System.currentTimeMillis() - start
                val code = response.code
                response.close()

                if (code == 204 || code == 200) {
                    node.realDelayMs = duration
                    return@withContext duration
                }
            } catch (e: Exception) {
                // Ignore and try next URL
            }
        }
        node.realDelayMs = -1
        return@withContext -1L
    }
}
