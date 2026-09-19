package com.v2ray.ang.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtils {
    fun getNetworkTypeName(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "WLAN"
        val activeNetwork = cm.activeNetwork ?: return "WLAN"
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "WLAN"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "WLAN"
            else -> "WLAN"
        }
    }
}
