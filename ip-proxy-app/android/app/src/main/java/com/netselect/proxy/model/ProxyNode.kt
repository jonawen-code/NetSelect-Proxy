package com.netselect.proxy.model

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
    var latencyMs: Long = -1,
    var optimizedIp: String? = null
) {
    companion object {
        fun parseVless(vlessUrl: String): ProxyNode? {
            return try {
                if (!vlessUrl.startsWith("vless://")) return null
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
    }

    fun getOptimizedVlessUrl(): String {
        val targetAddr = optimizedIp ?: address
        return "vless://$uuid@$targetAddr:$port?type=$type&security=$security&sni=$sni&host=$host&path=$path#$name"
    }
}
