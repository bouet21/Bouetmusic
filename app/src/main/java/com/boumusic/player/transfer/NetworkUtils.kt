package com.boumusic.player.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getLocalIpAddress(context: Context): String? {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null) {
                val activeNetwork = connectivityManager.activeNetwork
                val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
                if (linkProperties != null) {
                    for (linkAddress in linkProperties.linkAddresses) {
                        val address = linkAddress.address
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            return address.hostAddress
                        }
                    }
                }
            }

            // Fallback con interfaces de red directas
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
