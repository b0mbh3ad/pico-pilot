package com.picopilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.picopilot.ui.components.CircleIconButton
import com.picopilot.ui.components.DividerLine
import com.picopilot.ui.theme.Accent
import com.picopilot.ui.theme.Accent2
import com.picopilot.ui.theme.Bad
import com.picopilot.ui.theme.Card
import com.picopilot.ui.theme.CardHi
import com.picopilot.ui.theme.TextPrimary
import com.picopilot.ui.theme.TextSecondary
import com.picopilot.vm.PicoViewModel
import com.picopilot.vm.TerminalKind

private val quickCommands = listOf(
    "getprop ro.product.model",
    "dumpsys battery",
    "pm list packages -3",
    "wm size",
    "top -n 1",
    "logcat -d -t 80",
)

@Composable
fun TerminalScreen(vm: PicoViewModel, onBack: () -> Unit) {
    val lines by vm.terminal.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var command by remember { mutableStateOf("") }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    fun run(text: String) {
        if (text.isBlank()) return
        vm.runCommand(text)
        command = ""
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
                Text("Терминал", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("Shell на шлеме · команды через агента", color = TextSecondary, fontSize = 10.sp)
            }
            CircleIconButton(Icons.Rounded.DeleteSweep, TextSecondary, onClick = vm::clearTerminal)
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            quickCommands.forEach { quick ->
                Text(
                    quick,
                    color = Accent2,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Card)
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
                        .clickable { run(quick) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        DividerLine()

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (lines.isEmpty()) {
                item {
                    Text(
                        "Введи команду внизу или выбери подсказку сверху.\n\n" +
                            "Например: getprop ro.product.model — покажет модель шлема.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            items(lines) { line ->
                Text(
                    text = line.text,
                    color = when (line.kind) {
                        TerminalKind.Command -> Accent2
                        TerminalKind.Error -> Bad
                        TerminalKind.Output -> TextPrimary
                    },
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("shell-команда…", fontSize = 12.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { run(command) }),
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
            CircleIconButton(
                Icons.Rounded.Send,
                tint = Accent2,
                size = 46.dp,
                background = Accent.copy(alpha = 0.16f),
                onClick = { run(command) },
            )
        }
    }
}
