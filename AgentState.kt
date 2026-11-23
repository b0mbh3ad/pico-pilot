package com.picopilot.agent

import android.content.Context
import android.content.SharedPreferences
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Collections

object AgentState {

    const val PORT = 47801
    const val DISCOVERY_PORT = 47800
    const val PROTOCOL_VERSION = 1

    private const val PREFS = "picopilot_agent"

    private lateinit var prefs: SharedPreferences

    @Volatile
    var running: Boolean = false

    @Volatile
    var root: Boolean = false

    @Volatile
    var lastError: String? = null

    private val clientSet = LinkedHashSet<String>()
    private val logLines = ArrayDeque<String>()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (pin().isEmpty()) regeneratePin()
    }

    fun pin(): String = prefs.getString("pin", "").orEmpty()

    fun regeneratePin() {
        val value = (SecureRandom().nextInt(900000) + 100000).toString()
        prefs.edit().putString("pin", value).apply()
        log("Новый PIN: $value")
    }

    fun checkPin(candidate: String?): Boolean {
        val current = pin()
        return current.isNotEmpty() && candidate == current
    }

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) {
            prefs.edit().putBoolean("enabled", value).apply()
        }

    var autoStart: Boolean
        get() = prefs.getBoolean("autostart", true)
        set(value) {
            prefs.edit().putBoolean("autostart", value).apply()
        }

    fun clientConnected(address: String) {
        synchronized(clientSet) { clientSet.add(address) }
        log("Подключился телефон: $address")
    }

    fun clientDisconnected(address: String) {
        synchronized(clientSet) { clientSet.remove(address) }
    }

    fun clients(): List<String> = synchronized(clientSet) { clientSet.toList() }

    fun log(line: String) {
        val stamp = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString()
        synchronized(logLines) {
            logLines.addLast("$stamp  $line")
            while (logLines.size > 60) logLines.removeFirst()
        }
    }

    fun logs(): List<String> = synchronized(logLines) { logLines.toList() }

    fun ipv4Addresses(): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { iface ->
                if (!iface.isUp || iface.isLoopback) return@forEach
                Collections.list(iface.inetAddresses).forEach { address ->
                    val host = address.hostAddress ?: return@forEach
                    if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        result.add(host)
                    }
                }
            }
        }
        return result.distinct()
    }
}
