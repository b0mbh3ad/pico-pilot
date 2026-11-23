package com.picopilot.data

data class PicoModel(
    val id: String,
    val name: String,
    val year: Int,
    val chipset: String,
    val resolution: String,
    val tagline: String,
    val accent: Long,
    val isPico: Boolean = true,
) {
    val generation: String
        get() = when {
            year > 0 -> "$year"
            else -> "—"
        }
}

object PicoModels {

    val ultra = PicoModel(
        id = "pico4ultra",
        name = "Pico 4 Ultra",
        year = 2024,
        chipset = "Snapdragon XR2 Gen 2",
        resolution = "2160 × 2160 / глаз",
        tagline = "Флагман с цветным passthrough",
        accent = 0xFF9B7BFF,
    )

    val fourPro = PicoModel(
        id = "pico4pro",
        name = "Pico 4 Pro",
        year = 2022,
        chipset = "Snapdragon XR2",
        resolution = "2160 × 2160 / глаз",
        tagline = "Трекинг лица и глаз",
        accent = 0xFF4FD1FF,
    )

    val four = PicoModel(
        id = "pico4",
        name = "Pico 4",
        year = 2022,
        chipset = "Snapdragon XR2",
        resolution = "2160 × 2160 / глаз",
        tagline = "Народный VR-шлем",
        accent = 0xFF5B8CFF,
    )

    val neo3 = PicoModel(
        id = "pico_neo3",
        name = "Pico Neo 3",
        year = 2021,
        chipset = "Snapdragon XR2",
        resolution = "1832 × 1920 / глаз",
        tagline = "Рабочая лошадка VR-клубов",
        accent = 0xFF3DDC97,
    )

    val neo2 = PicoModel(
        id = "pico_neo2",
        name = "Pico Neo 2",
        year = 2020,
        chipset = "Snapdragon 845",
        resolution = "1920 × 2160 / глаз",
        tagline = "Ветеран с 4K-дисплеем",
        accent = 0xFFFFB84D,
    )

    val g3 = PicoModel(
        id = "picog3",
        name = "Pico G3",
        year = 2023,
        chipset = "Snapdragon XR2",
        resolution = "1832 × 1920 / глаз",
        tagline = "Шлем для обучения и бизнеса",
        accent = 0xFFFF6FAE,
    )

    val g2 = PicoModel(
        id = "picog2",
        name = "Pico G2 4K",
        year = 2019,
        chipset = "Snapdragon 835",
        resolution = "1920 × 2160 / глаз",
        tagline = "Компактный 4K-шлем",
        accent = 0xFF2ED3C6,
    )

    val five = PicoModel(
        id = "pico5",
        name = "Pico 5",
        year = 2025,
        chipset = "Snapdragon XR2+ Gen 2",
        resolution = "—",
        tagline = "Новое поколение Pico",
        accent = 0xFFB388FF,
    )

    val unknownPico = PicoModel(
        id = "pico_unknown",
        name = "Pico VR",
        year = 0,
        chipset = "—",
        resolution = "—",
        tagline = "Модель не распознана — уточните вручную",
        accent = 0xFF8A8FA3,
    )

    val other = PicoModel(
        id = "other",
        name = "Android-устройство",
        year = 0,
        chipset = "—",
        resolution = "—",
        tagline = "Подключено не Pico, но управление работает",
        accent = 0xFF8A8FA3,
        isPico = false,
    )

    val catalog: List<PicoModel> = listOf(ultra, fourPro, four, neo3, neo2, g3, g2, five)

    fun byId(id: String?): PicoModel? = catalog.firstOrNull { it.id == id }

    fun detect(props: Map<String, String>): PicoModel {
        val model = props["ro.product.model"].orEmpty()
        val device = props["ro.product.device"].orEmpty()
        val name = props["ro.product.name"].orEmpty()
        val brand = props["ro.product.brand"].orEmpty()
        val manufacturer = props["ro.product.manufacturer"].orEmpty()
        val product = props["ro.build.product"].orEmpty()
        val fingerprint = props["ro.build.fingerprint"].orEmpty()
        val picoProps = props.entries
            .filter { it.key.contains("pico", ignoreCase = true) }
            .joinToString(" ") { "${it.key}=${it.value}" }

        val haystack = listOf(model, device, name, brand, manufacturer, product, fingerprint, picoProps)
            .joinToString(" ")
            .lowercase()

        fun has(vararg needles: String) = needles.any { haystack.contains(it) }

        val looksPico = has("pico", "bytedance", "a8110", "a8s10", "a9210", "a7h10", "a6s10", "a7h20", "a5s10")

        if (!looksPico) return other

        return when {
            has("ultra", "a9210", "pico4ultra", "pico 4 ultra") -> ultra
            has("4 pro", "4pro", "a8s10") -> fourPro
            has("pico 4", "pico4", "a8110") -> four
            has("neo 3", "neo3", "a7h10") -> neo3
            has("neo 2", "neo2", "a6s10") -> neo2
            has("g3", "a7h20") -> g3
            has("g2", "a5s10") -> g2
            has("pico 5", "pico5") -> five
            else -> unknownPico
        }
    }
}
