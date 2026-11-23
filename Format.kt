package com.picopilot.ui

import java.util.Locale

fun formatGb(kb: Long): String {
    if (kb <= 0) return "—"
    val gb = kb / 1024.0 / 1024.0
    return String.format(Locale.US, "%.1f ГБ", gb)
}

fun formatTemp(celsius: Float?): String {
    if (celsius == null || celsius <= 0f) return "—"
    return String.format(Locale.US, "%.1f °C", celsius)
}

fun formatMb(bytes: Long): String {
    if (bytes <= 0) return "—"
    return String.format(Locale.US, "%.1f МБ", bytes / 1024.0 / 1024.0)
}
