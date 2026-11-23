package com.picopilot.net

import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/** Агент на шлеме не принял PIN. */
class AgentPinException(message: String) : IOException(message)

/**
 * Клиент к приложению «PicoPilot Агент» на шлеме.
 *
 * Протокол — JSON-строки поверх TCP. Двоичные данные (скриншоты, APK, видео)
 * передаются кусками в base64. В отличие от ADB здесь нет рукопожатия ключами
 * и подтверждения отладки: один раз вводится PIN, дальше подключение молчаливое.
 */
class AgentConnection private constructor(
    private val host: String,
    private val port: Int,
    private val pin: String,
    private val socket: Socket,
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
    val deviceName: String,
    val deviceModel: String,
    val hasRoot: Boolean,
) : DeviceChannel {

    private val ids = AtomicInteger(1)
    private val lock = Any()

    @Volatile
    private var closed = false

    override val isClosed: Boolean
        get() = closed || socket.isClosed

    override fun shell(command: String, timeoutMs: Int): String {
        val bytes = roundTrip(
            JSONObject()
                .put("t", "sh")
                .put("cmd", command)
                .put("timeout", timeoutMs),
            timeoutMs.coerceAtLeast(2_000) + 2_000,
        )
        return bytes.toString(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trimEnd()
    }

    override fun execBytes(command: String, timeoutMs: Int): ByteArray = roundTrip(
        JSONObject()
            .put("t", "sh")
            .put("cmd", command)
            .put("timeout", timeoutMs)
            .put("raw", true),
        timeoutMs.coerceAtLeast(2_000) + 2_000,
    )

    override fun push(
        source: InputStream,
        remotePath: String,
        size: Long,
        onProgress: (sent: Long, total: Long) -> Unit,
    ) {
        synchronized(lock) {
            ensureOpen()
            socket.soTimeout = 120_000
            val id = ids.getAndIncrement()

            send(JSONObject().put("t", "push").put("id", id).put("path", remotePath))

            val buffer = ByteArray(32 * 1024)
            var sent = 0L
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                val slice = if (read == buffer.size) buffer else buffer.copyOf(read)
                send(
                    JSONObject()
                        .put("t", "push")
                        .put("id", id)
                        .put("path", remotePath)
                        .put("b64", Base64.encodeToString(slice, Base64.NO_WRAP)),
                )
                sent += read
                onProgress(sent, size)
            }

            send(JSONObject().put("t", "push").put("id", id).put("path", remotePath).put("eof", true))

            while (true) {
                val frame = readFrame() ?: throw IOException("Агент отключился во время передачи файла")
                if (frame.optInt("id", -1) != id) continue
                when (frame.optString("t")) {
                    "ok" -> return
                    "err" -> throw IOException(frame.optString("msg").ifBlank { "Не удалось записать файл" })
                }
            }
        }
    }

    override fun pull(
        remotePath: String,
        sink: OutputStream,
        onProgress: (received: Long) -> Unit,
    ): Long = synchronized(lock) {
        ensureOpen()
        socket.soTimeout = 120_000
        val id = ids.getAndIncrement()

        send(JSONObject().put("t", "pull").put("id", id).put("path", remotePath))

        var total = 0L
        while (true) {
            val frame = readFrame() ?: throw IOException("Агент отключился во время чтения файла")
            if (frame.optInt("id", -1) != id) continue
            when (frame.optString("t")) {
                "chunk" -> {
                    val bytes = Base64.decode(frame.optString("b64"), Base64.NO_WRAP)
                    sink.write(bytes)
                    total += bytes.size
                    onProgress(total)
                }

                "file_end" -> return total
                "err" -> throw IOException(frame.optString("msg").ifBlank { "Не удалось прочитать файл" })
            }
        }
        throw IllegalStateException("поток закончился без file_end")
    }

    override fun ping(): Int = synchronized(lock) {
        ensureOpen()
        val started = System.nanoTime()
        val id = ids.getAndIncrement()
        socket.soTimeout = 4_000
        send(JSONObject().put("t", "ping").put("id", id))
        while (true) {
            val frame = readFrame() ?: throw IOException("Агент не отвечает")
            if (frame.optInt("id", -1) == id && frame.optString("t") == "pong") break
        }
        ((System.nanoTime() - started) / 1_000_000).toInt()
    }

    override fun stream(command: String): CommandStream = openStream(host, port, pin, command)

    override fun close() {
        closed = true
        runCatching { socket.close() }
    }

    // ------------------------------------------------------------ механика

    private fun roundTrip(request: JSONObject, timeoutMs: Int): ByteArray = synchronized(lock) {
        ensureOpen()
        socket.soTimeout = timeoutMs
        val id = ids.getAndIncrement()
        request.put("id", id)
        send(request)

        val out = ByteArrayOutputStream()
        while (true) {
            val frame = readFrame() ?: run {
                close()
                throw IOException("Агент отключился")
            }
            if (frame.optInt("id", -1) != id) continue
            when (frame.optString("t")) {
                "chunk" -> out.write(Base64.decode(frame.optString("b64"), Base64.NO_WRAP))
                "end", "file_end" -> return out.toByteArray()
                "err" -> throw IOException(frame.optString("msg").ifBlank { "Ошибка агента" })
            }
        }
        throw IllegalStateException("поток закончился без ответа")
    }

    private fun ensureOpen() {
        if (isClosed) throw IOException("Соединение с агентом закрыто")
    }

    private fun send(json: JSONObject) {
        writeLine(writer, json)
    }

    private fun readFrame(): JSONObject? {
        val line = try {
            reader.readLine()
        } catch (e: IOException) {
            close()
            throw IOException("Соединение с агентом потеряно", e)
        } ?: return null
        return runCatching { JSONObject(line) }.getOrNull()
    }

    companion object {
        const val DEFAULT_PORT = 47801
        const val DISCOVERY_PORT = 47800
        const val PROTOCOL_VERSION = 1

        fun open(
            host: String,
            port: Int = DEFAULT_PORT,
            pin: String,
            connectTimeoutMs: Int = 6_000,
        ): AgentConnection {
            val socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = 8_000

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            return try {
                val info = handshake(socket, reader, writer, pin)
                AgentConnection(host, port, pin, socket, reader, writer, info.name, info.model, info.root)
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }
        }

        private class Handshake(val name: String, val model: String, val root: Boolean)

        /** Отдельное соединение для длинного потока (logcat). */
        fun openStream(
            host: String,
            port: Int,
            pin: String,
            command: String,
        ): CommandStream {
            val streamSocket = Socket()
            streamSocket.tcpNoDelay = true
            streamSocket.connect(InetSocketAddress(host, port), 5_000)
            streamSocket.soTimeout = 0
            val streamReader = BufferedReader(
                InputStreamReader(streamSocket.getInputStream(), Charsets.UTF_8),
            )
            val streamWriter = BufferedWriter(
                OutputStreamWriter(streamSocket.getOutputStream(), Charsets.UTF_8),
            )

            try {
                handshake(streamSocket, streamReader, streamWriter, pin)
                writeLine(
                    streamWriter,
                    JSONObject().put("t", "shs").put("id", 1).put("cmd", command),
                )
            } catch (e: Exception) {
                runCatching { streamSocket.close() }
                throw e
            }

            return object : CommandStream {
                override fun read(): ByteArray? {
                    return try {
                        val line = streamReader.readLine() ?: return null
                        val frame = runCatching { JSONObject(line) }.getOrNull() ?: return ByteArray(0)
                        when (frame.optString("t")) {
                            "chunk" -> Base64.decode(frame.optString("b64"), Base64.NO_WRAP)
                            "end" -> null
                            "err" -> throw IOException(
                                frame.optString("msg").ifBlank { "Поток прерван" },
                            )

                            else -> ByteArray(0)
                        }
                    } catch (_: IOException) {
                        runCatching { streamSocket.close() }
                        null
                    }
                }

                override fun close() {
                    runCatching { streamSocket.close() }
                }
            }
        }

        private fun handshake(
            socket: Socket,
            reader: BufferedReader,
            writer: BufferedWriter,
            pin: String,
        ): Handshake {
            writeLine(
                writer,
                JSONObject()
                    .put("t", "hello")
                    .put("pin", pin)
                    .put("ver", PROTOCOL_VERSION),
            )

            val line = reader.readLine() ?: throw IOException("Агент закрыл соединение")
            val answer = runCatching { JSONObject(line) }.getOrElse {
                throw IOException("Агент ответил непонятно")
            }
            if (answer.optString("t") != "welcome") {
                throw IOException("Неожиданный ответ агента")
            }
            if (!answer.optBoolean("ok", false)) {
                if (answer.optString("err") == "pin") {
                    throw AgentPinException(
                        "Неверный PIN. Посмотрите PIN в приложении «PicoPilot Агент» на шлеме.",
                    )
                }
                throw IOException("Агент отклонил подключение")
            }
            return Handshake(
                answer.optString("name", "Pico"),
                answer.optString("model", "Pico"),
                answer.optBoolean("root", false),
            )
        }

        private fun writeLine(writer: BufferedWriter, json: JSONObject) {
            writer.write(json.toString())
            writer.write("\n")
            writer.flush()
        }
    }
}
