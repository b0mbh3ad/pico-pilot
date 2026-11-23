package com.picopilot.data

object DeviceParser {

    fun parse(raw: String, override: PicoModel?): DeviceInfo {
        val sections = splitSections(raw)
        val props = parseProps(sections["PROPS"].orEmpty())

        val autoModel = PicoModels.detect(props)
        val model = override ?: autoModel

        val battery = sections["BATT"].orEmpty()
        val level = intAfter(battery, "level") ?: -1
        val status = intAfter(battery, "status") ?: 0
        val batteryTemp = intAfter(battery, "temperature")?.let { it / 10f }
        val voltage = intAfter(battery, "voltage")

        val diskLine = sections["DF"].orEmpty().lines()
            .lastOrNull { it.contains("/data") && it.trim().isNotEmpty() }
            ?: sections["DF"].orEmpty().lines().lastOrNull { it.trim().isNotEmpty() }
        val diskFields = diskLine?.trim()?.split(Regex("\\s+")).orEmpty()
        val totalKb = diskFields.getOrNull(1)?.toLongOrNull() ?: 0L
        val freeKb = diskFields.getOrNull(3)?.toLongOrNull() ?: 0L

        val uptime = sections["UP"].orEmpty().trim().split(Regex("\\s+"))
            .firstOrNull()?.toDoubleOrNull()?.toLong() ?: 0L

        val wm = sections["WM"].orEmpty()
        val resolution = Regex("Physical size:\\s*(\\d+\\s*[x×]\\s*\\d+)").find(wm)
            ?.groupValues?.get(1)?.replace(" ", "")
            ?: model.resolution
        val density = Regex("Physical density:\\s*(\\d+)").find(wm)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val ip = Regex("inet\\s+(\\d{1,3}(?:\\.\\d{1,3}){3})")
            .findAll(sections["IP"].orEmpty())
            .map { it.groupValues[1] }
            .firstOrNull { !it.startsWith("127.") }
            ?: props["dhcp.eth0.ipaddress"]

        val cpuTemp = sections["THERM"].orEmpty().lines()
            .mapNotNull { it.trim().toDoubleOrNull() }
            .mapNotNull { normalizeTemp(it) }
            .filter { it in 1.0..120.0 }
            .maxOrNull()

        val audio = sections["VOL"].orEmpty()
        val volumeMax = intAfter(audio, "Max") ?: 15
        val volumeLevel = intAfter(audio, "Current") ?: -1
        val volumeMuted = audio.contains("Muted: true")

        val brightness = sections["BRIGHT"].orEmpty().trim().toIntOrNull() ?: -1

        val androidVersion = props["ro.build.version.release"].orEmpty().ifBlank { "—" }
        val sdk = props["ro.build.version.sdk"]?.toIntOrNull() ?: 0
        val serial = sequenceOf(
            props["ro.serialno"],
            props["ro.boot.serialno"],
            props["persist.sys.serialno"],
        ).firstOrNull { !it.isNullOrBlank() } ?: "—"

        val picoOs = sequenceOf(
            props["ro.pico.os.version"],
            props["ro.pico.version"],
            props["ro.build.pico.version"],
            props["ro.product.pico.version"],
            props["persist.sys.pico.version"],
            props["ro.vr.os.version"],
            props["ro.build.display.id"],
            props["ro.build.version.incremental"],
        ).firstOrNull { candidate ->
            !candidate.isNullOrBlank() && Regex("\\d+\\.\\d+").containsMatchIn(candidate)
        }?.let { extractVersion(it) }

        return DeviceInfo(
            model = model,
            autoModel = autoModel,
            modelOverridden = override != null,
            rawModel = props["ro.product.model"].orEmpty().ifBlank { "—" },
            rawDevice = props["ro.product.device"].orEmpty().ifBlank { "—" },
            androidVersion = androidVersion,
            sdk = sdk,
            buildId = props["ro.build.display.id"].orEmpty().ifBlank { "—" },
            serial = serial,
            picoOs = picoOs,
            fingerprint = props["ro.build.fingerprint"].orEmpty().ifBlank { "—" },
            batteryLevel = level,
            charging = status == 2 || status == 5,
            batteryStatus = batteryStatusText(status),
            batteryTempC = batteryTemp,
            voltageMv = voltage,
            storageTotalKb = totalKb,
            storageFreeKb = freeKb,
            uptimeSeconds = uptime,
            ip = ip,
            resolution = resolution,
            density = density,
            cpuTempC = cpuTemp?.toFloat(),
            volumeLevel = volumeLevel,
            volumeMax = volumeMax.coerceAtLeast(1),
            volumeMuted = volumeMuted,
            brightness = brightness,
            props = props,
        )
    }

    private fun splitSections(raw: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var current: String? = null
        val builder = StringBuilder()
        for (line in raw.lineSequence()) {
            if (line.startsWith("@@PP_")) {
                current?.let { result[it] = builder.toString() }
                current = line.removePrefix("@@PP_").trim()
                builder.setLength(0)
            } else if (current != null) {
                builder.append(line).append('\n')
            }
        }
        current?.let { result[it] = builder.toString() }
        return result
    }

    private fun parseProps(raw: String): Map<String, String> {
        val regex = Regex("^\\[([^]]+)]:\\s*\\[(.*)]$")
        val result = LinkedHashMap<String, String>()
        for (line in raw.lineSequence()) {
            val match = regex.matchEntire(line.trim()) ?: continue
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private fun intAfter(text: String, key: String): Int? =
        Regex("$key:\\s*(-?\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()

    private fun extractVersion(value: String): String =
        Regex("\\d+\\.\\d+(?:\\.\\d+)?").find(value)?.value ?: value

    private fun normalizeTemp(value: Double): Double? = when {
        value <= 0 -> null
        value > 10_000 -> value / 1000.0
        value > 1_000 -> value / 100.0
        value > 200 -> value / 10.0
        else -> value
    }

    private fun batteryStatusText(status: Int): String = when (status) {
        2 -> "Заряжается"
        3 -> "Разряжается"
        4 -> "Не заряжается"
        5 -> "Заряд полный"
        else -> "Неизвестно"
    }
}
