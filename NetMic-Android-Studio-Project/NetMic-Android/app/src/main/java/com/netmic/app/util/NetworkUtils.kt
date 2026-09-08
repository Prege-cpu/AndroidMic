package com.netmic.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    data class NetworkInterfaceInfo(
        val interfaceName: String,
        val ip: String,
        val displayName: String
    )

    /**
     * Ritorna tutti gli indirizzi IPv4 locali disponibili.
     * Funziona identicamente su Wi-Fi, Tethering USB (rndis0, usb0) e Bluetooth (bt-pan).
     */
    fun getAllLocalIps(context: Context): List<NetworkInterfaceInfo> {
        val result = mutableListOf<NetworkInterfaceInfo>()

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue

                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue

                        val friendly = when {
                            intf.name.startsWith("wlan") -> "Wi-Fi (${intf.name})"
                            intf.name.startsWith("rndis") || intf.name.startsWith("usb") -> "Tethering USB (${intf.name})"
                            intf.name.startsWith("bt-pan") -> "Tethering Bluetooth (${intf.name})"
                            intf.name.startsWith("eth") -> "Ethernet (${intf.name})"
                            else -> intf.name
                        }

                        result.add(NetworkInterfaceInfo(intf.name, host, friendly))
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback ConnectivityManager
        if (result.isEmpty()) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            val props: LinkProperties? = cm?.getLinkProperties(activeNet)
            props?.linkAddresses?.forEach { linkAddr ->
                val addr = linkAddr.address
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    result.add(NetworkInterfaceInfo("Rete", addr.hostAddress ?: "0.0.0.0", "Connessione Attiva"))
                }
            }
        }

        if (result.isEmpty()) {
            result.add(NetworkInterfaceInfo("Localhost", "127.0.0.1", "Non connesso"))
        }

        return result;
    }
}
