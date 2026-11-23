package com.picopilot.agent

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Выполнение shell-команд на шлеме.
 *
 * Агент живёт в обычном Android-приложении, поэтому без root ему доступны только
 * команды от своего uid. С root (Magisk / KernelSU / встроенный su) доступно всё
 * то же, что и через `adb shell`.
 */
object Shell {

    private const val MAX_OUTPUT = 24 * 1024 * 1024

    @Volatile
    private var rootMode: Int = -1

    @Volatile
    var workDir: File = File("/data/local/tmp")

    data class Result(val code: Int, val output: ByteArray)

    fun hasRoot(): Boolean {
        ensureRoot()
        return rootMode > 0
    }

    private fun argv(command: String): List<String> {
        ensureRoot()
        return when (rootMode) {
            1 -> listOf("su", "-c", command)
            2 -> listOf("su", "0", "sh", "-c", command)
            3 -> listOf("su", "root", "sh", "-c", command)
            else -> listOf("sh", "-c", command)
        }
    }

    @Synchronized
    private fun ensureRoot() {
        if (rootMode >= 0) return
        rootMode = 0
        val variants = listOf(
            1 to listOf("su", "-c", "id"),
            2 to listOf("su", "0", "sh", "-c", "id"),
            3 to listOf("su", "root", "sh", "-c", "id"),
        )
        for ((mode, probe) in variants) {
            val text = runProcess(probe, 6_000, 4_096)
                ?.let { String(it.output, Charsets.UTF_8) }
                .orEmpty()
            if (text.contains("uid=0")) {
                rootMode = mode
                AgentState.log("Root получен (вариант $mode)")
                return
            }
        }
        AgentState.log("Root недоступен: работают только обычные команды")
    }

    fun exec(command: String, timeoutMs: Int = 30_000, mergeErrors: Boolean = true): Result =
        runProcess(argv(command), timeoutMs, MAX_OUTPUT, mergeErrors) ?: Result(127, ByteArray(0))

    /** Запускает процесс для потоковой выдачи (logcat и т.п.). */
    fun startProcess(command: String): Process {
        val process = ProcessBuilder(argv(command)).redirectErrorStream(true).start()
        runCatching { process.outputStream.close() }
        return process
    }

    private fun runProcess(
        argv: List<String>,
        timeoutMs: Int,
        limit: Int,
        mergeErrors: Boolean = true,
    ): Result? {
        return try {
            val process = ProcessBuilder(argv).redirectErrorStream(mergeErrors).start()
            runCatching { process.outputStream.close() }
            val buffer = ByteArrayOutputStream()
            val reader = Thread {
                runCatching {
                    process.inputStream.use { input ->
                        val chunk = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(chunk)
                            if (read < 0) break
                            val room = limit - buffer.size()
                            if (room > 0) buffer.write(chunk, 0, minOf(read, room))
                        }
                    }
                }
            }
            reader.isDaemon = true
            reader.start()

            // Если stderr не слит в stdout, его всё равно надо вычитывать, иначе процесс встанет.
            if (!mergeErrors) {
                val drain = Thread {
                    runCatching {
                        process.errorStream.use { stream ->
                            val chunk = ByteArray(8 * 1024)
                            while (stream.read(chunk) >= 0) {
                                // stderr для бинарных команд (screencap) не нужен
                            }
                        }
                    }
                }
                drain.isDaemon = true
                drain.start()
            }

            val finished = process.waitFor(timeoutMs.toLong().coerceAtLeast(500), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(1_500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            }
            reader.join(2_000)
            Result(if (finished) runCatching { process.exitValue() }.getOrDefault(-1) else -1, buffer.toByteArray())
        } catch (e: Exception) {
            Result(127, e.toString().toByteArray(Charsets.UTF_8))
        }
    }

    /** Кладёт локальный файл по нужному пути с правами 644. */
    fun installFile(tmp: File, target: String) {
        val dir = target.substringBeforeLast('/', "")
        val script = buildString {
            if (dir.isNotEmpty()) append("mkdir -p '").append(dir).append("'; ")
            append("cp '").append(tmp.absolutePath).append("' '").append(target).append("'")
            append(" && chmod 644 '").append(target).append("'")
        }
        val result = exec(script, 90_000)
        if (result.code != 0) {
            throw IOException(
                "Не удалось записать $target: " +
                    String(result.output, Charsets.UTF_8).trim().take(200),
            )
        }
    }

    /** Открывает файл на чтение, при необходимости копируя его через su. */
    fun openRead(path: String): InputStream {
        val direct = File(path)
        if (direct.isFile && direct.canRead()) return direct.inputStream()

        val tmp = File(workDir, "pull_${System.currentTimeMillis()}")
        val result = exec(
            "cp '$path' '${tmp.absolutePath}' && chmod 644 '${tmp.absolutePath}'",
            120_000,
        )
        if (!tmp.exists()) {
            throw IOException(
                "Не удалось прочитать $path: " +
                    String(result.output, Charsets.UTF_8).trim().take(160),
            )
        }
        return tmp.inputStream()
    }

    fun cleanTemp() {
        runCatching {
            workDir.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith("push_") || name.startsWith("pull_")) file.delete()
            }
        }
    }
}
