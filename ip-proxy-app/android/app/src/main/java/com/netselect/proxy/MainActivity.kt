package com.netselect.proxy

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.integration.android.IntentIntegrator
import com.netselect.proxy.model.ProxyNode
import com.netselect.proxy.optimizer.IpSpeedTester
import com.netselect.proxy.optimizer.RealConnectionTester
import com.netselect.proxy.service.ProxyVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private val VPN_REQUEST_CODE = 1001
    private val CAMERA_PERMISSION_CODE = 1002

    private lateinit var etSubUrl: EditText
    private lateinit var btnUpdateSub: Button
    private lateinit var tvSubInfo: TextView
    private lateinit var btnScanQr: Button
    private lateinit var btnImportClipboard: Button
    private lateinit var etNodeUrl: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvRealDelay: TextView
    private lateinit var tvOptimizedIp: TextView
    private lateinit var btnOptimize: Button
    private lateinit var btnTestRealConnection: Button
    private lateinit var btnConnect: Button

    private var currentNode: ProxyNode? = null
    private var subscriptionNodes: List<ProxyNode> = emptyList()
    private var isConnected = false

    private val speedTester = IpSpeedTester()
    private val realConnectionTester = RealConnectionTester()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etSubUrl = findViewById(R.id.etSubUrl)
        btnUpdateSub = findViewById(R.id.btnUpdateSub)
        tvSubInfo = findViewById(R.id.tvSubInfo)
        btnScanQr = findViewById(R.id.btnScanQr)
        btnImportClipboard = findViewById(R.id.btnImportClipboard)
        etNodeUrl = findViewById(R.id.etNodeUrl)
        tvStatus = findViewById(R.id.tvStatus)
        tvRealDelay = findViewById(R.id.tvRealDelay)
        tvOptimizedIp = findViewById(R.id.tvOptimizedIp)
        btnOptimize = findViewById(R.id.btnOptimize)
        btnTestRealConnection = findViewById(R.id.btnTestRealConnection)
        btnConnect = findViewById(R.id.btnConnect)

        // Default sample VLESS node configuration
        etNodeUrl.setText("vless://12345678-1234-1234-1234-123456789012@my-cdn-node.com:443?type=ws&security=tls&sni=my-cdn-node.com&host=my-cdn-node.com#CloudflareNode")

        btnScanQr.setOnClickListener {
            startQrScan()
        }

        btnImportClipboard.setOnClickListener {
            importFromClipboard()
        }

        btnUpdateSub.setOnClickListener {
            val subUrl = etSubUrl.text.toString().trim()
            if (subUrl.isEmpty()) {
                Toast.makeText(this, "请输入订阅链接", Toast.LENGTH_SHORT).show()
            } else {
                fetchSubscription(subUrl)
            }
        }

        btnOptimize.setOnClickListener {
            runIpOptimization()
        }

        btnTestRealConnection.setOnClickListener {
            runRealConnectionTest()
        }

        btnConnect.setOnClickListener {
            if (isConnected) {
                stopVpnService()
            } else {
                prepareAndStartVpn()
            }
        }
    }

    private fun startQrScan() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else {
            val integrator = IntentIntegrator(this)
            integrator.setPrompt("请将二维码对准框内 (支持节点/订阅二维码)")
            integrator.setBeepEnabled(true)
            integrator.setOrientationLocked(false)
            integrator.initiateScan()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startQrScan()
            } else {
                Toast.makeText(this, "需要相机权限以扫描二维码", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                handleImportedText(text)
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleImportedText(text: String) {
        if (text.startsWith("http://") || text.startsWith("https://")) {
            etSubUrl.setText(text)
            Toast.makeText(this, "检测到订阅链接，正在更新订阅...", Toast.LENGTH_SHORT).show()
            fetchSubscription(text)
        } else {
            val node = ProxyNode.parseNode(text)
            if (node != null) {
                currentNode = node
                etNodeUrl.setText(text)
                Toast.makeText(this, "已解析并导入节点: ${node.name}", Toast.LENGTH_SHORT).show()
            } else {
                val nodes = ProxyNode.parseSubscriptionContent(text)
                if (nodes.isNotEmpty()) {
                    subscriptionNodes = nodes
                    currentNode = nodes[0]
                    etNodeUrl.setText(nodes[0].getOptimizedUrl())
                    tvSubInfo.text = "解析并导入了 ${nodes.size} 个节点，当前：${nodes[0].name}"
                    Toast.makeText(this, "成功解析 ${nodes.size} 个节点！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "无法识别节点配置或订阅内容", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchSubscription(subUrl: String) {
        tvSubInfo.text = "正在下载并更新订阅..."
        btnUpdateSub.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            val nodes = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url(subUrl)
                        .header("User-Agent", "v2rayNG/1.8.5")
                        .build()
                    val response = client.newCall(request).execute()
                    val bodyText = response.body?.string() ?: ""
                    response.close()
                    ProxyNode.parseSubscriptionContent(bodyText)
                } catch (e: Exception) {
                    emptyList()
                }
            }

            btnUpdateSub.isEnabled = true
            if (nodes.isNotEmpty()) {
                subscriptionNodes = nodes
                currentNode = nodes[0]
                etNodeUrl.setText(nodes[0].getOptimizedUrl())
                tvSubInfo.text = "订阅更新成功！包含 ${nodes.size} 个节点，默认已选择：${nodes[0].name}"
                Toast.makeText(this@MainActivity, "成功拉取 ${nodes.size} 个节点", Toast.LENGTH_SHORT).show()
            } else {
                tvSubInfo.text = "订阅更新失败，请检查链接或网络"
                Toast.makeText(this@MainActivity, "获取订阅失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runIpOptimization() {
        val url = etNodeUrl.text.toString().trim()
        val node = ProxyNode.parseNode(url) ?: currentNode
        if (node == null) {
            Toast.makeText(this, "无效的节点配置链接", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatus.text = "状态：正在对 CDN IP 池并发测速优选..."
        btnOptimize.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            val optimized = speedTester.optimizeNode(node)
            currentNode = optimized
            btnOptimize.isEnabled = true

            if (optimized.optimizedIp != null) {
                tvStatus.text = "状态：优选完成！最低 RTT 延迟 IP：${optimized.optimizedIp} (${optimized.latencyMs} ms)"
                tvOptimizedIp.text = "优化后节点:\n${optimized.getOptimizedUrl()}"
            } else {
                tvStatus.text = "状态：测速失败，未能连接到有效 CDN IP"
            }
        }
    }

    private fun runRealConnectionTest() {
        val url = etNodeUrl.text.toString().trim()
        val node = ProxyNode.parseNode(url) ?: currentNode
        if (node == null) {
            Toast.makeText(this, "请先选择或输入有效节点", Toast.LENGTH_SHORT).show()
            return
        }

        tvRealDelay.text = "真连接延迟：测试中..."
        btnTestRealConnection.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            val delay = realConnectionTester.testRealDelay(node)
            btnTestRealConnection.isEnabled = true

            if (delay >= 0) {
                tvRealDelay.text = "真连接延迟：${delay} ms (HTTP 204 Success)"
            } else {
                tvRealDelay.text = "真连接延迟：连接超时 / 失败"
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
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                val qrText = result.contents.trim()
                handleImportedText(qrText)
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, ProxyVpnService::class.java)
        startService(intent)
        isConnected = true
        btnConnect.text = "断开 VPN 连接"
        tvStatus.text = "状态：代理服务运行中 (${currentNode?.optimizedIp ?: currentNode?.address ?: "已连接"})"
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
