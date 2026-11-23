package com.picopilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.picopilot.data.DeviceInfo
import com.picopilot.ui.components.ActionTile
import com.picopilot.ui.components.BatteryRing
import com.picopilot.ui.components.CircleIconButton
import com.picopilot.ui.components.ConfirmDialog
import com.picopilot.ui.components.DividerLine
import com.picopilot.ui.components.GhostButton
import com.picopilot.ui.components.GlassCard
import com.picopilot.ui.components.Pill
import com.picopilot.ui.components.SectionHeader
import com.picopilot.ui.components.SlimProgress
import com.picopilot.ui.components.StatRow
import com.picopilot.ui.components.VolumeControlCard
import com.picopilot.ui.formatGb
import com.picopilot.ui.formatTemp
import com.picopilot.ui.theme.Accent
import com.picopilot.ui.theme.Accent2
import com.picopilot.ui.theme.Bad
import com.picopilot.ui.theme.Bg
import com.picopilot.ui.theme.Card
import com.picopilot.ui.theme.Good
import com.picopilot.ui.theme.TextPrimary
import com.picopilot.ui.theme.TextSecondary
import com.picopilot.ui.theme.Warn
import com.picopilot.vm.ConnState
import com.picopilot.vm.PicoViewModel

@Composable
fun DashboardScreen(
    vm: PicoViewModel,
    onOpenConnect: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenTerminal: () -> Unit,
) {
    val device by vm.info.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()
    val latency by vm.latency.collectAsStateWithLifecycle()

    val current = device ?: return

    var confirmReboot by remember { mutableStateOf(false) }
    var confirmPower by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item { HeroCard(current, onOpenInfo) }

        item { SectionHeader("Быстрые действия") }

        item {
            QuickActions(
                vm = vm,
                onOpenTerminal = onOpenTerminal,
                onReboot = { confirmReboot = true },
                onPowerOff = { confirmPower = true },
            )
        }

        item {
            VolumeControlCard(
                volume = volume,
                onSet = vm::setVolume,
                onMute = vm::toggleMute,
                onNudge = vm::nudgeVolume,
            )
        }

        item { StatusCard(current, onRefresh = vm::refreshInfo) }

        item {
            ConnectionCard(
                conn = conn,
                latency = latency,
                fallbackHost = current.ip,
                onRefresh = vm::refreshInfo,
                onOpenConnect = onOpenConnect,
                onDisconnect = vm::disconnect,
            )
        }

        item {
            Text(
                "PicoPilot ${vm.appVersion} · неофициальный клиент",
                color = TextSecondary.copy(alpha = 0.55f),
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }

    if (confirmReboot) {
        ConfirmDialog(
            title = "Перезагрузить шлем?",
            text = "Шлем перезагрузится, текущие приложения закроются. Подключение придётся восстановить.",
            confirmText = "Перезагрузить",
            onConfirm = { vm.reboot() },
            onDismiss = { confirmReboot = false },
        )
    }
    if (confirmPower) {
        ConfirmDialog(
            title = "Выключить шлем?",
            text = "Шлем полностью выключится. Включить обратно можно только кнопкой питания на самом шлеме.",
            confirmText = "Выключить",
            destructive = true,
            onConfirm = { vm.powerOff() },
            onDismiss = { confirmPower = false },
        )
    }
}

@Composable
private fun HeroCard(device: DeviceInfo, onOpenInfo: () -> Unit) {
    val accent = Color(device.model.accent)
    GlassCard(
        gradient = listOf(accent.copy(alpha = 0.30f), Card.copy(alpha = 0.96f), Bg),
        borderColor = accent.copy(alpha = 0.24f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(
                        text = if (device.modelOverridden) "ВРУЧНУЮ" else "ОПРЕДЕЛЕНО",
                        color = accent,
                        icon = Icons.Rounded.AutoAwesome,
                    )
                    if (device.charging) {
                        Pill("ЗАРЯДКА", Good, Icons.Rounded.Bolt)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    device.model.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    device.model.tagline,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Android ${device.androidVersion}", Accent2)
                    device.picoOs?.let { Pill("PICO OS $it", Color(0xFFB388FF)) }
                }
            }
            Spacer(Modifier.width(8.dp))
            BatteryRing(device.batteryLevel, device.charging)
        }

        Spacer(Modifier.height(15.dp))
        DividerLine()
        Spacer(Modifier.height(11.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat("Процессор", device.model.chipset, Modifier.weight(1f))
            MiniStat("Дисплей", device.resolution.ifBlank { device.model.resolution }, Modifier.weight(1f))
        }

        if (!device.model.isPico || device.autoModel.id == "pico_unknown" || device.modelOverridden) {
            Spacer(Modifier.height(12.dp))
            GhostButton(
                text = if (device.modelOverridden) "Модель выбрана вручную — изменить" else "Уточнить модель",
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.AutoAwesome,
                tint = accent,
                onClick = onOpenInfo,
            )
        }
    }
}

@Composable
private fun MiniStat(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title.uppercase(),
            color = TextSecondary,
            fontSize = 9.sp,
            letterSpacing = 0.9.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun QuickActions(
    vm: PicoViewModel,
    onOpenTerminal: () -> Unit,
    onReboot: () -> Unit,
    onPowerOff: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile(
                Icons.Rounded.PhotoCamera,
                "Скриншот",
                Warn,
                Modifier.weight(1f),
                vm::takeScreenshot,
            )
            ActionTile(
                Icons.Rounded.Terminal,
                "Терминал",
                TextSecondary,
                Modifier.weight(1f),
                onOpenTerminal,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile(
                Icons.Rounded.RestartAlt,
                "Перезагрузка",
                Warn,
                Modifier.weight(1f),
                onReboot,
            )
            ActionTile(
                Icons.Rounded.PowerSettingsNew,
                "Выключить",
                Bad,
                Modifier.weight(1f),
                onPowerOff,
            )
        }
    }
}

@Composable
private fun StatusCard(device: DeviceInfo, onRefresh: () -> Unit) {
    val batteryTint = when {
        device.batteryLevel <= 20 -> Bad
        device.batteryLevel <= 45 -> Warn
        else -> Good
    }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Состояние",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(Icons.Rounded.Refresh, TextSecondary, size = 32.dp, onClick = onRefresh)
        }
        Spacer(Modifier.height(6.dp))
        StatRow(
            Icons.Rounded.BatteryFull,
            "Батарея",
            if (device.batteryLevel < 0) "—" else "${device.batteryLevel}% · ${device.batteryStatus}",
            batteryTint,
        )
        SlimProgress(device.batteryLevel.coerceAtLeast(0) / 100f, batteryTint)
        Spacer(Modifier.height(10.dp))
        StatRow(
            Icons.Rounded.Storage,
            "Накопитель",
            "${formatGb(device.storageFreeKb)} свободно",
            Accent2,
        )
        SlimProgress(device.storageUsedFraction, Accent2)
        Spacer(Modifier.height(10.dp))
        StatRow(
            Icons.Rounded.Thermostat,
            "Температура",
            formatTemp(device.cpuTempC ?: device.batteryTempC),
            Warn,
        )
        StatRow(Icons.Rounded.Schedule, "В работе", device.uptimeText, Accent2)
        StatRow(Icons.Rounded.Wifi, "IP-адрес", device.ip ?: "—", Good)
        StatRow(Icons.Rounded.Speed, "Разрешение", device.resolution, Accent)
    }
}

@Composable
private fun ConnectionCard(
    conn: ConnState,
    latency: Int?,
    fallbackHost: String?,
    onRefresh: () -> Unit,
    onOpenConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val hostText = (conn as? ConnState.Connected)?.let { "${it.host}:${it.port}" }
        ?: fallbackHost
        ?: "—"

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Accent2.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Link,
                    contentDescription = null,
                    tint = Accent2,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Соединение", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(hostText, color = TextSecondary, fontSize = 11.sp)
            }
            if (latency != null) {
                val tint = when {
                    latency < 90 -> Good
                    latency < 220 -> Warn
                    else -> Bad
                }
                Pill("$latency мс", tint)
            }
        }

        if (conn is ConnState.Error) {
            Spacer(Modifier.height(9.dp))
            Text(conn.message, color = Bad, fontSize = 11.sp, lineHeight = 15.sp)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                text = "Проверить",
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Refresh,
                onClick = onRefresh,
            )
            GhostButton(
                text = "Сменить шлем",
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Wifi,
                onClick = onOpenConnect,
            )
        }
        Spacer(Modifier.height(8.dp))
        GhostButton(
            text = "Отключиться",
            modifier = Modifier.fillMaxWidth(),
            tint = Bad,
            onClick = onDisconnect,
        )
    }
}
