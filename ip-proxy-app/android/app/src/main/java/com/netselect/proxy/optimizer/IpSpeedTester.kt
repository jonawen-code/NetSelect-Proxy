package com.netselect.proxy.optimizer

import com.netselect.proxy.model.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class IpSpeedTester {

    private val defaultCloudflareIps = listOf(
        "104.16.249.249", "104.17.150.150", "162.159.200.1", "172.67.180.180",
        "104.18.2.2", "104.19.3.3", "104.20.4.4", "172.64.1.1",
        "162.158.10.10", "198.41.129.1"
    )

    suspend fun pingIp(ip: String, port: Int = 443, timeoutMs: Int = 1500): Pair<String, Long?> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                val rtt = System.currentTimeMillis() - start
                Pair(ip, rtt)
            }
        } catch (e: Exception) {
            Pair(ip, null)
        }
    }

    suspend fun optimizeNode(node: ProxyNode, candidateIps: List<String> = defaultCloudflareIps): ProxyNode = withContext(Dispatchers.IO) {
        val deferreds = candidateIps.map { ip ->
            async { pingIp(ip, node.port) }
        }
        
        val results = deferreds.awaitAll()
            .filter { it.second != null }
            .map { Pair(it.first, it.second!!) }
            .sortedBy { it.second }

        if (results.isNotEmpty()) {
            val best = results.first()
            node.optimizedIp = best.first
            node.latencyMs = best.second
        }
        node
    }
}
