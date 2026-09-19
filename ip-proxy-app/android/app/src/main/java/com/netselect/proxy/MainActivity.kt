package com.netselect.proxy

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.netselect.proxy.model.ProxyNode
import com.netselect.proxy.optimizer.IpSpeedTester
import com.netselect.proxy.service.ProxyVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {

    private val VPN_REQUEST_CODE = 1001

    private lateinit var etNodeUrl: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvOptimizedIp: TextView
    private lateinit var btnOptimize: Button
    private lateinit var btnConnect: Button

    private var currentNode: ProxyNode? = null
    private var isConnected = false
    private val speedTester = IpSpeedTester()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etNodeUrl = findViewById(R.id.etNodeUrl)
        tvStatus = findViewById(R.id.tvStatus)
        tvOptimizedIp = findViewById(R.id.tvOptimizedIp)
        btnOptimize = findViewById(R.id.btnOptimize)
        btnConnect = findViewById(R.id.btnConnect)

        // Default sample VLESS configuration
        etNodeUrl.setText("vless://12345678-1234-1234-1234-123456789012@my-cdn-node.com:443?type=ws&security=tls&sni=my-cdn-node.com&host=my-cdn-node.com#CloudflareNode")

        btnOptimize.setOnClickListener {
            runIpOptimization()
        }

        btnConnect.setOnClickListener {
            if (isConnected) {
                stopVpnService()
            } else {
                prepareAndStartVpn()
            }
        }
    }

    private fun runIpOptimization() {
        val url = etNodeUrl.text.toString().trim()
        val node = ProxyNode.parseVless(url)
        if (node == null) {
            Toast.makeText(this, "无效的 VLESS 节点链接", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatus.text = "状态：正在对 CDN IP 池并发测速优选..."
        btnOptimize.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            val optimized = speedTester.optimizeNode(node)
            currentNode = optimized
            btnOptimize.isEnabled = true

            if (optimized.optimizedIp != null) {
                tvStatus.text = "状态：优选完成！最低延迟 IP：${optimized.optimizedIp} (${optimized.latencyMs} ms)"
                tvOptimizedIp.text = "优化后节点:\n${optimized.getOptimizedVlessUrl()}"
            } else {
                tvStatus.text = "状态：测速失败，未能连接到有效 CDN IP"
            }
        }
    }

    private fun prepareAndStartVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            startVpnService()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, ProxyVpnService::class.java)
        startService(intent)
        isConnected = true
        btnConnect.text = "断开 VPN 连接"
        tvStatus.text = "状态：代理服务运行中 (${currentNode?.optimizedIp ?: "未优选"})"
    }

    private fun stopVpnService() {
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = "STOP_VPN"
        }
        startService(intent)
        isConnected = false
        btnConnect.text = "启动 VPN 连接"
        tvStatus.text = "状态：已断开"
    }
}
