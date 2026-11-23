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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.picopilot.net.NearbyAgent
import com.picopilot.net.TransportKind
import com.picopilot.ui.components.CircleIconButton
import com.picopilot.ui.components.GhostButton
import com.picopilot.ui.components.GlassCard
import com.picopilot.ui.components.GlowButton
import com.picopilot.ui.components.LogoMark
import com.picopilot.ui.components.Pill
import com.picopilot.ui.theme.Accent
import com.picopilot.ui.theme.Accent2
import com.picopilot.ui.theme.Bad
import com.picopilot.ui.theme.Card
import com.picopilot.ui.theme.CardHi
import com.picopilot.ui.theme.Good
import com.picopilot.ui.theme.TextPrimary
import com.picopilot.ui.theme.TextSecondary
import com.picopilot.ui.theme.Warn
import com.picopilot.vm.ConnState
import com.picopilot.vm.PicoViewModel
import com.picopilot.vm.ScanState

@Composable
fun ConnectScreen(
    vm: PicoViewModel,
    onBack: (() -> Unit)?,
    onOpenInfo: () -> Unit,
) {
    val conn by vm.conn.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val savedHosts by vm.savedHosts.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val keyFingerprint by vm.keyFingerprint.collectAsStateWithLifecycle()
    val storedPin by vm.agentPin.collectAsStateWithLifecycle()
    val lastKind by vm.transport.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    val firstSaved = savedHosts.firstOrNull()
    var host by rememberSaveable {
        mutableStateOf(firstSaved?.substringBefore(':') ?: "")
    }
    var port by rememberSaveable {
        mutableStateOf(
            firstSaved?.substringAfter(':', "")?.takeIf { it.isNotBlank() }
                ?: if (lastKind == TransportKind.ADB) "5555" else "47801",
        )
    }
    var kindName by rememberSaveable { mutableStateOf(lastKind.name) }
    var pin by rememberSaveable { mutableStateOf(storedPin) }
    var pinError by rememberSaveable { mutableStateOf<String?>(null) }

    val kind = runCatching { TransportKind.valueOf(kindName) }.getOrDefault(TransportKind.AGENT)

    fun connectAgent(agent: NearbyAgent) {
        if (pin.length < 4) {
            pinError = "Введите PIN с экрана шлема"
            return
        }
        pinError = null
        vm.connect(agent.host, agent.port, TransportKind.AGENT, pin)
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    CircleIconButton(Icons.Rounded.ArrowBack, TextPrimary, onClick = onBack)
                    Spacer(Modifier.width(10.dp))
                } else {
                    LogoMark(42.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "PICOPILOT",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Найдём шлем",
                        color = TextPrimary,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                }
                if (info != null) {
                    Pill("ГОТОВО", Good, Icons.Rounded.CheckCircle)
                }
            }
        }

        item {
            Text(
                "На шлеме работает «PicoPilot Агент» — он сам объявляет себя в Wi-Fi. " +
                    "Телефон находит его и подключается по PIN. ADB и компьютер не нужны.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }

        if (conn !is ConnState.Disconnected) {
            item {
                ConnectionStatusCard(
                    conn = conn,
                    host = host,
                    port = port.toIntOrNull() ?: 47801,
                    onRetry = { vm.connect(host, port.toIntOrNull() ?: 47801, kind, pin) },
                )
            }
        }

        // ---------------------------------------------------------- поиск
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Шлемы рядом",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Поиск идёт по всей Wi-Fi сети",
                            color = TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    if (scan is ScanState.Scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Accent2,
                            strokeWidth = 2.dp,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                GlowButton(
                    text = if (scan is ScanState.Scanning) "Слушаю сеть…" else "Найти шлем",
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Radar,
                    enabled = scan !is ScanState.Scanning,
                    onClick = vm::scanNetwork,
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    "PIN С ЭКРАНА ШЛЕМА",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        pin = value.filter { it.isDigit() }.take(6)
                        pinError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("6 цифр", fontSize = 11.sp) },
                    placeholder = { Text("000000", fontSize = 15.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Key, null, tint = Accent2, modifier = Modifier.size(18.dp))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(15.dp),
                    colors = fieldColors(),
                )
                if (pinError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(pinError!!, color = Bad, fontSize = 11.sp)
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Вводится один раз — дальше телефон помнит PIN и подключается сам.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }

                Spacer(Modifier.height(14.dp))
                ScanResults(
                    scan = scan,
                    onConnectAgent = { agent ->
                        connectAgent(agent)
                    },
                    onConnectAdb = { address ->
                        vm.connect(address, 5555, TransportKind.ADB)
                    },
                )
            }
        }

        // ---------------------------------------------------------- вручную
        item {
            GlassCard {
                Text(
                    "Подключиться вручную",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(
                        text = "Агент",
                        selected = kind == TransportKind.AGENT,
                        modifier = Modifier.weight(1f),
                    ) {
                        kindName = TransportKind.AGENT.name
                        if (port == "5555") port = "47801"
                    }
                    ModeChip(
                        text = "ADB",
                        selected = kind == TransportKind.ADB,
                        modifier = Modifier.weight(1f),
                    ) {
                        kindName = TransportKind.ADB.name
                        if (port == "47801") port = "5555"
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it.trim() },
                        modifier = Modifier.weight(2.2f),
                        singleLine = true,
                        label = { Text("IP-адрес", fontSize = 11.sp) },
                        placeholder = { Text("192.168.1.42", fontSize = 14.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        shape = RoundedCornerShape(15.dp),
                        colors = fieldColors(),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { value -> port = value.filter { it.isDigit() }.take(5) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Порт", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(15.dp),
                        colors = fieldColors(),
                    )
                }

                Spacer(Modifier.height(12.dp))
                GlowButton(
                    text = when (conn) {
                        ConnState.Connecting -> "Подключаюсь…"
                        ConnState.AwaitingApproval -> "Жду подтверждения…"
                        else -> "Подключиться"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = if (conn is ConnState.Connecting) null else Icons.Rounded.Wifi,
                    enabled = conn !is ConnState.Connecting && host.isNotBlank(),
                    onClick = { vm.connect(host, port.toIntOrNull() ?: 47801, kind, pin) },
                )

                if (info != null && onBack != null) {
                    Spacer(Modifier.height(9.dp))
                    GlowButton(
                        text = "Продолжить к управлению",
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.CheckCircle,
                        onClick = onBack,
                    )
                }

                if (savedHosts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Недавние", color = TextSecondary, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        savedHosts.forEach { entry ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(CardHi)
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                                    .clickable {
                                        host = entry.substringBefore(':')
                                        port = entry.substringAfter(':', "47801")
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text(entry, color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------- помощь
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Если агент ещё не стоит",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Pill("3 ШАГА", Accent)
                }
                Spacer(Modifier.height(12.dp))
                StepRow(1, "Установите на шлем файл PicoPilot-Agent.apk — он лежит рядом с приложением.")
                StepRow(2, "Откройте на шлеме «PicoPilot Агент» и оставьте его включённым. Root приветствуется: с ним доступны все команды.")
                StepRow(3, "Вернитесь сюда, нажмите «Найти шлем», выберите шлем и введите PIN.")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Good.copy(alpha = 0.09f))
                        .border(1.dp, Good.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    Icon(Icons.Rounded.Bolt, null, tint = Good, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Агент сам возвращается после сна шлема, поэтому ADB больше не «умирает» " +
                            "до перезагрузки. Адрес шлема тоже не нужно вводить вручную.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }

        // ---------------------------------------------------- ADB (fallback)
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Старый режим ADB",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Оставлен на случай, если агент недоступен",
                            color = TextSecondary,
                            fontSize = 10.sp,
                        )
                    }
                    Pill("5555", TextSecondary)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Для ADB на шлеме должна быть включена беспроводная отладка, а на телефоне — тот же ключ. " +
                        "Ключ хранится только на телефоне.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        keyFingerprint,
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenInfo) {
                        Text("Инфо", color = Accent2, fontSize = 12.sp)
                    }
                }
                CommandPill("adb tcpip 5555") {
                    clipboard.setText(AnnotatedString("adb tcpip 5555"))
                }
            }
        }
    }
}

@Composable
private fun ScanResults(
    scan: ScanState,
    onConnectAgent: (NearbyAgent) -> Unit,
    onConnectAdb: (String) -> Unit,
) {
    when (scan) {
        ScanState.Idle -> Text(
            "Нажмите «Найти шлем» — список появится здесь.",
            color = TextSecondary,
            fontSize = 11.sp,
        )

        ScanState.Scanning -> Text(
            "Сканирую 254 адреса…",
            color = TextSecondary,
            fontSize = 11.sp,
        )

        is ScanState.Failed -> Text(scan.message, color = Bad, fontSize = 11.sp)

        is ScanState.Done -> {
            if (scan.agents.isEmpty() && scan.adbHosts.isEmpty()) {
                Text(
                    "Ничего не нашлось. Проверьте, что на шлеме открыт «PicoPilot Агент» " +
                        "и телефон подключён к той же Wi-Fi сети.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                return
            }

            scan.agents.forEachIndexed { index, agent ->
                if (index > 0) Spacer(Modifier.height(6.dp))
                DeviceRow(
                    title = agent.title,
                    subtitle = buildString {
                        append(agent.host)
                        append(" · агент")
                        if (agent.root) append(" · root")
                    },
                    tint = Good,
                    badge = "АГЕНТ",
                    onClick = { onConnectAgent(agent) },
                )
            }

            if (scan.adbHosts.isNotEmpty()) {
                if (scan.agents.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("ADB", color = TextSecondary, fontSize = 10.sp, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(4.dp))
                }
                scan.adbHosts.forEach { address ->
                    DeviceRow(
                        title = address,
                        subtitle = "старый режим · порт 5555",
                        tint = Warn,
                        badge = "ADB",
                        onClick = { onConnectAdb(address) },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    title: String,
    subtitle: String,
    tint: Color,
    badge: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Wifi, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            badge,
            color = tint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    conn: ConnState,
    host: String,
    port: Int,
    onRetry: () -> Unit,
) {
    GlassCard(contentPadding = PaddingValues(16.dp)) {
        when (conn) {
            ConnState.Connecting -> StatusLine(
                icon = null,
                title = "Стучусь в шлем…",
                subtitle = "Проверяю $host:$port",
                tint = Warn,
                showSpinner = true,
            )

            ConnState.AwaitingApproval -> StatusLine(
                icon = Icons.Rounded.Lock,
                title = "Подтвердите отладку",
                subtitle = "Наденьте шлем: появится окно «Разрешить отладку». Нажмите «Разрешить».",
                tint = Warn,
            )

            is ConnState.Error -> {
                StatusLine(
                    icon = Icons.Rounded.Warning,
                    title = "Не получилось подключиться",
                    subtitle = conn.message,
                    tint = Bad,
                )
                Spacer(Modifier.height(8.dp))
                GhostButton(
                    text = "Попробовать снова",
                    modifier = Modifier.fillMaxWidth(),
                    tint = Accent2,
                    onClick = onRetry,
                )
            }

            is ConnState.Connected -> StatusLine(
                icon = Icons.Rounded.CheckCircle,
                title = "Подключено",
                subtitle = "${conn.host}:${conn.port}",
                tint = Good,
            )

            ConnState.Disconnected -> Unit
        }
    }
}

@Composable
private fun StatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    subtitle: String,
    tint: Color,
    showSpinner: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = tint,
                    strokeWidth = 2.dp,
                )
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Accent.copy(alpha = 0.18f) else Card)
            .border(
                1.dp,
                if (selected) Accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Accent2 else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = Accent2, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CommandPill(command: String, onCopy: () -> Unit) {
    Row(
        Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A0C14))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .clickable(onClick = onCopy)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(command, color = Accent2, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = "Скопировать",
            tint = TextSecondary,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
    focusedContainerColor = Card,
    unfocusedContainerColor = Card,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = Accent2,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Accent2,
)
