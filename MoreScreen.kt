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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.picopilot.ui.components.ConfirmDialog
import com.picopilot.ui.components.DividerLine
import com.picopilot.ui.components.GhostButton
import com.picopilot.ui.components.GlassCard
import com.picopilot.ui.components.LabelValueRow
import com.picopilot.ui.components.LogoMark
import com.picopilot.ui.components.SectionHeader
import com.picopilot.ui.theme.Accent
import com.picopilot.ui.theme.Accent2
import com.picopilot.ui.theme.Bad
import com.picopilot.ui.theme.Card
import com.picopilot.ui.theme.CardHi
import com.picopilot.ui.theme.Good
import com.picopilot.ui.theme.TextPrimary
import com.picopilot.ui.theme.TextSecondary
import com.picopilot.ui.theme.Warn
import com.picopilot.vm.PicoViewModel

@Composable
fun MoreScreen(
    vm: PicoViewModel,
    onOpenTerminal: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    val info by vm.info.collectAsStateWithLifecycle()
    val keyFingerprint by vm.keyFingerprint.collectAsStateWithLifecycle()

    var showTextDialog by remember { mutableStateOf(false) }
    var confirmRecovery by remember { mutableStateOf(false) }
    var confirmResetKey by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionHeader("Инструменты") }

        item {
            GlassCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                ToolRow(
                    icon = Icons.Rounded.Terminal,
                    title = "Терминал",
                    subtitle = "Любая shell-команда на шлеме",
                    tint = Accent2,
                    onClick = onOpenTerminal,
                )
                DividerLine()
                ToolRow(
                    icon = Icons.Rounded.BugReport,
                    title = "Логи",
                    subtitle = "Live logcat в реальном времени",
                    tint = Warn,
                    onClick = onOpenLogs,
                )
                DividerLine()
                ToolRow(
                    icon = Icons.Rounded.Info,
                    title = "Модель и параметры",
                    subtitle = info?.model?.name?.let { "Сейчас: $it" } ?: "Все параметры шлема",
                    tint = Accent,
                    onClick = onOpenInfo,
                )
                DividerLine()
                ToolRow(
                    icon = Icons.Rounded.TextFields,
                    title = "Текст на шлеме",
                    subtitle = "Напечатает в активном поле",
                    tint = Accent2,
                    onClick = { showTextDialog = true },
                )
                DividerLine()
                ToolRow(
                    icon = Icons.Rounded.RestartAlt,
                    title = "Recovery",
                    subtitle = "Перезагрузка в режим восстановления",
                    tint = Bad,
                    onClick = { confirmRecovery = true },
                )
                DividerLine()
                ToolRow(
                    icon = Icons.Rounded.Link,
                    title = "Подключение",
                    subtitle = "Сменить IP или найти шлем в сети",
                    tint = Good,
                    onClick = onOpenConnect,
                )
            }
        }

        item { SectionHeader("О приложении") }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoMark(44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PicoPilot", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Версия ${vm.appVersion} · неофициальный клиент",
                            color = TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        null,
                        tint = Accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "PicoPilot общается со шлемом напрямую: агент на шлеме и телефон в одной Wi-Fi сети. " +
                        "Никаких серверов и аккаунтов — всё остаётся в вашей локальной сети.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(12.dp))
                DividerLine()
                Spacer(Modifier.height(8.dp))
                LabelValueRow("Модель", info?.model?.name ?: "—")
                LabelValueRow("ADB-ключ", keyFingerprint, mono = true)
                Spacer(Modifier.height(6.dp))
                GhostButton(
                    text = "Сбросить ADB-ключ",
                    modifier = Modifier.fillMaxWidth(),
                    tint = Bad,
                    onClick = { confirmResetKey = true },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Сброс потребуется, если шлем перестал принимать телефон. После сброса подтвердите отладку заново.",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }

    if (showTextDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            containerColor = CardHi,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Текст на шлеме", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "Текст отправится в активное поле ввода внутри шлема.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("hello world", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        shape = RoundedCornerShape(14.dp),
                        colors = dialogFieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.sendText(text)
                    showTextDialog = false
                }) {
                    Text("Отправить", color = Accent2, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("Отмена", color = TextSecondary)
                }
            },
        )
    }

    if (confirmRecovery) {
        ConfirmDialog(
            title = "Загрузиться в recovery?",
            text = "Шлем перезагрузится в режим восстановления. Обычные приложения работать не будут — понадобится кнопка питания.",
            confirmText = "В recovery",
            destructive = true,
            onConfirm = { vm.reboot("recovery") },
            onDismiss = { confirmRecovery = false },
        )
    }

    if (confirmResetKey) {
        ConfirmDialog(
            title = "Сбросить ADB-ключ?",
            text = "Текущий ключ будет удалён. При следующем подключении шлем снова спросит разрешение на отладку.",
            confirmText = "Сбросить",
            destructive = true,
            onConfirm = { vm.resetAdbKey() },
            onDismiss = { confirmResetKey = false },
        )
    }
}

@Composable
private fun ToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
    focusedContainerColor = Card,
    unfocusedContainerColor = Card,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Accent2,
)
