package com.picopilot.net

import com.picopilot.adb.AdbConnection
import java.io.InputStream
import java.io.OutputStream

/** Обёртка над ADB-соединением, чтобы старый путь работал как раньше. */
class AdbChannel(private val connection: AdbConnection) : DeviceChannel {

    override val isClosed: Boolean get() = connection.isClosed

    override fun shell(command: String, timeoutMs: Int): String =
        connection.shell(command, timeoutMs)

    override fun execBytes(command: String, timeoutMs: Int): ByteArray =
        connection.execBytes(command, timeoutMs)

    override fun push(
        source: InputStream,
        remotePath: String,
        size: Long,
        onProgress: (sent: Long, total: Long) -> Unit,
    ) {
        connection.push(source, remotePath, size, onProgress)
    }

    override fun pull(
        remotePath: String,
        sink: OutputStream,
        onProgress: (received: Long) -> Unit,
    ): Long = connection.pull(remotePath, sink, onProgress)

    override fun ping(): Int = connection.ping()

    override fun stream(command: String): CommandStream =
        AdbStreamChannel(connection, connection.shellStream(command))

    override fun close() = connection.close()

    private class AdbStreamChannel(
        private val connection: AdbConnection,
        private val stream: AdbConnection.AdbStream,
    ) : CommandStream {
        override fun read(): ByteArray? = stream.read()

        override fun close() {
            runCatching { stream.close() }
            connection.close()
        }
    }
}
