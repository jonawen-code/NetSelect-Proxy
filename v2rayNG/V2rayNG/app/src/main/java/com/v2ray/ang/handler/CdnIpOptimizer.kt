package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object CdnIpOptimizer {

    val defaultCloudflareIps = listOf(
        "104.16.249.249", "104.17.150.150", "162.159.200.1", "172.67.180.180",
        "104.18.2.2", "104.19.3.3", "104.20.4.4", "172.64.1.1",
        "162.158.10.10", "198.41.129.1", "104.16.123.96", "172.67.200.50",
        "104.17.200.200", "104.21.1.1", "104.22.2.2"
    )

    data class OptimizationResult(
        val success: Boolean,
        val bestIp: String?,
        val delayMillis: Long,
        val message: String
    )

    suspend fun optimizeProfile(
        guid: String,
        profile: ProfileItem,
        candidateIps: List<String> = defaultCloudflareIps
    ): OptimizationResult = withContext(Dispatchers.IO) {
        val originalServer = profile.server ?: ""
        if (originalServer.isEmpty()) {
            return@withContext OptimizationResult(false, null, -1, "节点服务器地址为空")
        }

        // Preserve Host and SNI if not explicitly configured
        if (profile.host.isNullOrEmpty()) {
            profile.host = originalServer
        }
        if (profile.sni.isNullOrEmpty()) {
            profile.sni = originalServer
        }

        val port = profile.serverPort?.toIntOrNull() ?: 443

        val deferreds = candidateIps.map { ip ->
            async {
                val delay = SpeedtestManager.socketConnectTime(ip, port, timeoutMs = 1500)
                Pair(ip, delay)
            }
        }

        val results = deferreds.awaitAll()
            .filter { it.second > 0 }
            .sortedBy { it.second }

        if (results.isEmpty()) {
            return@withContext OptimizationResult(false, null, -1, "测速失败：未能连通有效 CDN IP")
        }

        val best = results.first()
        val bestIp = best.first
        val delay = best.second

        profile.server = bestIp
        MmkvManager.encodeServerConfig(guid, profile)

        LogUtil.d(AppConfig.TAG, "CDN IP 优选成功: $guid -> $bestIp ($delay ms)")

        OptimizationResult(
            success = true,
            bestIp = bestIp,
            delayMillis = delay,
            message = "优选成功！最低延迟 IP: $bestIp ($delay ms)"
        )
    }
}
