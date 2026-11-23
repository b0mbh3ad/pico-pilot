package com.picopilot.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.picopilot.ui.components.AppAvatar
import com.picopilot.ui.components.CircleIconButton
import com.picopilot.ui.components.ConfirmDialog
import com.picopilot.ui.components.EmptyState
import com.picopilot.ui.theme.Accent
import com.picopilot.ui.theme.Accent2
import com.picopilot.ui.theme.Bad
import com.picopilot.ui.theme.Card
import com.picopilot.ui.theme.CardHi
import com.picopilot.ui.theme.Good
import com.picopilot.ui.theme.TextPrimary
import com.picopilot.ui.theme.TextSecondary
import com.picopilot.vm.PicoViewModel

private enum class AppAction { Uninstall, ClearData }

@Composable
fun AppsScreen(vm: PicoViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.appsLoading.collectAsStateWithLifecycle()
    val includeSystem by vm.includeSystemApps.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var query by rememberSaveable { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<Pair<AppAction, String>?>(null) }

    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::installApk)
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Поиск по пакету", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
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
            CircleIconButton(Icons.Rounded.Refresh, Accent2, onClick = { vm.loadApps(includeSystem) })
            CircleIconButton(
                Icons.Rounded.Add,
                Accent2,
                onClick = { installLauncher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) },
            )
        }

        Row(
            Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${filtered.size} из ${apps.size}",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text("Системные", color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = includeSystem,
                onCheckedChange = { vm.loadApps(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = CardHi,
                ),
            )
        }

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Accent2,
                trackColor = Color.Transparent,
            )
        }

        if (filtered.isEmpty() && !loading) {
            EmptyState(
                icon = Icons.Rounded.Apps,
                title = if (query.isBlank()) "Пока пусто" else "Ничего не нашлось",
                subtitle = if (query.isBlank()) {
                    "Обнови список или установи APK кнопкой «+» — он отправится прямо в шлем."
                } else {
                    "Попробуй другой запрос."
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(filtered, key = { it }) { packageName ->
                    AppRow(
                        packageName = packageName,
                        onLaunch = { vm.launchApp(packageName) },
                        onStop = { vm.forceStop(packageName) },
                        onCopy = {
                            clipboard.setText(AnnotatedString(packageName))
                            vm.notify("Пакет скопирован")
                        },
                        onClear = { pendingAction = AppAction.ClearData to packageName },
                        onUninstall = { pendingAction = AppAction.Uninstall to packageName },
                    )
                }
            }
        }
    }

    pendingAction?.let { (action, packageName) ->
        when (action) {
            AppAction.Uninstall -> ConfirmDialog(
                title = "Удалить приложение?",
                text = "${prettyName(packageName)} будет удалено со шлема вместе с данными.",
                confirmText = "Удалить",
                destructive = true,
                onConfirm = { vm.uninstallApp(packageName) },
                onDismiss = { pendingAction = null },
            )

            AppAction.ClearData -> ConfirmDialog(
                title = "Очистить данные?",
                text = "Данные и настройки ${prettyName(packageName)} будут удалены. Приложение останется установленным.",
                confirmText = "Очистить",
                destructive = true,
                onConfirm = { vm.clearAppData(packageName) },
                onDismiss = { pendingAction = null },
            )
        }
    }
}

@Composable
private fun AppRow(
    packageName: String,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onUninstall: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Card.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
            .padding(start = 12.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppAvatar(packageName)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                prettyName(packageName),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                packageName,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onLaunch) {
            Icon(Icons.Rounded.PlayArrow, "Запустить", tint = Good, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onStop) {
            Icon(Icons.Rounded.Stop, "Остановить", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, "Ещё", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Запустить", fontSize = 13.sp) },
                    onClick = { menuOpen = false; onLaunch() },
                )
                DropdownMenuItem(
                    text = { Text("Остановить", fontSize = 13.sp) },
                    onClick = { menuOpen = false; onStop() },
                )
                DropdownMenuItem(
                    text = { Text("Скопировать пакет", fontSize = 13.sp) },
                    onClick = { menuOpen = false; onCopy() },
                )
                DropdownMenuItem(
                    text = { Text("Очистить данные", fontSize = 13.sp) },
                    onClick = { menuOpen = false; onClear() },
                )
                DropdownMenuItem(
                    text = { Text("Удалить", fontSize = 13.sp, color = Bad) },
                    onClick = { menuOpen = false; onUninstall() },
                )
            }
        }
    }
}

private fun prettyName(packageName: String): String =
    packageName.substringAfterLast('.')
        .replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
