package com.picopilot.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.picopilot.ui.components.AppBackground
import com.picopilot.ui.components.AppTopBar
import com.picopilot.ui.components.BusyBar
import com.picopilot.ui.screens.AppsScreen
import com.picopilot.ui.screens.ConnectScreen
import com.picopilot.ui.screens.DashboardScreen
import com.picopilot.ui.screens.InfoScreen
import com.picopilot.ui.screens.LogsScreen
import com.picopilot.ui.screens.MoreScreen
import com.picopilot.ui.screens.RemoteScreen
import com.picopilot.ui.screens.ScreenScreen
import com.picopilot.ui.screens.TerminalScreen
import com.picopilot.ui.theme.Accent
import com.picopilot.ui.theme.Accent2
import com.picopilot.ui.theme.Bad
import com.picopilot.ui.theme.Card
import com.picopilot.ui.theme.TextPrimary
import com.picopilot.ui.theme.TextSecondary
import com.picopilot.vm.ConnState
import com.picopilot.vm.PicoViewModel

enum class Tab(val title: String, val icon: ImageVector) {
    Dashboard("Шлем", Icons.Rounded.Home),
    Remote("Пульт", Icons.Rounded.Tune),
    Screen("Экран", Icons.Rounded.Screenshot),
    Apps("Игры", Icons.Rounded.Apps),
    More("Ещё", Icons.Rounded.AutoAwesome),
}

enum class Overlay { None, Terminal, Logs, Info }

@Composable
fun AppRoot(vm: PicoViewModel = viewModel()) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Dashboard) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    var forceConnect by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.consumeMessage()
    }
    LaunchedEffect(conn) {
        if (conn is ConnState.Connected) forceConnect = false
    }

    AppBackground {
        Box(Modifier.fillMaxSize()) {
            when {
                overlay == Overlay.Terminal -> TerminalScreen(vm, onBack = { overlay = Overlay.None })
                overlay == Overlay.Logs -> LogsScreen(vm, onBack = { overlay = Overlay.None })
                overlay == Overlay.Info -> InfoScreen(vm, onBack = { overlay = Overlay.None })

                forceConnect || (conn !is ConnState.Connected && info == null) -> ConnectScreen(
                    vm = vm,
                    onBack = if (info != null) ({ forceConnect = false }) else null,
                    onOpenInfo = { overlay = Overlay.Info },
                )

                else -> Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = { BottomNav(tab) { tab = it } },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                        AppTopBar(
                            connection = conn,
                            onConnectionClick = { forceConnect = true },
                            onSettingsClick = { forceConnect = true },
                        )
                        BusyBar(busy, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        if (conn is ConnState.Error) {
                            ErrorBanner(conn as ConnState.Error, onRetry = vm::reconnect)
                        }
                        Crossfade(
                            targetState = tab,
                            label = "tab",
                            modifier = Modifier.weight(1f),
                        ) { target ->
                            when (target) {
                                Tab.Dashboard -> DashboardScreen(
                                    vm = vm,
                                    onOpenConnect = { forceConnect = true },
                                    onOpenInfo = { overlay = Overlay.Info },
                                    onOpenTerminal = { overlay = Overlay.Terminal },
                                )

                                Tab.Remote -> RemoteScreen(vm)
                                Tab.Screen -> ScreenScreen(vm)
                                Tab.Apps -> AppsScreen(vm)
                                Tab.More -> MoreScreen(
                                    vm = vm,
                                    onOpenTerminal = { overlay = Overlay.Terminal },
                                    onOpenLogs = { overlay = Overlay.Logs },
                                    onOpenInfo = { overlay = Overlay.Info },
                                    onOpenConnect = { forceConnect = true },
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 94.dp),
            )
        }
    }
}

@Composable
private fun ErrorBanner(error: ConnState.Error, onRetry: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Bad.copy(alpha = 0.12f))
            .border(1.dp, Bad.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = Bad, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            error.message,
            color = TextPrimary,
            fontSize = 11.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text("Повторить", color = Accent2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Card.copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(26.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (active) Accent.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = if (active) Accent2 else TextSecondary,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.size(3.dp))
                    Text(
                        tab.title,
                        fontSize = 10.sp,
                        color = if (active) TextPrimary else TextSecondary,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
