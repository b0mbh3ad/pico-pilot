package com.picopilot.data

import com.picopilot.adb.AdbConnection
import com.picopilot.adb.AdbException
import com.picopilot.net.AdbChannel
import com.picopilot.net.AgentConnection
import com.picopilot.net.CommandStream
import com.picopilot.net.DeviceChannel
import com.picopilot.net.TransportKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class DeviceRepository(private val key: com.picopilot.adb.AdbKey) {

    private var connection: DeviceChannel? = null
    private val mutex = Mutex()

    var host: String? = null
        private set

    var port: Int = AdbConnection.DEFAULT_PORT
        private set

    var kind: TransportKind = TransportKind.ADB
        private set

    private var pin: String? = null

    suspend fun connect(
        host: String,
        port: Int,
        kind: TransportKind,
        pin: String?,
        onAuthPrompt: () -> Unit,
    ): DeviceInfo = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked()
            val opened: DeviceChannel = when (kind) {
                TransportKind.ADB -> AdbChannel(
                    AdbConnection.open(host, port, key, onAuthPrompt = onAuthPrompt),
                )

                TransportKind.AGENT -> AgentConnection.open(host, port, pin.orEmpty())
            }
            connection = opened
            this@DeviceRepository.host = host
            this@DeviceRepository.port = port
            this@DeviceRepository.kind = kind
            this@DeviceRepository.pin = pin
            try {
                DeviceParser.parse(opened.shell(INFO_SCRIPT, 30_000), null)
            } catch (e: Exception) {
                opened.close()
                connection = null
                throw e
            }
        }
    }

    suspend fun refresh(): DeviceInfo = run { it ->
        DeviceParser.parse(it.shell(INFO_SCRIPT, 30_000), null)
    }

    suspend fun shell(command: String, timeoutMs: Int = 30_000): String = run { it ->
        it.shell(command, timeoutMs)
    }

    suspend fun <T> run(block: (DeviceChannel) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val active = connection ?: throw AdbException("Нет подключения к шлему")
            try {
                block(active)
            } catch (e: IOException) {
                active.close()
                connection = null
                throw e
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock { closeLocked() }
    }

    fun closeNow() {
        connection?.close()
        connection = null
    }

    private fun closeLocked() {
        connection?.close()
        connection = null
    }

    suspend fun ping(): Int = run { it.ping() }

    suspend fun openStream(command: String): CommandStream = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentHost = host ?: throw AdbException("Нет подключения к шлему")
            when (kind) {
                TransportKind.ADB -> AdbChannel(
                    AdbConnection.open(currentHost, port, key),
                ).stream(command)

                TransportKind.AGENT -> AgentConnection
                    .openStream(currentHost, port, pin.orEmpty(), command)
            }
        }
    }

    // ------------------------------------------------------------- управление

    suspend fun tap(x: Int, y: Int) = run {
        it.shell("input tap ${x.coerceAtLeast(0)} ${y.coerceAtLeast(0)}", 10_000)
    }

    suspend fun sendText(text: String) = run {
        val escaped = text
            .replace("\\", "")
            .replace("'", "")
            .replace("\"", "")
            .replace("\n", " ")
            .replace(" ", "%s")
        it.shell("input text '$escaped'", 10_000)
    }

    suspend fun setVolume(level: Int, max: Int): Boolean = run { conn ->
        val clamped = level.coerceIn(0, max.coerceAtLeast(1))

        // На Pico часть команд молча ничего не делает, поэтому проверяем фактический уровень.
        val commands = listOf(
            "cmd media_session volume --stream 3 --set $clamped",
            "media volume --stream 3 --set $clamped",
        )
        for (command in commands) {
            runCatching { conn.shell("$command 2>&1", 10_000) }
            if (mediaVolume(conn) == clamped) return@run true
        }
        false
    }

    /** Добивает громкость аппаратными кнопками: их Pico понимает всегда. */
    suspend fun setVolumeWithKeys(target: Int, fallbackCurrent: Int? = null) = run { conn ->
        val current = mediaVolume(conn) ?: fallbackCurrent ?: return@run
        val delta = (target - current).coerceIn(-30, 30)
        if (delta == 0) return@run
        val key = if (delta > 0) 24 else 25
        repeat(kotlin.math.abs(delta)) {
            conn.shell("input keyevent $key", 15_000)
        }
    }

    suspend fun mediaVolumeLevel(): Int? = run { mediaVolume(it) }

    private fun mediaVolume(conn: DeviceChannel): Int? {
        val dump = runCatching {
            conn.shell("dumpsys audio 2>/dev/null | grep -m1 -A 14 \"STREAM_MUSIC:\"", 12_000)
        }.getOrNull() ?: return null
        return parseMediaVolume(dump)
    }

    internal fun parseMediaVolume(text: String): Int? {
        Regex("streamVolume:\\s*(\\d+)").find(text)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("Current:\\s*(\\d+)").find(text)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("(?i)mCurrentVolume\\s*[:=]\\s*(\\d+)").find(text)
            ?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    suspend fun setBrightness(value: Int) = run { it ->
        val clamped = value.coerceIn(0, 255)
        it.shell(
            "settings put system screen_brightness_mode 0; settings put system screen_brightness $clamped",
            10_000,
        )
    }

    suspend fun reboot(mode: String? = null) = run {
        val suffix = mode?.let { " $it" }.orEmpty()
        it.shell("svc power reboot$suffix || reboot$suffix", 15_000)
    }

    suspend fun powerOff() = run {
        it.shell("svc power shutdown || reboot -p", 15_000)
    }

    // ------------------------------------------------------------------ экран

    suspend fun screenshot(): ByteArray = run { it ->
        val bytes = it.execBytes("screencap -p", 30_000)
        if (bytes.size < 8 || bytes[0] != 0x89.toByte() || bytes[1] != 0x50.toByte()) {
            val preview = bytes.take(120).toByteArray().toString(Charsets.UTF_8)
                .replace("\n", " ")
                .trim()
            throw AdbException(
                if (preview.isBlank()) "Шлем вернул пустой скриншот"
                else "Шлем вернул не PNG: $preview",
            )
        }
        bytes
    }

    suspend fun startRecording(seconds: Int = 180) = run {
        it.shell(
            "rm -f $RECORDING_PATH; nohup screenrecord --bit-rate 12000000 " +
                "--time-limit ${seconds.coerceIn(5, 180)} $RECORDING_PATH " +
                ">/dev/null 2>&1 &",
            15_000,
        )
    }

    suspend fun stopRecording(): String = run {
        it.shell("pkill -INT screenrecord 2>/dev/null; sleep 1; ls -l $RECORDING_PATH 2>/dev/null", 20_000)
    }

    suspend fun pullRecording(sink: OutputStream): Long = run { it.pull(RECORDING_PATH, sink) {} }

    // ----------------------------------------------------------- приложения

    suspend fun listApps(includeSystem: Boolean): List<String> = run { it ->
        val command = if (includeSystem) "pm list packages" else "pm list packages -3"
        it.shell(command, 40_000)
            .lineSequence()
            .map { line -> line.trim().removePrefix("package:").trim() }
            .filter { it.isNotEmpty() && it.contains('.') }
            .distinct()
            .sorted()
            .toList()
    }

    suspend fun launchApp(packageName: String): String = run { it ->
        val component = it.shell(
            "cmd package resolve-activity --brief $packageName 2>/dev/null | tail -n 1",
            15_000,
        ).trim()
        if (component.contains('/')) {
            it.shell("am start -n $component", 25_000)
        } else {
            it.shell("monkey -p $packageName -c android.intent.category.LAUNCHER 1", 25_000)
        }
    }

    suspend fun forceStop(packageName: String) = run {
        it.shell("am force-stop $packageName", 15_000)
    }

    suspend fun clearApp(packageName: String): String = run {
        it.shell("pm clear $packageName", 30_000)
    }

    suspend fun uninstallApp(packageName: String): String = run {
        it.shell("pm uninstall --user 0 $packageName", 40_000)
    }

    suspend fun installApk(
        source: InputStream,
        size: Long,
        onProgress: (Long, Long) -> Unit,
    ): String = run { it ->
        val remote = "/data/local/tmp/picopilot_install.apk"
        it.push(source, remote, size, onProgress)
        val result = it.shell("pm install -r -g $remote", 180_000)
        it.shell("rm -f $remote", 10_000)
        result
    }

    companion object {
        const val RECORDING_PATH = "/sdcard/Download/picopilot_rec.mp4"

        val INFO_SCRIPT = """
            echo @@PP_PROPS; getprop
            echo @@PP_BATT; dumpsys battery
            echo @@PP_DF; df -k /data
            echo @@PP_UP; cat /proc/uptime
            echo @@PP_WM; wm size; wm density
            echo @@PP_IP; ip -f inet addr show wlan0 2>/dev/null
            echo @@PP_THERM; for f in /sys/class/thermal/thermal_zone*/temp; do cat "${'$'}f" 2>/dev/null; echo; done
            echo @@PP_VOL; dumpsys audio | grep -m1 -A 12 "STREAM_MUSIC:"
            echo @@PP_BRIGHT; settings get system screen_brightness
            echo @@PP_END
        """.trimIndent()
    }
}
