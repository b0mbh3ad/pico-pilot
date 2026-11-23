package com.picopilot.vm

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.picopilot.adb.AdbConnection
import com.picopilot.adb.AdbException
import com.picopilot.adb.AdbKey
import com.picopilot.adb.AdbKeyStore
import com.picopilot.data.DeviceInfo
import com.picopilot.data.DeviceRepository
import com.picopilot.data.PicoModel
import com.picopilot.data.PicoModels
import com.picopilot.net.AgentConnection
import com.picopilot.net.AgentDiscovery
import com.picopilot.net.AgentPinException
import com.picopilot.net.CommandStream
import com.picopilot.net.NetworkScanner
import com.picopilot.net.TransportKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream

class PicoViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val prefs = app.getSharedPreferences("picopilot", Context.MODE_PRIVATE)
    private var key: AdbKey = AdbKeyStore.load(app)
    private var repo = DeviceRepository(key)

    private var currentHost: String? = null
    private var currentPort: Int = AdbConnection.DEFAULT_PORT
    private var currentKind: TransportKind = TransportKind.ADB
    private var currentPin: String = ""
    private var lastNonZeroVolume = 8
    private var connectJob: Job? = null
    private var refreshJob: Job? = null
    private var volumeJob: Job? = null
    private var logsJob: Job? = null
    private var logsConnection: CommandStream? = null
    private val refreshMutex = Mutex()

    val conn = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val info = MutableStateFlow<DeviceInfo?>(null)
    val volume = MutableStateFlow(VolumeUi())
    val brightness = MutableStateFlow(128)
    val latency = MutableStateFlow<Int?>(null)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow<String?>(null)
    val savedHosts = MutableStateFlow<List<String>>(emptyList())
    val scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val agentPin = MutableStateFlow("")
    val transport = MutableStateFlow(TransportKind.ADB)
    val modelOverride = MutableStateFlow<PicoModel?>(null)
    val keyFingerprint = MutableStateFlow(key.fingerprint)

    val shot = MutableStateFlow<Bitmap?>(null)
    val shotFile = MutableStateFlow<File?>(null)
    val eyeMode = MutableStateFlow(EyeMode.BOTH)
    val recording = MutableStateFlow(false)
    val recordFile = MutableStateFlow<File?>(null)

    val apps = MutableStateFlow<List<String>>(emptyList())
    val appsLoading = MutableStateFlow(false)
    val includeSystemApps = MutableStateFlow(false)

    val logs = MutableStateFlow<List<String>>(emptyList())
    val logsRunning = MutableStateFlow(false)
    val terminal = MutableStateFlow<List<TerminalLine>>(emptyList())

    val appVersion: String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull() ?: "1.0"

    init {
        savedHosts.value = prefs.getString("hosts", "").orEmpty()
            .split("|")
            .filter { it.isNotBlank() }
        modelOverride.value = PicoModels.byId(prefs.getString("model_override", null))
        eyeMode.value = runCatching {
            EyeMode.valueOf(prefs.getString("eye_mode", EyeMode.BOTH.name)!!)
        }.getOrDefault(EyeMode.BOTH)
        currentPin = prefs.getString("last_pin", "").orEmpty()
        agentPin.value = currentPin
        val savedKind = prefs.getString("last_kind", null)
        currentKind = runCatching {
            // Если настройки остались от версии с ADB — продолжаем в ADB, а не ломаемся.
            TransportKind.valueOf(savedKind ?: TransportKind.ADB.name)
        }.getOrDefault(TransportKind.ADB)
        transport.value = currentKind
        val lastHost = prefs.getString("last_host", null)
        // Автоподключение только если режим уже известен, иначе после обновления
        // приложение будет стучаться по старому ADB-адресу и показывать ошибку.
        if (!lastHost.isNullOrBlank() && savedKind != null) {
            connect(
                lastHost,
                prefs.getInt("last_port", defaultPort(currentKind)),
                currentKind,
                currentPin,
                auto = true,
            )
        }
    }

    // ------------------------------------------------------------ подключение

    fun connect(
        host: String,
        port: Int = AgentConnection.DEFAULT_PORT,
        kind: TransportKind = TransportKind.AGENT,
        pin: String = agentPin.value,
        auto: Boolean = false,
    ) {
        val raw = host.trim().removePrefix("http://").removeSuffix("/")
        if (raw.isEmpty()) {
            message.value = "Введите IP-адрес шлема"
            return
        }

        val targetHost: String
        val targetPort: Int
        if (raw.count { it == ':' } == 1) {
            targetHost = raw.substringBefore(':').trim()
            targetPort = raw.substringAfter(':').trim().toIntOrNull() ?: port
        } else {
            targetHost = raw
            targetPort = port
        }
        if (targetHost.isEmpty()) {
            message.value = "Введите IP-адрес шлема"
            return
        }

        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            stopAutoRefresh()
            stopLogs()
            withContext(Dispatchers.IO) { runCatching { repo.disconnect() } }

            conn.value = ConnState.Connecting
            info.value = null
            try {
                val parsed = repo.connect(targetHost, targetPort, kind, pin) {
                    conn.value = ConnState.AwaitingApproval
                }
                currentHost = targetHost
                currentPort = targetPort
                currentKind = kind
                currentPin = pin
                agentPin.value = pin
                transport.value = kind
                val effective = applyOverride(parsed)
                info.value = effective
                conn.value = ConnState.Connected(targetHost, targetPort)
                latency.value = runCatching { repo.ping() }.getOrNull()
                rememberHost(targetHost, targetPort, kind)
                applyInfo(effective)
                startAutoRefresh()
                loadApps(includeSystemApps.value, silent = true)
                if (!auto) message.value = "Подключено: ${effective.model.name}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: AgentPinException) {
                conn.value = ConnState.Error(e.message ?: "Неверный PIN")
                message.value = e.message
                withContext(Dispatchers.IO) { runCatching { repo.disconnect() } }
            } catch (e: Exception) {
                conn.value = ConnState.Error(humanMessage(e))
                withContext(Dispatchers.IO) { runCatching { repo.disconnect() } }
            }
        }
    }

    private fun defaultPort(kind: TransportKind): Int = when (kind) {
        TransportKind.AGENT -> AgentConnection.DEFAULT_PORT
        TransportKind.ADB -> AdbConnection.DEFAULT_PORT
    }

    fun reconnect() {
        val host = currentHost ?: prefs.getString("last_host", null) ?: return
        connect(host, currentPort, currentKind, currentPin)
    }

    fun disconnect() {
        connectJob?.cancel()
        viewModelScope.launch {
            stopAutoRefresh()
            stopLogs()
            withContext(Dispatchers.IO) { runCatching { repo.disconnect() } }
            conn.value = ConnState.Disconnected
            info.value = null
            latency.value = null
        }
    }

    fun resetAdbKey() {
        viewModelScope.launch {
            stopAutoRefresh()
            stopLogs()
            withContext(Dispatchers.IO) { runCatching { repo.disconnect() } }
            AdbKeyStore.reset(app)
            key = AdbKeyStore.load(app)
            repo = DeviceRepository(key)
            keyFingerprint.value = key.fingerprint
            conn.value = ConnState.Disconnected
            info.value = null
            message.value = "Ключ сброшен. Подключитесь заново и подтвердите отладку в шлеме."
        }
    }

    fun refreshInfo() {
        viewModelScope.launch {
            try {
                refreshInternal()
                message.value = "Обновлено"
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    private suspend fun refreshInternal() {
        refreshMutex.withLock {
            val parsed = repo.refresh()
            val effective = applyOverride(parsed)
            info.value = effective
            applyInfo(effective)
            val host = currentHost
            if (host != null && conn.value !is ConnState.Connected) {
                conn.value = ConnState.Connected(host, currentPort)
            }
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5_000)
                try {
                    refreshInternal()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is IOException) {
                        val text = humanMessage(e)
                        message.value = text
                        conn.value = ConnState.Error(text)
                        runCatching { repo.disconnect() }
                        if (currentKind != TransportKind.AGENT) break
                        if (!trySilentReconnect()) break
                    }
                }
            }
        }
    }

    /**
     * Агент на шлеме после сна поднимается сам, поэтому телефон пробует
     * вернуть соединение молча — как с USB-донглом, без действий пользователя.
     */
    private suspend fun trySilentReconnect(): Boolean {
        val host = currentHost ?: return false
        repeat(10) {
            delay(3_000)
            val ok = runCatching {
                repo.connect(host, currentPort, currentKind, currentPin) {}
            }.isSuccess
            if (ok) {
                conn.value = ConnState.Connected(host, currentPort)
                message.value = "Подключение к шлему восстановлено"
                return true
            }
        }
        return false
    }

    private fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun applyOverride(source: DeviceInfo): DeviceInfo {
        val chosen = modelOverride.value ?: return source
        return source.copy(model = chosen, modelOverridden = true)
    }

    private fun applyInfo(device: DeviceInfo) {
        if (device.volumeLevel >= 0) {
            val level = device.volumeLevel.coerceIn(0, device.volumeMax)
            volume.value = VolumeUi(level, device.volumeMax, device.volumeMuted || level == 0)
            if (level > 0) lastNonZeroVolume = level
        }
        if (device.brightness >= 0) brightness.value = device.brightness
    }

    private fun rememberHost(host: String, port: Int, kind: TransportKind) {
        val entry = "$host:$port"
        val updated = (listOf(entry) + savedHosts.value.filterNot { it == entry }).take(8)
        savedHosts.value = updated
        prefs.edit()
            .putString("hosts", updated.joinToString("|"))
            .putString("last_host", host)
            .putInt("last_port", port)
            .putString("last_kind", kind.name)
            .putString("last_pin", currentPin)
            .apply()
    }

    fun setModelOverride(model: PicoModel?) {
        modelOverride.value = model
        prefs.edit().apply {
            if (model == null) remove("model_override") else putString("model_override", model.id)
        }.apply()
        val current = info.value ?: return
        info.value = current.copy(
            model = model ?: current.autoModel,
            modelOverridden = model != null,
        )
    }

    fun scanNetwork() {
        if (scan.value is ScanState.Scanning) return
        scan.value = ScanState.Scanning
        viewModelScope.launch {
            try {
                val (agents, adb) = coroutineScope {
                    val agentSearch = async(Dispatchers.IO) {
                        runCatching { AgentDiscovery.find() }.getOrDefault(emptyList())
                    }
                    val adbSearch = async(Dispatchers.IO) {
                        runCatching { NetworkScanner.scan(5555) }.getOrDefault(emptyList())
                    }
                    agentSearch.await() to adbSearch.await()
                }
                scan.value = ScanState.Done(agents, adb)
            } catch (e: Exception) {
                scan.value = ScanState.Failed(humanMessage(e))
            }
        }
    }

    fun resetScan() {
        scan.value = ScanState.Idle
    }

    // ------------------------------------------------------------- управление

    fun setVolume(level: Int) {
        val state = volume.value
        val clamped = level.coerceIn(0, state.max)
        val previous = state.level
        volume.value = state.copy(level = clamped, muted = clamped == 0)
        if (clamped > 0) lastNonZeroVolume = clamped

        volumeJob?.cancel()
        volumeJob = viewModelScope.launch {
            // Слайдер шлёт одно значение, но кнопки ±1 могут нажимать быстро.
            delay(120)
            try {
                if (repo.setVolume(clamped, state.max)) return@launch

                // Команда не сработала — дожимаем кнопками громкости.
                repo.setVolumeWithKeys(clamped, previous)
                val real = repo.mediaVolumeLevel()
                if (real != null) {
                    volume.value = volume.value.copy(level = real, muted = real == 0)
                    if (real > 0) lastNonZeroVolume = real
                    if (real != clamped) {
                        message.value = "Шлем не даёт выставить громкость: сейчас $real из ${state.max}"
                    }
                } else if (previous != clamped) {
                    message.value = "Громкость не подтвердилась — проверьте вручную"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    fun nudgeVolume(delta: Int) {
        setVolume(volume.value.level + delta)
    }

    fun toggleMute() {
        val state = volume.value
        if (state.level > 0) {
            lastNonZeroVolume = state.level
            setVolume(0)
        } else {
            setVolume(lastNonZeroVolume.coerceIn(1, state.max))
        }
    }

    fun setBrightness(value: Int) {
        val clamped = value.coerceIn(0, 255)
        brightness.value = clamped
        viewModelScope.launch {
            try {
                repo.setBrightness(clamped)
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    fun tap(x: Int, y: Int) = launchOp { repo.tap(x, y) }

    fun sendText(text: String) {
        if (text.isBlank()) return
        launchOp {
            repo.sendText(text)
            message.value = "Текст отправлен в шлем"
        }
    }

    fun reboot(mode: String? = null) {
        viewModelScope.launch {
            try {
                repo.reboot(mode)
                message.value = when (mode) {
                    null -> "Перезагружаю шлем…"
                    "recovery" -> "Перезагрузка в recovery…"
                    "bootloader" -> "Перезагрузка в bootloader…"
                    else -> "Перезагрузка: $mode…"
                }
                afterPowerAction()
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    fun powerOff() {
        viewModelScope.launch {
            try {
                repo.powerOff()
                message.value = "Выключаю шлем…"
                afterPowerAction()
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    private suspend fun afterPowerAction() {
        delay(2_500)
        stopAutoRefresh()
        stopLogs()
        withContext(Dispatchers.IO) { runCatching { repo.disconnect() } }
        conn.value = ConnState.Error("Шлем перезагружается — подключитесь снова")
        if (currentKind == TransportKind.AGENT) {
            delay(20_000)
            if (trySilentReconnect()) startAutoRefresh()
        }
    }

    // ------------------------------------------------------------------ экран

    fun takeScreenshot() {
        if (busy.value != null) return
        viewModelScope.launch {
            busy.value = "Делаю скриншот…"
            try {
                val bytes = repo.screenshot()
                val result = decodeShot(bytes)
                    ?: throw AdbException("Не удалось декодировать скриншот")
                shot.value = result.bitmap
                shotFile.value = writeShot(
                    if (result.cropped) encodePng(result.bitmap) else bytes,
                )
                message.value = when (eyeMode.value) {
                    EyeMode.BOTH -> "Скриншот готов"
                    EyeMode.LEFT -> "Скриншот: левый глаз"
                    EyeMode.RIGHT -> "Скриншот: правый глаз"
                }
            } catch (e: Exception) {
                onFailure(e)
            } finally {
                busy.value = null
            }
        }
    }

    fun setEyeMode(mode: EyeMode) {
        if (eyeMode.value == mode) return
        eyeMode.value = mode
        prefs.edit().putString("eye_mode", mode.name).apply()
        // Если подключение есть — сразу переснимаем, чтобы было видно разницу.
        if (conn.value is ConnState.Connected) takeScreenshot()
    }

    private data class Shot(val bitmap: Bitmap, val cropped: Boolean)

    /**
     * Pico отдаёт стереокадр «два глаза рядом». Режем его на половины, не декодируя
     * весь кадр целиком — иначе телефон может упасть по памяти на 4320×2160.
     */
    private fun decodeShot(bytes: ByteArray): Shot? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        val mode = eyeMode.value

        if (mode == EyeMode.BOTH || width <= 0 || height <= 0 || width < height * 1.4f) {
            val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            return Shot(full, cropped = false)
        }

        val half = width / 2
        val left = if (mode == EyeMode.LEFT) 0 else half
        val decoder = runCatching {
            BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        }.getOrNull()

        if (decoder != null) {
            try {
                val region = decoder.decodeRegion(
                    Rect(left, 0, left + half, height),
                    BitmapFactory.Options(),
                )
                if (region != null) return Shot(region, cropped = true)
            } finally {
                decoder.recycle()
            }
        }

        val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val cropped = Bitmap.createBitmap(full, left, 0, half, height)
        return Shot(cropped, cropped = true)
    }

    private fun encodePng(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun writeShot(bytes: ByteArray): File {
        val dir = File(app.cacheDir, "screenshots").apply { mkdirs() }
        val file = File(dir, "picopilot_${System.currentTimeMillis()}.png")
        file.writeBytes(bytes)
        return file
    }

    fun toggleRecording() {
        if (recording.value) stopRecording() else startRecording()
    }

    private fun startRecording() {
        viewModelScope.launch {
            busy.value = "Запускаю запись…"
            try {
                repo.startRecording(180)
                recording.value = true
                message.value = "Запись пошла (до 3 минут)"
            } catch (e: Exception) {
                onFailure(e)
            } finally {
                busy.value = null
            }
        }
    }

    private fun stopRecording() {
        viewModelScope.launch {
            busy.value = "Сохраняю видео…"
            try {
                repo.stopRecording()
                val dir = File(app.cacheDir, "recordings").apply { mkdirs() }
                val file = File(dir, "picopilot_${System.currentTimeMillis()}.mp4")
                file.outputStream().use { repo.pullRecording(it) }
                recording.value = false
                recordFile.value = file.takeIf { it.length() > 0 }
                message.value = if (file.length() > 0) "Видео сохранено" else "Запись не удалась"
            } catch (e: Exception) {
                recording.value = false
                onFailure(e)
            } finally {
                busy.value = null
            }
        }
    }

    // ------------------------------------------------------------- приложения

    fun loadApps(includeSystem: Boolean = includeSystemApps.value, silent: Boolean = false) {
        includeSystemApps.value = includeSystem
        viewModelScope.launch {
            if (!silent) appsLoading.value = true
            try {
                apps.value = repo.listApps(includeSystem)
            } catch (e: Exception) {
                if (!silent) onFailure(e)
            } finally {
                appsLoading.value = false
            }
        }
    }

    fun launchApp(packageName: String) = launchOp {
        repo.launchApp(packageName)
        message.value = "Запускаю ${prettyPackage(packageName)}"
    }

    fun forceStop(packageName: String) = launchOp {
        repo.forceStop(packageName)
        message.value = "Остановлено: ${prettyPackage(packageName)}"
    }

    fun uninstallApp(packageName: String) {
        viewModelScope.launch {
            busy.value = "Удаляю…"
            try {
                val result = repo.uninstallApp(packageName)
                if (result.contains("Success", ignoreCase = true)) {
                    apps.value = apps.value - packageName
                    message.value = "Удалено: ${prettyPackage(packageName)}"
                } else {
                    message.value = result.trim().take(160)
                }
            } catch (e: Exception) {
                onFailure(e)
            } finally {
                busy.value = null
            }
        }
    }

    fun clearAppData(packageName: String) = launchOp {
        val result = repo.clearApp(packageName)
        message.value = if (result.contains("Success", ignoreCase = true)) {
            "Данные ${prettyPackage(packageName)} очищены"
        } else {
            result.trim().take(160)
        }
    }

    fun installApk(uri: Uri) {
        viewModelScope.launch {
            busy.value = "Готовлю APK…"
            try {
                val resolver = app.contentResolver
                val size = querySize(resolver, uri)
                val name = queryName(resolver, uri) ?: "APK"
                val stream = resolver.openInputStream(uri) ?: throw IOException("Не удалось открыть файл")
                stream.use { input ->
                    val result = repo.installApk(input, size) { sent, total ->
                        busy.value = if (total > 0) {
                            "Отправка APK: ${(sent * 100 / total)}%"
                        } else {
                            "Отправка APK: ${sent / 1024 / 1024} МБ"
                        }
                    }
                    if (result.contains("Success", ignoreCase = true)) {
                        message.value = "Установлено: $name"
                        loadApps(includeSystemApps.value, silent = true)
                    } else {
                        message.value = "pm install: ${result.trim().takeLast(180)}"
                    }
                }
            } catch (e: Exception) {
                onFailure(e)
            } finally {
                busy.value = null
            }
        }
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
                } else -1L
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                } else null
            }
        }.getOrNull()
    }

    // -------------------------------------------------------------------- логи

    fun startLogs() {
        if (logsJob != null) return
        logsRunning.value = true
        logsJob = viewModelScope.launch(Dispatchers.IO) {
            var connection: CommandStream? = null
            try {
                connection = repo.openStream("logcat -v time")
                logsConnection = connection
                while (isActive) {
                    val chunk = connection.read() ?: break
                    appendLogText(String(chunk, Charsets.UTF_8))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive) message.value = "Логи: ${humanMessage(e)}"
            } finally {
                runCatching { connection?.close() }
                logsConnection = null
                logsRunning.value = false
                logsJob = null
            }
        }
    }

    fun stopLogs() {
        logsJob?.cancel()
        logsJob = null
        runCatching { logsConnection?.close() }
        logsConnection = null
        logsRunning.value = false
    }

    fun clearLogs() {
        logs.value = emptyList()
    }

    private fun appendLogText(text: String) {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return
        logs.value = (logs.value + lines).takeLast(2_500)
    }

    // ---------------------------------------------------------------- терминал

    fun runCommand(command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        terminal.value = terminal.value + TerminalLine("$ $cmd", TerminalKind.Command)
        viewModelScope.launch {
            try {
                val output = repo.shell(cmd, 120_000)
                terminal.value = terminal.value + TerminalLine(
                    output.trimEnd().ifEmpty { "(пусто)" },
                    TerminalKind.Output,
                )
            } catch (e: Exception) {
                terminal.value = terminal.value +
                    TerminalLine("Ошибка: ${humanMessage(e)}", TerminalKind.Error)
            }
        }
    }

    fun clearTerminal() {
        terminal.value = emptyList()
    }

    // ------------------------------------------------------------------ служебное

    fun consumeMessage() {
        message.value = null
    }

    fun notify(text: String) {
        message.value = text
    }

    private fun launchOp(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    private fun onFailure(e: Throwable) {
        if (e is CancellationException) return
        val text = humanMessage(e)
        message.value = text
        if (e is IOException) {
            stopAutoRefresh()
            if (conn.value is ConnState.Connected) {
                conn.value = ConnState.Error(text)
            }
            viewModelScope.launch { runCatching { repo.disconnect() } }
        }
    }

    private fun humanMessage(e: Throwable): String = when (e) {
        is AgentPinException -> e.message ?: "Неверный PIN агента"
        is java.net.ConnectException ->
            "Шлем не отвечает. Проверьте, что на шлеме запущен «PicoPilot Агент» и вы в одной Wi-Fi сети."
        is java.net.SocketTimeoutException ->
            "Таймаут. Телефон и шлем точно в одной Wi-Fi сети?"
        is java.net.UnknownHostException -> "Адрес не найден"
        is AdbException -> e.message ?: "Ошибка обмена с шлемом"
        is java.net.SocketException -> "Соединение с шлемом потеряно"
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun prettyPackage(packageName: String): String =
        packageName.substringAfterLast('.').replace('_', ' ')

    override fun onCleared() {
        stopLogs()
        stopAutoRefresh()
        repo.closeNow()
        super.onCleared()
    }
}
