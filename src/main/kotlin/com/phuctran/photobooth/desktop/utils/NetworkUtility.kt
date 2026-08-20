package com.phuctran.photobooth.desktop.utils

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtility {
    /**
     * Finds the most likely active local IPv4 address of this machine.
     * Prefers Wi-Fi or Ethernet adapters.
     */
    fun getLocalIpAddress(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                // Skip virtual network adapters (like VMware, VirtualBox, WSL) if possible
                val name = networkInterface.displayName.lowercase()
                if (name.contains("vmware") || name.contains("virtual") || name.contains("veth")) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress()) {
                        return address.hostAddress
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }
}
