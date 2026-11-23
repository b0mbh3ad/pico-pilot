package com.picopilot.adb

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class AdbException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

/**
 * Минимальный клиент протокола ADB (host side).
 *
 * Одно соединение = один активный сервис за раз (кроме sync-сессий, которые
 * последовательно открываются и закрываются на том же сокете). Для постоянных
 * потоков (logcat) создавайте отдельное соединение.
 */
class AdbConnection private constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: OutputStream,
    private val key: AdbKey,
    @Volatile var maxData: Int,
) : Closeable {

    private val ids = AtomicInteger(1)

    @Volatile
    var isClosed: Boolean = false
        private set

    companion object {
        const val DEFAULT_PORT = 5555
        private const val MAX_PAYLOAD = 256 * 1024

        const val A_CNXN = 0x4e584e43
        const val A_AUTH = 0x48545541
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_CLSE = 0x45534c43
        const val A_WRTE = 0x45545257
        const val A_STLS = 0x534c5453

        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3

        const val A_VERSION = 0x01000001

        private val EMPTY = ByteArray(0)

        fun open(
            host: String,
            port: Int = DEFAULT_PORT,
            key: AdbKey,
            connectTimeoutMs: Int = 8_000,
            authTimeoutMs: Int = 120_000,
            onAuthPrompt: () -> Unit = {},
        ): AdbConnection {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.soTimeout = 15_000

                val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 64 * 1024))
                val output = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
                val connection = AdbConnection(socket, input, output, key, MAX_PAYLOAD)

                connection.send(A_CNXN, A_VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray(Charsets.UTF_8))

                var signed = false
                var publicKeySent = false

                while (true) {
                    val message = try {
                        connection.read()
                    } catch (timeout: SocketTimeoutException) {
                        throw AdbException(
                            "Шлем не ответил. Проверьте, что отладка по Wi-Fi включена и порт доступен.",
                            timeout,
                        )
                    }

                    when (message.command) {
                        A_CNXN -> {
                            connection.maxData = message.arg1.coerceIn(4 * 1024, MAX_PAYLOAD)
                            socket.soTimeout = 30_000
                            return connection
                        }

                        A_AUTH -> {
                            when (message.arg0) {
                                AUTH_TOKEN -> {
                                    if (!signed) {
                                        connection.send(A_AUTH, AUTH_SIGNATURE, 0, key.sign(message.payload))
                                        signed = true
                                    } else if (!publicKeySent) {
                                        socket.soTimeout = authTimeoutMs
                                        connection.send(
                                            A_AUTH,
                                            AUTH_RSAPUBLICKEY,
                                            0,
                                            key.publicKeyBlob + byteArrayOf(0),
                                        )
                                        publicKeySent = true
                                        onAuthPrompt()
                                    } else {
                                        throw AdbException(
                                            "Шлем отклонил ключ. Удалите разрешение отладки в настройках шлема и подключитесь заново."
                                        )
                                    }
                                }

                                else -> Unit
                            }
                        }

                        A_STLS -> throw AdbException(
                            "Шлем требует TLS-подключение (беспроводная отладка с кодом). " +
                                "Включите классический ADB: adb tcpip 5555 по USB."
                        )

                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
                throw e
            }
        }
    }

    // ---------------------------------------------------------------- shell

    fun shell(command: String, timeoutMs: Int = 30_000): String {
        val bytes = runService("shell:$command", timeoutMs)
        return bytes.toString(Charsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n')
    }

    /** Без pty: бинарно-безопасный вывод (скриншоты и т.п.). */
    fun execBytes(command: String, timeoutMs: Int = 60_000): ByteArray {
        return runService("exec:$command", timeoutMs)
    }

    private fun runService(name: String, timeoutMs: Int): ByteArray {
        ensureOpen()
        socket.soTimeout = timeoutMs
        val local = ids.getAndIncrement()
        send(A_OPEN, local, 0, (name + "\u0000").toByteArray(Charsets.UTF_8))

        val buffer = ByteArrayOutputStream()
        var remote = -1

        while (true) {
            val message = read()
            when {
                message.command == A_OKAY && message.arg1 == local -> remote = message.arg0

                message.command == A_WRTE && message.arg1 == local -> {
                    buffer.write(message.payload)
                    send(A_OKAY, remote, local)
                }

                message.command == A_CLSE && message.arg1 == local -> {
                    if (remote < 0) throw AdbException("Сервис недоступен: $name")
                    return buffer.toByteArray()
                }
            }
        }
    }

    fun shellStream(command: String): AdbStream {
        ensureOpen()
        socket.soTimeout = 0
        val local = ids.getAndIncrement()
        send(A_OPEN, local, 0, ("shell:$command\u0000").toByteArray(Charsets.UTF_8))
        while (true) {
            val message = read()
            when {
                message.command == A_OKAY && message.arg1 == local ->
                    return AdbStream(local, message.arg0)

                message.command == A_CLSE && message.arg1 == local ->
                    throw AdbException("Не удалось запустить: $command")
            }
        }
    }

    inner class AdbStream internal constructor(
        private val localId: Int,
        private var remoteId: Int,
    ) : Closeable {

        @Volatile
        private var finished = false

        /** Возвращает очередной кусок вывода или null в конце потока. */
        @Synchronized
        fun read(): ByteArray? {
            if (finished) return null
            while (true) {
                val message = readMessage()
                when {
                    message.command == A_WRTE && message.arg1 == localId -> {
                        send(A_OKAY, remoteId, localId)
                        return message.payload
                    }

                    message.command == A_CLSE && message.arg1 == localId -> {
                        finished = true
                        return null
                    }
                }
            }
        }

        override fun close() {
            if (finished) return
            finished = true
            runCatching { send(A_CLSE, remoteId, localId) }
        }
    }

    // ----------------------------------------------------------------- sync

    fun push(
        source: InputStream,
        remotePath: String,
        size: Long = -1,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        ensureOpen()
        socket.soTimeout = 60_000
        val service = openService("sync:")
        try {
            writeSync("SEND", "$remotePath,33188".toByteArray(Charsets.UTF_8))
            expectOkay()

            val chunkSize = (maxData - 8).coerceIn(1024, 64 * 1024)
            val buffer = ByteArray(chunkSize)
            var sent = 0L
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                writeSync("DATA", buffer.copyOf(read))
                sent += read
                onProgress(sent, size)
            }
            writeSync("DONE", leInt((System.currentTimeMillis() / 1000).toInt()))
            expectOkay()
        } finally {
            runCatching { send(A_CLSE, service.remote, service.local) }
        }
    }

    fun pull(
        remotePath: String,
        sink: OutputStream,
        onProgress: (received: Long) -> Unit = {},
    ): Long {
        ensureOpen()
        socket.soTimeout = 60_000
        val service = openService("sync:")
        var total = 0L
        try {
            writeSync("RECV", remotePath.toByteArray(Charsets.UTF_8))
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val (id, length) = readSyncHeader()
                when (id) {
                    "DATA" -> {
                        var remaining = length
                        while (remaining > 0) {
                            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                            if (read < 0) throw AdbException("Передача файла прервалась")
                            sink.write(buffer, 0, read)
                            remaining -= read
                            total += read
                            onProgress(total)
                        }
                    }

                    "DONE" -> {
                        if (length > 0) input.skipFully(length)
                        break
                    }

                    "FAIL" -> {
                        val message = ByteArray(length).also { input.readFully(it) }
                        throw AdbException(message.toString(Charsets.UTF_8))
                    }

                    else -> throw AdbException("Неожиданный ответ sync: $id")
                }
            }
        } finally {
            runCatching { send(A_CLSE, service.remote, service.local) }
        }
        return total
    }

    fun ping(): Int {
        val started = System.nanoTime()
        shell("echo ok", timeoutMs = 8_000)
        return ((System.nanoTime() - started) / 1_000_000).toInt()
    }

    private data class Service(val local: Int, val remote: Int)

    private fun openService(name: String): Service {
        val local = ids.getAndIncrement()
        send(A_OPEN, local, 0, (name + "\u0000").toByteArray(Charsets.UTF_8))
        while (true) {
            val message = read()
            when {
                message.command == A_OKAY && message.arg1 == local -> return Service(local, message.arg0)
                message.command == A_CLSE && message.arg1 == local ->
                    throw AdbException("Не удалось открыть сервис: $name")
            }
        }
    }

    private fun writeSync(id: String, payload: ByteArray) {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.put(id.toByteArray(Charsets.US_ASCII))
        header.putInt(payload.size)
        synchronized(output) {
            output.write(header.array())
            if (payload.isNotEmpty()) output.write(payload)
            output.flush()
        }
    }

    private fun readSyncHeader(): Pair<String, Int> {
        val header = ByteArray(8)
        input.readFully(header)
        val id = String(header, 0, 4, Charsets.US_ASCII)
        val length = getIntLE(header, 4)
        if (length < 0 || length > 64 * 1024 * 1024) throw AdbException("Некорректный размер блока sync")
        return id to length
    }

    private fun expectOkay() {
        val (id, length) = readSyncHeader()
        when (id) {
            "OKAY" -> if (length > 0) input.skipFully(length)
            "FAIL" -> {
                val message = ByteArray(length).also { input.readFully(it) }
                throw AdbException(message.toString(Charsets.UTF_8))
            }

            else -> throw AdbException("Неожиданный ответ sync: $id")
        }
    }

    // -------------------------------------------------------------- transport

    private fun ensureOpen() {
        if (isClosed) throw AdbException("Соединение закрыто")
    }

    private fun readMessage(): AdbMessage {
        val header = ByteArray(24)
        input.readFully(header)

        val command = getIntLE(header, 0)
        val arg0 = getIntLE(header, 4)
        val arg1 = getIntLE(header, 8)
        val length = getIntLE(header, 12)
        val magic = getIntLE(header, 20)

        if (magic != command.inv()) throw AdbException("Повреждён пакет ADB")
        if (length < 0 || length > 32 * 1024 * 1024) {
            throw AdbException("Некорректный размер пакета ADB: $length")
        }

        val payload = ByteArray(length)
        if (length > 0) input.readFully(payload)
        return AdbMessage(command, arg0, arg1, payload)
    }

    private fun read(): AdbMessage = readMessage()

    private fun send(command: Int, arg0: Int, arg1: Int, payload: ByteArray = EMPTY) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(payload.size)
        header.putInt(checksum(payload))
        header.putInt(command.inv())
        synchronized(output) {
            output.write(header.array())
            if (payload.isNotEmpty()) output.write(payload)
            output.flush()
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (byte in data) sum += byte.toInt() and 0xff
        return sum
    }

    private fun getIntLE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xff) or
            ((data[offset + 1].toInt() and 0xff) shl 8) or
            ((data[offset + 2].toInt() and 0xff) shl 16) or
            ((data[offset + 3].toInt() and 0xff) shl 24)

    private fun leInt(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun DataInputStream.skipFully(count: Int) {
        var remaining = count
        val scratch = ByteArray(8 * 1024)
        while (remaining > 0) {
            val read = read(scratch, 0, minOf(scratch.size, remaining))
            if (read < 0) throw AdbException("Поток оборвался")
            remaining -= read
        }
    }
}
