package com.picopilot.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class NearbyAgent(
    val host: String,
    val port: Int,
    val name: String,
    val model: String,
    val android: String,
    val root: Boolean,
) {
    val title: String
        get() = if (name.isBlank() || name == model) "Pico" else name
}

/**
 * Поиск шлемов с запущенным агентом: шлём UDP-«пинг» на весь /24 и слушаем ответы.
 * Работает без multicast и без ADB, поэтому не зависит от того, включена ли отладка.
 */
object AgentDiscovery {

    private const val PROBE = "PICOPILOT_DISCOVER_V1"
    private const val REPLY = "PICOPILOT_AGENT_V1"

    suspend fun find(
        timeoutMs: Long = 1_400,
        port: Int = AgentConnection.DISCOVERY_PORT,
    ): List<NearbyAgent> = withContext(Dispatchers.IO) {
        val socket = runCatching {
            DatagramSocket().apply {
                broadcast = true
                soTimeout = 200
            }
        }.getOrElse { return@withContext emptyList() }

        val found = linkedMapOf<String, NearbyAgent>()
        try {
            val bytes = PROBE.toByteArray(Charsets.UTF_8)
            val local = runCatching { NetworkScanner.localIpv4() }.getOrNull()

            // Широковещательный запрос.
            runCatching {
                socket.send(
                    DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), port),
                )
            }

            val base = local?.substringBeforeLast('.', "")?.takeIf { it.isNotBlank() }
            if (base != null) {
                // Широковещательный адрес подсети — его любят пропускать некоторые роутеры.
                runCatching {
                    socket.send(
                        DatagramPacket(bytes, bytes.size, InetAddress.getByName("$base.255"), port),
                    )
                }
                // И адресный обход: 254 коротких UDP-пакета, это быстрее секунды.
                for (index in 1..254) {
                    val candidate = "$base.$index"
                    if (candidate == local) continue
                    runCatching {
                        socket.send(
                            DatagramPacket(
                                bytes,
                                bytes.size,
                                InetAddress.getByName(candidate),
                                port,
                            ),
                        )
                    }
                }
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(2_048)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (!text.startsWith(REPLY)) continue
                    val json = runCatching {
                        JSONObject(text.removePrefix(REPLY).trim())
                    }.getOrNull() ?: continue
                    val host = packet.address?.hostAddress ?: continue
                    found[host] = NearbyAgent(
                        host = host,
                        port = json.optInt("port", AgentConnection.DEFAULT_PORT),
                        name = json.optString("name", "Pico"),
                        model = json.optString("model", ""),
                        android = json.optString("android", ""),
                        root = json.optBoolean("root", false),
                    )
                } catch (_: SocketTimeoutException) {
                    // продолжаем слушать до общего дедлайна
                } catch (_: Exception) {
                    // один битый пакет не должен ломать поиск
                }
            }
        } finally {
            runCatching { socket.close() }
        }

        found.values.sortedBy { it.host.substringAfterLast('.').toIntOrNull() ?: 0 }
    }
}
