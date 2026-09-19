package com.netselect.proxy.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class ProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_VPN") {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        try {
            val builder = Builder()
                .setSession("NetSelect IP-Optimizer Proxy")
                .addAddress("26.26.26.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .setMtu(1500)

            vpnInterface = builder.establish()
            Log.i("ProxyVpnService", "VpnService TUN interface successfully established.")
        } catch (e: Exception) {
            Log.e("ProxyVpnService", "Error establishing VPN interface", e)
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            stopForeground(true)
            stopSelf()
            Log.i("ProxyVpnService", "VpnService stopped.")
        } catch (e: Exception) {
            Log.e("ProxyVpnService", "Error stopping VPN interface", e)
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
