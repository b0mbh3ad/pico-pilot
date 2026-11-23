package com.picopilot.net

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/** Как телефон общается со шлемом. */
enum class TransportKind {
    /** Свой агент на шлеме: поиск в сети, PIN, свой протокол. */
    AGENT,

    /** Классический ADB по Wi-Fi (порт 5555). */
    ADB,
}

/** Поток вывода длинной команды (logcat). */
interface CommandStream : Closeable {
    fun read(): ByteArray?
}

/**
 * Одинаковый набор операций для обоих транспортов, чтобы остальное приложение
 * не знало, через что именно оно управляет шлемом.
 */
interface DeviceChannel : Closeable {

    val isClosed: Boolean

    fun shell(command: String, timeoutMs: Int): String

    fun execBytes(command: String, timeoutMs: Int): ByteArray

    fun push(
        source: InputStream,
        remotePath: String,
        size: Long,
        onProgress: (sent: Long, total: Long) -> Unit,
    )

    fun pull(
        remotePath: String,
        sink: OutputStream,
        onProgress: (received: Long) -> Unit,
    ): Long

    fun ping(): Int

    fun stream(command: String): CommandStream
}
