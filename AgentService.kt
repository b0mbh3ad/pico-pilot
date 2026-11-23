package com.picopilot.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/**
 * Служба агента: слушает UDP-объявления о поиске и TCP-команды от телефона.
 *
 * Это «приёмник» вместо ADB: телефон находит шлем сам, подключается по PIN
 * и дальше гоняет те же shell-команды, что раньше шли через adbd.
 */
class AgentService : Service() {

    companion object {
        const val ACTION_STOP = "com.picopilot.agent.STOP"
        private const val CHANNEL = "picopilot_agent"
        private const val NOTIFICATION_ID = 4411

        fun start(context: Context) {
            val intent = Intent(context, AgentService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Exception) {
                AgentState.log("Не удалось запустить службу: ${e.message}")
            }
        }

        fun stop(context: Context) {
            AgentState.enabled = false
            runCatching { context.stopService(Intent(context, AgentService::class.java)) }
        }
    }

    @Volatile
    private var alive = false

    private var serverSocket: ServerSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AgentState.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AgentState.enabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        AgentState.enabled = true
        startIfNeeded()
        return START_STICKY
    }

    override fun onDestroy() {
        alive = false
        AgentState.running = false
        runCatching { serverSocket?.close() }
        runCatching { discoverySocket?.close() }
        runCatching { wifiLock?.release() }
        runCatching { multicastLock?.release() }
        serverSocket = null
        discoverySocket = null
        wifiLock = null
        multicastLock = null
        AgentState.log("Агент остановлен")
        super.onDestroy()
    }

    // ------------------------------------------------------------- запуск

    private fun startForegroundCompat() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL,
                "PicoPilot Агент",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Приём подключений с телефона" }
            manager.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("PicoPilot Агент")
            .setContentText("Ждёт подключения телефона")
            .setSmallIcon(R.drawable.ic_notify)
            .setOngoing(true)
            .setContentIntent(open)
            .build()

        runCatching { startForeground(NOTIFICATION_ID, notification) }
            .onFailure { AgentState.log("Уведомление службы не показалось: ${it.message}") }
    }

    @Synchronized
    private fun startIfNeeded() {
        if (alive) return
        alive = true

        Shell.workDir = File(cacheDir, "transfer").apply { mkdirs() }
        Shell.cleanTemp()

        acquireLocks()
        startServer()
        startDiscovery()

        AgentState.running = true
        AgentState.lastError = null
        AgentState.log("Агент запущен")

        Thread {
            AgentState.root = Shell.hasRoot()
            AgentState.log(if (AgentState.root) "Root доступен" else "Root недоступен")
        }.apply { isDaemon = true }.start()
    }

    private fun acquireLocks() {
        runCatching {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "picopilot:agent").apply {
                setReferenceCounted(false)
                acquire()
            }
            multicastLock = wifi.createMulticastLock("picopilot:agent").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    // ------------------------------------------------------------- сервер

    private fun startServer() {
        thread(name = "pp-server", isDaemon = true) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(AgentState.PORT))
                serverSocket = socket
                AgentState.log("TCP-сервер на порту ${AgentState.PORT}")
                while (alive) {
                    val client = try {
                        socket.accept()
                    } catch (e: Exception) {
                        if (!alive) break else continue
                    }
                    thread(isDaemon = true) { handleClient(client) }
                }
            } catch (e: Exception) {
                AgentState.lastError = e.message
                AgentState.log("Сервер не поднялся: ${e.message}")
                AgentState.running = false
            }
        }
    }

    private fun startDiscovery() {
        thread(name = "pp-discovery", isDaemon = true) {
            try {
                val socket = DatagramSocket(null)
                socket.reuseAddress = true
                socket.broadcast = true
                socket.bind(InetSocketAddress(AgentState.DISCOVERY_PORT))
                socket.soTimeout = 1_000
                discoverySocket = socket
                AgentState.log("Объявления в сети: UDP ${AgentState.DISCOVERY_PORT}")
                val buffer = ByteArray(1_024)
                while (alive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (!text.startsWith("PICOPILOT_DISCOVER")) continue
                        val payload = "PICOPILOT_AGENT_V1 " + discoveryPayload()
                        val bytes = payload.toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(bytes, bytes.size, packet.address, packet.port))
                    } catch (_: SocketTimeoutException) {
                        // просто проверяем, живы ли мы
                    } catch (e: Exception) {
                        if (!alive) break
                    }
                }
            } catch (e: Exception) {
                AgentState.log("Поиск в сети не включился: ${e.message}")
            }
        }
    }

    private fun discoveryPayload(): String = JSONObject().apply {
        put("name", Build.MODEL)
        put("model", Build.MODEL)
        put("brand", Build.BRAND)
        put("android", Build.VERSION.RELEASE)
        put("port", AgentState.PORT)
        put("ver", AgentState.PROTOCOL_VERSION)
        put("root", AgentState.root)
    }.toString()

    // ------------------------------------------------------------- клиент

    private fun handleClient(socket: Socket) {
        val address = socket.inetAddress?.hostAddress ?: "?"
        var pushFile: File? = null
        var pushStream: OutputStream? = null
        var pushTarget: String? = null

        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 120_000

            val input = BufferedInputStream(socket.getInputStream(), 64 * 1024)
            val output = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            val writeLock = Any()

            fun send(json: JSONObject) {
                val bytes = (json.toString() + "\n").toByteArray(Charsets.UTF_8)
                synchronized(writeLock) {
                    output.write(bytes)
                    output.flush()
                }
            }

            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8), 64 * 1024)

            val helloLine = reader.readLine() ?: return
            val hello = runCatching { JSONObject(helloLine) }.getOrNull()
            if (hello == null || hello.optString("t") != "hello") {
                send(welcome(ok = false, error = "protocol"))
                return
            }
            if (!AgentState.checkPin(hello.optString("pin"))) {
                send(welcome(ok = false, error = "pin"))
                AgentState.log("Отклонено: неверный PIN ($address)")
                return
            }

            send(welcome(ok = true))
            AgentState.clientConnected(address)

            while (true) {
                val line = reader.readLine() ?: break
                val request = runCatching { JSONObject(line) }.getOrNull() ?: continue
                val id = request.optInt("id", 0)

                when (request.optString("t")) {
                    "ping" -> send(JSONObject().put("t", "pong").put("id", id))

                    "sh" -> {
                        val command = request.optString("cmd")
                        val timeout = request.optInt("timeout", 30_000).coerceIn(1_000, 180_000)
                        val raw = request.optBoolean("raw", false)
                        val result = Shell.exec(command, timeout, mergeErrors = !raw)
                        sendChunks({ json -> send(json) }, id, result.output)
                        send(endFrame(id, result.code))
                    }

                    "shs" -> {
                        streamCommand({ json -> send(json) }, id, request.optString("cmd"))
                        return
                    }

                    "push" -> {
                        val path = request.optString("path")
                        if (pushStream == null || pushTarget != path) {
                            runCatching { pushStream?.close() }
                            val tmp = File(Shell.workDir, "push_${System.currentTimeMillis()}")
                            pushFile = tmp
                            pushStream = FileOutputStream(tmp)
                            pushTarget = path
                        }
                        val b64 = request.optString("b64")
                        if (b64.isNotEmpty()) {
                            pushStream?.write(Base64.decode(b64, Base64.NO_WRAP))
                        }
                        if (request.optBoolean("eof", false)) {
                            runCatching { pushStream?.flush() }
                            runCatching { pushStream?.close() }
                            pushStream = null

                            val tmp = pushFile
                            if (tmp != null && path.isNotEmpty()) {
                                try {
                                    Shell.installFile(tmp, path)
                                    send(JSONObject().put("t", "ok").put("id", id))
                                } catch (e: Exception) {
                                    send(errFrame(id, e.message ?: "не удалось записать файл"))
                                } finally {
                                    tmp.delete()
                                }
                            } else {
                                send(JSONObject().put("t", "ok").put("id", id))
                            }
                            pushFile = null
                            pushTarget = null
                        }
                    }

                    "pull" -> {
                        val path = request.optString("path")
                        try {
                            Shell.openRead(path).use { stream ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val read = stream.read(buffer)
                                    if (read < 0) break
                                    if (read > 0) send(chunkFrame(id, buffer, read))
                                }
                            }
                            send(JSONObject().put("t", "file_end").put("id", id).put("code", 0))
                        } catch (e: Exception) {
                            send(errFrame(id, e.message ?: "не удалось прочитать файл"))
                        }
                    }

                    "close" -> return
                }
            }
        } catch (_: Exception) {
            // телефон отключился — это нормально
        } finally {
            runCatching { pushStream?.close() }
            runCatching { pushFile?.delete() }
            AgentState.clientDisconnected(address)
            runCatching { socket.close() }
        }
    }

    private fun streamCommand(send: (JSONObject) -> Unit, id: Int, command: String) {
        var process: Process? = null
        try {
            process = Shell.startProcess(command)
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = process.inputStream.read(buffer)
                if (read < 0) break
                if (read > 0) send(chunkFrame(id, buffer, read))
            }
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            send(endFrame(id, code))
        } catch (e: Exception) {
            runCatching { send(errFrame(id, e.message ?: "поток прерван")) }
        } finally {
            runCatching { process?.destroy() }
        }
    }

    private fun welcome(ok: Boolean, error: String? = null): JSONObject {
        val json = JSONObject()
        json.put("t", "welcome")
        json.put("ok", ok)
        if (!ok) {
            json.put("err", error ?: "error")
            return json
        }
        json.put("name", Build.MODEL)
        json.put("model", Build.MODEL)
        json.put("brand", Build.BRAND)
        json.put("android", Build.VERSION.RELEASE)
        json.put("root", AgentState.root)
        json.put("port", AgentState.PORT)
        json.put("ver", AgentState.PROTOCOL_VERSION)
        return json
    }

    private fun sendChunks(send: (JSONObject) -> Unit, id: Int, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val length = minOf(32 * 1024, data.size - offset)
            send(chunkFrame(id, data, offset, length))
            offset += length
        }
    }

    private fun chunkFrame(id: Int, data: ByteArray, offset: Int = 0, length: Int = data.size): JSONObject {
        val slice = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        return JSONObject()
            .put("t", "chunk")
            .put("id", id)
            .put("b64", Base64.encodeToString(slice, Base64.NO_WRAP))
    }

    private fun endFrame(id: Int, code: Int): JSONObject =
        JSONObject().put("t", "end").put("id", id).put("code", code)

    private fun errFrame(id: Int, message: String): JSONObject =
        JSONObject().put("t", "err").put("id", id).put("msg", message)
}
