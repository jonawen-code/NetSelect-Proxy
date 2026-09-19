package com.netselect.proxy.model

import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

data class ProxyNode(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val protocol: String, // "vless", "vmess", "ss", "trojan"
    val address: String,  // Target connection IP or domain
    val port: Int,
    val uuid: String = "",
    val sni: String = "",
    val host: String = "",
    val path: String = "",
    val security: String = "tls",
    val type: String = "ws",
    var latencyMs: Long = -1,      // TCP ping latency
    var realDelayMs: Long = -1,    // Real connection HTTP 204 latency
    var optimizedIp: String? = null
) {
    companion object {
        fun parseNode(url: String): ProxyNode? {
            val trimmed = url.trim()
            return when {
                trimmed.startsWith("vless://") -> parseVless(trimmed)
                trimmed.startsWith("vmess://") -> parseVmess(trimmed)
                trimmed.startsWith("trojan://") -> parseTrojan(trimmed)
                trimmed.startsWith("ss://") -> parseShadowsocks(trimmed)
                else -> null
            }
        }

        fun parseVless(vlessUrl: String): ProxyNode? {
            return try {
                val uri = URI(vlessUrl)
                val userInfo = uri.userInfo ?: ""
                val host = uri.host ?: ""
                val port = if (uri.port != -1) uri.port else 443
                val tag = if (uri.fragment != null) URLDecoder.decode(uri.fragment, "UTF-8") else "VLESS Node"
                
                val queryParams = (uri.query ?: "").split("&").associate {
                    val parts = it.split("=")
                    if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
                }
                
                ProxyNode(
                    name = tag,
                    protocol = "vless",
                    address = host,
                    port = port,
                    uuid = userInfo,
                    sni = queryParams["sni"] ?: host,
                    host = queryParams["host"] ?: host,
                    path = queryParams["path"] ?: "/",
                    security = queryParams["security"] ?: "tls",
                    type = queryParams["type"] ?: "ws"
                )
            } catch (e: Exception) {
                null
            }
        }

        fun parseVmess(vmessUrl: String): ProxyNode? {
            return try {
                val base64Part = vmessUrl.removePrefix("vmess://")
                val decodedJson = String(Base64.decode(base64Part, Base64.DEFAULT or Base64.URL_SAFE), Charsets.UTF_8)
                val json = JSONObject(decodedJson)
                
                ProxyNode(
                    name = json.optString("ps", "VMess Node"),
                    protocol = "vmess",
                    address = json.optString("add", ""),
                    port = json.optInt("port", 443),
                    uuid = json.optString("id", ""),
                    sni = json.optString("sni", json.optString("host", "")),
                    host = json.optString("host", ""),
                    path = json.optString("path", "/"),
                    security = json.optString("tls", "tls"),
                    type = json.optString("net", "ws")
                )
            } catch (e: Exception) {
                null
            }
        }

        fun parseTrojan(trojanUrl: String): ProxyNode? {
            return try {
                val uri = URI(trojanUrl)
                val password = uri.userInfo ?: ""
                val host = uri.host ?: ""
                val port = if (uri.port != -1) uri.port else 443
                val tag = if (uri.fragment != null) URLDecoder.decode(uri.fragment, "UTF-8") else "Trojan Node"

                ProxyNode(
                    name = tag,
                    protocol = "trojan",
                    address = host,
                    port = port,
                    uuid = password,
                    sni = host,
                    host = host
                )
            } catch (e: Exception) {
                null
            }
        }

        fun parseShadowsocks(ssUrl: String): ProxyNode? {
            return try {
                val uri = URI(ssUrl)
                val host = uri.host ?: ""
                val port = if (uri.port != -1) uri.port else 8388
                val tag = if (uri.fragment != null) URLDecoder.decode(uri.fragment, "UTF-8") else "SS Node"

                ProxyNode(
                    name = tag,
                    protocol = "ss",
                    address = host,
                    port = port,
                    uuid = uri.userInfo ?: ""
                )
            } catch (e: Exception) {
                null
            }
        }

        fun parseSubscriptionContent(content: String): List<ProxyNode> {
            val decoded = try {
                String(Base64.decode(content.trim(), Base64.DEFAULT or Base64.URL_SAFE), Charsets.UTF_8)
            } catch (e: Exception) {
                content
            }

            return decoded.lines()
                .mapNotNull { parseNode(it) }
        }
    }

    fun getOptimizedUrl(): String {
        val targetAddr = optimizedIp ?: address
        return when (protocol) {
            "vless" -> "vless://$uuid@$targetAddr:$port?type=$type&security=$security&sni=$sni&host=$host&path=$path#$name"
            "vmess" -> "vmess://$uuid@$targetAddr:$port?type=$type&security=$security&sni=$sni&host=$host#$name"
            else -> "$protocol://$uuid@$targetAddr:$port#$name"
        }
    }
}
