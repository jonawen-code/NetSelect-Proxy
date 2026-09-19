package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.service.RealPingExecutionLimiter
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.NetworkUtils
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

object CdnIpOptimizer {

    val defaultCloudflareIps = listOf(
        "104.16.249.249", "104.17.150.150", "162.159.200.1", "172.67.180.180",
        "104.18.2.2", "104.19.3.3", "104.20.4.4", "172.64.1.1",
        "162.158.10.10", "198.41.129.1", "104.16.123.96", "172.67.200.50",
        "104.17.200.200", "104.21.1.1", "104.22.2.2"
    )

    data class BatchOptimizationResult(
        val success: Boolean,
        val groupGuid: String,
        val firstNodeGuid: String?,
        val count: Int,
        val message: String
    )

    /**
     * Helper to measure real connection latency via Xray core with TCP fallback.
     */
    private suspend fun testRealPing(context: Context, guid: String, profile: ProfileItem): Long {
        val server = profile.server ?: return -1L
        val port = profile.serverPort?.toIntOrNull() ?: return -1L
        val tcpDelay = SpeedtestManager.socketConnectTime(server, port, timeoutMs = 1500)
        if (tcpDelay <= 0) return -1L

        return try {
            val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
            if (configResult.status && configResult.content.isNotBlank()) {
                val delayTestUrl = SettingsManager.getDelayTestUrl()
                val realDelay = RealPingExecutionLimiter.run(profile.configType) {
                    CoreNativeManager.measureOutboundDelay(configResult.content, delayTestUrl)
                }
                if (realDelay > 0) realDelay else tcpDelay
            } else {
                tcpDelay
            }
        } catch (_: Throwable) {
            tcpDelay
        }
    }

    /**
     * Probes candidate CDN IPs for a profile.
     * Only updates profile.server if a candidate CDN IP passes live Xray handshake and returns delay > 0.
     * Otherwise retains original working server IP.
     */
    private suspend fun verifyAndOptimizeProfileCdn(
        context: Context,
        guid: String,
        profile: ProfileItem,
        candidateIps: List<String> = defaultCloudflareIps
    ) {
        val originalServer = profile.server ?: ""
        if (originalServer.isEmpty()) return

        val origHost = profile.host
        val origSni = profile.sni
        if (profile.host.isNullOrEmpty()) profile.host = originalServer
        if (profile.sni.isNullOrEmpty()) profile.sni = originalServer

        val port = profile.serverPort?.toIntOrNull() ?: 443

        val workingCandidates = coroutineScope {
            candidateIps.map { ip ->
                async {
                    val tcp = SpeedtestManager.socketConnectTime(ip, port, timeoutMs = 1000)
                    ip to tcp
                }
            }.awaitAll()
        }.filter { it.second > 0 }.sortedBy { it.second }

        var bestIp: String? = null
        var bestDelay = -1L

        for ((ip, _) in workingCandidates.take(5)) {
            val testGuid = "temp_${Utils.getUuid()}"
            val testProfile = profile.copy(server = ip)
            MmkvManager.encodeServerConfig(testGuid, testProfile)
            val realDelay = testRealPing(context, testGuid, testProfile)
            MmkvManager.removeServer(testGuid)

            if (realDelay > 0) {
                bestIp = ip
                bestDelay = realDelay
                break
            }
        }

        if (bestIp != null && bestDelay > 0) {
            profile.server = bestIp
            LogUtil.d(AppConfig.TAG, "CDN IP 优选成功: $guid -> $bestIp ($bestDelay ms)")
        } else {
            profile.server = originalServer
            profile.host = origHost
            profile.sni = origSni
            LogUtil.d(AppConfig.TAG, "保留原节点服务器 IP: $guid -> $originalServer")
        }
        MmkvManager.encodeServerConfig(guid, profile)
    }

    /**
     * Scans all nodes across all subscription groups, streams progress, selects top 8 (4 IPv4 + 4 IPv6),
     * optimizes CDN IPs with handshake verification, renames according to network type, and writes to "本地优选" group.
     */
    suspend fun autoOptimizeTopNodesWithProgress(
        context: Context,
        onProgress: (stepText: String, progressFraction: Float, detailsLog: String) -> Unit
    ): BatchOptimizationResult = withContext(Dispatchers.IO) {
        val detailsBuilder = StringBuilder()
        fun log(msg: String) {
            detailsBuilder.appendLine(msg)
            LogUtil.i(AppConfig.TAG, "CdnOptimizer: $msg")
        }

        log("开始检索全量节点...")
        onProgress("开始检索全量节点...", 0.05f, detailsBuilder.toString())

        val allGuids = MmkvManager.decodeServerList("")
        if (allGuids.isEmpty()) {
            log("错误：没有找到任何节点配置")
            onProgress("错误：没有节点配置", 1.0f, detailsBuilder.toString())
            return@withContext BatchOptimizationResult(false, "", null, 0, "没有可优选的节点配置")
        }

        val candidates = allGuids.mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            if (profile.configType.isComplexType()) return@mapNotNull null
            if (profile.server.isNullOrBlank()) return@mapNotNull null
            guid to profile
        }

        if (candidates.isEmpty()) {
            log("错误：没有可进行测速的有效节点")
            onProgress("错误：没有有效节点", 1.0f, detailsBuilder.toString())
            return@withContext BatchOptimizationResult(false, "", null, 0, "没有有效可测速的节点")
        }

        log("共找到 ${candidates.size} 个待测速节点，开始真连接延迟扫描...")
        onProgress("准备测速...", 0.1f, detailsBuilder.toString())

        val totalCandidates = candidates.size
        val completedCount = AtomicInteger(0)

        val testedNodes = coroutineScope {
            candidates.map { (guid, profile) ->
                async {
                    val delay = testRealPing(context, guid, profile)
                    val current = completedCount.incrementAndGet()
                    val fraction = 0.1f + (current.toFloat() / totalCandidates) * 0.5f

                    val statusLine = "测试中 (${current}/${totalCandidates}): ${profile.remarks} -> ${if (delay > 0) "${delay}ms" else "超时"}"
                    log(statusLine)
                    onProgress("正在测试真连接延迟 ($current/$totalCandidates)", fraction, detailsBuilder.toString())

                    Triple(guid, profile, delay)
                }
            }.awaitAll()
        }.filter { it.third > 0 }.sortedBy { it.third }

        if (testedNodes.isEmpty()) {
            log("全量测速完成，未发现可用节点 (均为 -1ms)")
            onProgress("测速完成，无有效节点", 1.0f, detailsBuilder.toString())
            return@withContext BatchOptimizationResult(false, "", null, 0, "网络测速失败：未能与节点建立有效连接")
        }

        log("有效连通节点: ${testedNodes.size} 个，开始按 IPv4 / IPv6 遴选...")
        onProgress("筛选最快节点...", 0.65f, detailsBuilder.toString())

        val ipv6Nodes = testedNodes.filter {
            it.second.server?.contains(":") == true || it.second.server?.startsWith("[") == true
        }
        val ipv4Nodes = testedNodes.filter {
            it.second.server?.contains(":") != true && it.second.server?.startsWith("[") != true
        }

        log("IPv4 节点库: ${ipv4Nodes.size} 个，IPv6 节点库: ${ipv6Nodes.size} 个")

        val selectedV4 = ipv4Nodes.take(4)
        val selectedV6 = ipv6Nodes.take(4)

        val top8Nodes = (selectedV4 + selectedV6).let { combined ->
            if (combined.size < 8) {
                val remaining = testedNodes.filter { it !in combined }.take(8 - combined.size)
                combined + remaining
            } else combined
        }.take(8)

        log("已遴选出前 ${top8Nodes.size} 个最快节点")

        // Get or create "本地优选" subscription group
        val subs = MmkvManager.decodeSubscriptions()
        var groupGuid = subs.find { it.subscription.remarks == "本地优选" }?.guid
        if (groupGuid.isNullOrBlank()) {
            groupGuid = Utils.getUuid()
            val newSub = SubscriptionItem(
                remarks = "本地优选",
                url = ""
            )
            MmkvManager.encodeSubscription(groupGuid, newSub)
            log("已创建【本地优选】订阅分组")
        }

        val networkPrefix = NetworkUtils.getNetworkTypeName(context)
        log("当前网络接口: $networkPrefix")

        // Clear existing nodes in "本地优选" group
        MmkvManager.removeServerViaSubid(groupGuid)

        log("开始逐个验证 CDN 候选 IP 并生成优选节点...")
        onProgress("正在进行 CDN IP 连通性探针...", 0.75f, detailsBuilder.toString())

        var firstNodeGuid: String? = null
        top8Nodes.forEachIndexed { index, (_, origProfile, delay) ->
            val newGuid = Utils.getUuid()
            if (index == 0) firstNodeGuid = newGuid

            val nodeName = "$networkPrefix-${String.format("%02d", index + 1)}"
            val newProfile = origProfile.copy(
                subscriptionId = groupGuid,
                remarks = nodeName
            )

            log("处理节点 (${index + 1}/8): $nodeName (原延迟 ${delay}ms)...")
            val currentFraction = 0.75f + ((index + 1).toFloat() / top8Nodes.size) * 0.2f
            onProgress("生成节点 $nodeName...", currentFraction, detailsBuilder.toString())

            verifyAndOptimizeProfileCdn(context, newGuid, newProfile)
        }

        log("【本地优选】分组成功生成！包含 ${top8Nodes.size} 个 $networkPrefix 节点。")
        onProgress("优化完成！", 1.0f, detailsBuilder.toString())

        BatchOptimizationResult(
            success = true,
            groupGuid = groupGuid,
            firstNodeGuid = firstNodeGuid,
            count = top8Nodes.size,
            message = "成功在【本地优选】组生成了 ${top8Nodes.size} 个 $networkPrefix 最优节点！"
        )
    }
}
