package com.portalphotos.app.data.server

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*

object NetworkUtils {

    /**
     * Retrieves the current device's local IPv4 address on the Wi-Fi network.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces: List<NetworkInterface> = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs: List<java.net.InetAddress> = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress
                        if (!hostAddress.isNullOrEmpty() && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }
}
