package com.picopilot.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

object NetworkScanner {

    fun localIpv4(): String? {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val addresses = Collections.list(networkInterface.inetAddresses)
            for (address in addresses) {
                if (address.isLoopbackAddress) continue
                val host = address.hostAddress ?: continue
                if (address is java.net.Inet4Address && !address.isLinkLocalAddress) return host
            }
        }
        return null
    }

    suspend fun scan(port: Int = 5555, timeoutMs: Int = 350): List<String> = withContext(Dispatchers.IO) {
        val local = localIpv4() ?: throw IOException("Не удалось определить IP телефона. Проверьте Wi-Fi.")
        val base = local.substringBeforeLast('.', missingDelimiterValue = "")
        if (base.isBlank()) throw IOException("Не удалось определить подсеть")

        val semaphore = Semaphore(64)
        val found = Collections.synchronizedList(mutableListOf<String>())

        coroutineScope {
            (1..254).map { host ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val candidate = "$base.$host"
                        if (candidate != local && isPortOpen(candidate, port, timeoutMs)) {
                            found.add(candidate)
                        }
                    }
                }
            }.awaitAll()
        }

        found.sortedBy { it.substringAfterLast('.').toIntOrNull() ?: 0 }
    }

    fun isPortOpen(host: String, port: Int, timeoutMs: Int = 800): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
