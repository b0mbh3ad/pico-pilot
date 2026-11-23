package com.picopilot.data

data class DeviceInfo(
    val model: PicoModel,
    val autoModel: PicoModel,
    val modelOverridden: Boolean,
    val rawModel: String,
    val rawDevice: String,
    val androidVersion: String,
    val sdk: Int,
    val buildId: String,
    val serial: String,
    val picoOs: String?,
    val fingerprint: String,
    val batteryLevel: Int,
    val charging: Boolean,
    val batteryStatus: String,
    val batteryTempC: Float?,
    val voltageMv: Int?,
    val storageTotalKb: Long,
    val storageFreeKb: Long,
    val uptimeSeconds: Long,
    val ip: String?,
    val resolution: String,
    val density: Int,
    val cpuTempC: Float?,
    val volumeLevel: Int,
    val volumeMax: Int,
    val volumeMuted: Boolean,
    val brightness: Int,
    val props: Map<String, String>,
) {
    val storageUsedKb: Long get() = (storageTotalKb - storageFreeKb).coerceAtLeast(0)
    val storageUsedFraction: Float
        get() = if (storageTotalKb <= 0) 0f else (storageUsedKb.toFloat() / storageTotalKb).coerceIn(0f, 1f)

    val uptimeText: String
        get() {
            val days = uptimeSeconds / 86_400
            val hours = (uptimeSeconds % 86_400) / 3_600
            val minutes = (uptimeSeconds % 3_600) / 60
            return when {
                days > 0 -> "$days д $hours ч"
                hours > 0 -> "$hours ч $minutes мин"
                else -> "$minutes мин"
            }
        }

    val resolutionPair: Pair<Int, Int>?
        get() {
            val match = Regex("(\\d{3,5})\\s*[x×]\\s*(\\d{3,5})").find(resolution) ?: return null
            return match.groupValues[1].toIntOrNull()?.let { w ->
                match.groupValues[2].toIntOrNull()?.let { h -> w to h }
            }
        }

    fun withModel(model: PicoModel, overridden: Boolean): DeviceInfo = copy(
        model = model,
        modelOverridden = overridden,
    )
}
