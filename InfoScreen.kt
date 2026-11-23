package com.picopilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.picopilot.data.DeviceInfo
import com.picopilot.data.PicoModels
import com.picopilot.ui.components.CircleIconButton
import com.picopilot.ui.components.ConfirmDialog
import com.picopilot.ui.components.DividerLine
import com.picopilot.ui.components.GhostButton
import com.picopilot.ui.components.GlassCard
import com.picopilot.ui.components.LabelValueRow
import com.picopilot.ui.components.Pill
import com.picopilot.ui.components.SectionHeader
import com.picopilot.ui.formatGb
import com.picopilot.ui.formatTemp
import com.picopilot.ui.theme.Accent
import com.picopilot.ui.theme.Accent2
import com.picopilot.ui.theme.Bad
import com.picopilot.ui.theme.Card
import com.picopilot.ui.theme.CardHi
import com.picopilot.ui.theme.Good
import com.picopilot.ui.theme.TextPrimary
import com.picopilot.ui.theme.TextSecondary
import com.picopilot.vm.PicoViewModel

@Composable
fun InfoScreen(vm: PicoViewModel, onBack: () -> Unit) {
    val info by vm.info.collectAsStateWithLifecycle()
    val override by vm.modelOverride.collectAsStateWithLifecycle()
    val keyFingerprint by vm.keyFingerprint.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var showProps by rememberSaveable { mutableStateOf(false) }
    var propsQuery by rememberSaveable { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    val device = info

    val filteredProps = remember(info, propsQuery) {
        info?.props?.entries
            ?.filter {
                propsQuery.isBlank() ||
                    it.key.contains(propsQuery, ignoreCase = true) ||
                    it.value.contains(propsQuery, ignoreCase = true)
            }
            ?.sortedBy { it.key }
            .orEmpty()
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Rounded.ArrowBack, TextPrimary, onClick = onBack)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Модель и параметры", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        device?.model?.name ?: "Шлем не подключён",
                        color = TextSecondary,
                        fontSize = 10.sp,
                    )
                }
                if (device != null) {
                    Pill(
                        text = if (device.modelOverridden) "ВРУЧНУЮ" else "АВТО",
                        color = if (device.modelOverridden) Color(0xFFFFB84D) else Good,
                    )
                }
            }
        }

        if (device == null) {
            item {
                GlassCard {
                    Text(
                        "Подключитесь к шлему, чтобы увидеть параметры.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            return@LazyColumn
        }

        item { SectionHeader("Модель шлема") }

        item {
            GlassCard {
                Text(
                    "Автоопределение",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Определено: ${device.autoModel.name} · ${device.autoModel.chipset}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Если модель распознана неверно — выберите вручную:",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    ModelChip(
                        label = "Авто",
                        selected = override == null,
                        tint = Accent2,
                        onClick = { vm.setModelOverride(null) },
                    )
                    PicoModels.catalog.forEach { model ->
                        ModelChip(
                            label = model.name,
                            selected = override?.id == model.id,
                            tint = Color(model.accent),
                            onClick = { vm.setModelOverride(model) },
                        )
                    }
                }
            }
        }

        item { SectionHeader("Устройство") }

        item {
            GlassCard {
                InfoRow("Модель (getprop)", device.rawModel, mono = true) {
                    clipboard.setText(AnnotatedString(device.rawModel))
                    vm.notify("Скопировано")
                }
                InfoRow("Устройство", device.rawDevice, mono = true) {
                    clipboard.setText(AnnotatedString(device.rawDevice))
                    vm.notify("Скопировано")
                }
                InfoRow("Android", device.androidVersion)
                InfoRow("SDK", device.sdk.toString())
                InfoRow("PICO OS", device.picoOs ?: "—")
                InfoRow("Сборка", device.buildId, mono = true) {
                    clipboard.setText(AnnotatedString(device.buildId))
                    vm.notify("Скопировано")
                }
                InfoRow("Серийный номер", device.serial, mono = true) {
                    clipboard.setText(AnnotatedString(device.serial))
                    vm.notify("Скопировано")
                }
                InfoRow("IP-адрес", device.ip ?: "—", mono = true)
                InfoRow("Разрешение", device.resolution)
                InfoRow("Плотность", if (device.density > 0) "${device.density} dpi" else "—")
                InfoRow("Батарея", "${device.batteryLevel}% · ${device.batteryStatus}")
                InfoRow("Температура", formatTemp(device.cpuTempC ?: device.batteryTempC))
                InfoRow("Напряжение", device.voltageMv?.let { "$it мВ" } ?: "—")
                InfoRow(
                    "Накопитель",
                    "${formatGb(device.storageFreeKb)} свободно из ${formatGb(device.storageTotalKb)}",
                )
                InfoRow("В работе", device.uptimeText)
                InfoRow("Громкость", "${device.volumeLevel} / ${device.volumeMax}")
                InfoRow("Яркость", "${device.brightness} / 255")
                Spacer(Modifier.height(4.dp))
                DividerLine()
                Spacer(Modifier.height(6.dp))
                Text(
                    "Fingerprint",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    device.fingerprint,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        item { SectionHeader("Ключ ADB") }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Key, null, tint = Accent, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Отпечаток ключа", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            keyFingerprint,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CircleIconButton(
                        Icons.Rounded.ContentCopy,
                        TextSecondary,
                        size = 34.dp,
                        onClick = {
                            clipboard.setText(AnnotatedString(keyFingerprint))
                            vm.notify("Отпечаток скопирован")
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                GhostButton(
                    text = "Сбросить ключ",
                    modifier = Modifier.fillMaxWidth(),
                    tint = Bad,
                    onClick = { confirmReset = true },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "После сброса шлем заново спросит разрешение на отладку. Это лечит ситуации, " +
                        "когда шлем «забыл» телефон.",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }

        item {
            GlassCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showProps = !showProps }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Все свойства системы",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${device.props.size} записей getprop · тап — копировать",
                            color = TextSecondary,
                            fontSize = 10.sp,
                        )
                    }
                    Icon(
                        if (showProps) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (showProps) {
            item {
                OutlinedTextField(
                    value = propsQuery,
                    onValueChange = { propsQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Фильтр свойств…", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, null, tint = TextSecondary, modifier = Modifier.size(17.dp))
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Card,
                        unfocusedContainerColor = Card,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent2,
                    ),
                )
            }

            items(filteredProps, key = { it.key }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Card.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .clickable {
                            clipboard.setText(AnnotatedString("${entry.key}=${entry.value}"))
                            vm.notify("Скопировано: ${entry.key}")
                        }
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.key,
                            color = Accent2,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            entry.value.ifBlank { "—" },
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Сбросить ADB-ключ?",
            text = "Текущий ключ будет удалён. При следующем подключении шлем снова спросит разрешение на отладку.",
            confirmText = "Сбросить",
            destructive = true,
            onConfirm = {
                vm.resetAdbKey()
                onBack()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun ModelChip(
    label: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) tint.copy(alpha = 0.22f) else Card)
            .border(
                1.dp,
                if (selected) tint.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            color = if (selected) tint else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    mono: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LabelValueRow(
            label = label,
            value = value,
            modifier = Modifier.weight(1f),
            mono = mono,
        )
        if (onCopy != null) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = "Скопировать",
                tint = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onCopy),
            )
        }
    }
}
