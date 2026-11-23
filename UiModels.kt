package com.picopilot.vm

import com.picopilot.net.NearbyAgent

/** Что показывать на скриншоте: кадр целиком или только один глаз. */
enum class EyeMode { BOTH, LEFT, RIGHT }

sealed interface ConnState {
    data object Disconnected : ConnState
    data object Connecting : ConnState
    data object AwaitingApproval : ConnState
    data class Connected(val host: String, val port: Int) : ConnState
    data class Error(val message: String) : ConnState
}

data class VolumeUi(
    val level: Int = 8,
    val max: Int = 15,
    val muted: Boolean = false,
) {
    val fraction: Float get() = if (max <= 0) 0f else (level.toFloat() / max).coerceIn(0f, 1f)
}

sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    data class Done(
        val agents: List<NearbyAgent> = emptyList(),
        val adbHosts: List<String> = emptyList(),
    ) : ScanState
    data class Failed(val message: String) : ScanState
}

enum class TerminalKind { Command, Output, Error }

data class TerminalLine(val text: String, val kind: TerminalKind)
