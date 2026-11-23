package com.picopilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.picopilot.ui.components.CircleIconButton
import com.picopilot.ui.components.DividerLine
import com.picopilot.ui.theme.Accent
import com.picopilot.ui.theme.Accent2
import com.picopilot.ui.theme.Bad
import com.picopilot.ui.theme.Card
import com.picopilot.ui.theme.Good
import com.picopilot.ui.theme.TextPrimary
import com.picopilot.ui.theme.TextSecondary
import com.picopilot.ui.theme.Warn
import com.picopilot.vm.PicoViewModel

private val logLevelRegex = Regex("""^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+([VDIWEFA])/""")

private data class LevelFilter(val label: String, val letter: Char?, val color: Color)

private val levelFilters = listOf(
    LevelFilter("Все", null, Accent2),
    LevelFilter("E", 'E', Bad),
    LevelFilter("W", 'W', Warn),
    LevelFilter("I", 'I', Accent2),
    LevelFilter("D", 'D', TextSecondary),
)

private fun levelOf(line: String): Char? =
    logLevelRegex.find(line)?.groupValues?.getOrNull(1)?.firstOrNull()

@Composable
fun LogsScreen(vm: PicoViewModel, onBack: () -> Unit) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val running by vm.logsRunning.collectAsStateWithLifecycle()

    var paused by rememberSaveable { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<String>>(emptyList()) }
    var levelIndex by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var autoScroll by rememberSaveable { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val source = if (paused) frozen else logs

    val filtered = remember(source, levelIndex, query) {
        val letter = levelFilters[levelIndex].letter
        source.filter { line ->
            val level = levelOf(line)
            val levelOk = when {
                letter == null -> true
                level == null -> true
                letter == 'E' -> level == 'E' || level == 'F'
                else -> level == letter
            }
            levelOk && (query.isBlank() || line.contains(query, ignoreCase = true))
        }
    }

    LaunchedEffect(filtered.size, autoScroll, paused) {
        if (autoScroll && !paused && filtered.isNotEmpty()) {
            listState.scrollToItem(filtered.lastIndex)
        }
    }

    DisposableEffect(Unit) {
        vm.startLogs()
        onDispose { vm.stopLogs() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Rounded.ArrowBack, TextPrimary, onClick = onBack)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Логи", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (running) "logcat в реальном времени" else "Поток остановлен",
                    color = if (running) Good else TextSecondary,
                    fontSize = 10.sp,
                )
            }
            CircleIconButton(
                icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                tint = if (paused) Good else Accent2,
                onClick = {
                    if (!paused) frozen = logs
                    paused = !paused
                },
            )
            Spacer(Modifier.width(8.dp))
            CircleIconButton(
                icon = Icons.Rounded.DeleteSweep,
                tint = TextSecondary,
                onClick = {
                    vm.clearLogs()
                    frozen = emptyList()
                },
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("Поиск по логам…", fontSize = 12.sp) },
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

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            levelFilters.forEachIndexed { index, filter ->
                val active = index == levelIndex
                val color = filter.color
                Text(
                    filter.label,
                    color = if (active) Color.White else color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) color.copy(alpha = 0.85f) else Card)
                        .border(
                            1.dp,
                            if (active) color.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.07f),
                            RoundedCornerShape(50),
                        )
                        .clickable { levelIndex = index }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Text(
                "Автоскролл",
                color = if (autoScroll) Color.White else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (autoScroll) Accent.copy(alpha = 0.75f) else Card)
                    .border(
                        1.dp,
                        if (autoScroll) Accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.07f),
                        RoundedCornerShape(50),
                    )
                    .clickable { autoScroll = !autoScroll }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        DividerLine()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (filtered.isEmpty()) {
                Text(
                    if (paused) "Пауза. Нажми ▶, чтобы продолжить." else "Ждём строки из logcat…",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filtered) { line ->
                        Text(
                            text = line,
                            color = colorFor(levelOf(line)),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        DividerLine()
        Text(
            "${filtered.size} из ${source.size} строк" + if (paused) " · пауза" else "",
            color = TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
        )
    }
}

private fun colorFor(level: Char?): Color = when (level) {
    'E', 'F' -> Bad
    'W' -> Warn
    'I' -> TextPrimary
    'D', 'V' -> TextSecondary
    else -> TextSecondary.copy(alpha = 0.8f)
}
