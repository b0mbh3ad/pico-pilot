package com.picopilot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picopilot.ui.theme.Accent
import com.picopilot.ui.theme.Accent2
import com.picopilot.ui.theme.Bg
import com.picopilot.ui.theme.Bad
import com.picopilot.ui.theme.Card
import com.picopilot.ui.theme.CardHi
import com.picopilot.ui.theme.Good
import com.picopilot.ui.theme.Stroke
import com.picopilot.ui.theme.TextPrimary
import com.picopilot.ui.theme.TextSecondary
import com.picopilot.ui.theme.Warn
import com.picopilot.vm.ConnState

@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A0B15), Bg, Color(0xFF05060A)))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Accent.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.02f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.88f, size.height * 0.02f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Accent2.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.02f, size.height * 0.42f),
                    radius = size.width * 0.85f,
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.02f, size.height * 0.42f),
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    gradient: List<Color>? = null,
    borderColor: Color = Color.White.copy(alpha = 0.07f),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (gradient != null) Brush.linearGradient(gradient) else SolidColor(Card),
            )
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun Pill(
    text: String,
    color: Color = Accent,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.26f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        }
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(CardHi.copy(alpha = 0.92f), Card)))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
fun GlowButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(Accent, Color(0xFF6D5BFF)))
                } else {
                    SolidColor(CardHi)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = if (enabled) Color.White else TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = TextPrimary,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(CardHi.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    tint: Color = TextSecondary,
    size: Dp = 40.dp,
    background: Color = CardHi.copy(alpha = 0.7f),
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun KeyButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    size: Dp = 52.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(CardHi, Card)))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
fun StatRow(
    icon: ImageVector,
    title: String,
    value: String,
    tint: Color = Accent2,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(31.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SlimProgress(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.07f), cornerRadius = radius)
        val width = size.width * progress.coerceIn(0f, 1f)
        if (width > 0f) {
            drawRoundRect(color, size = Size(width, size.height), cornerRadius = radius)
        }
    }
}

@Composable
fun VrHeadsetIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    lensA: Color = Accent,
    lensB: Color = Accent2,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bodyLeft = w * 0.12f
        val bodyRight = w * 0.88f
        val bodyTop = h * 0.32f
        val bodyBottom = h * 0.76f
        val stroke = w * 0.035f

        drawLine(
            tint.copy(alpha = 0.55f),
            Offset(bodyLeft, h * 0.52f),
            Offset(w * 0.03f, h * 0.52f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            tint.copy(alpha = 0.55f),
            Offset(bodyRight, h * 0.52f),
            Offset(w * 0.97f, h * 0.52f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val strap = Path().apply {
            moveTo(bodyLeft + w * 0.07f, bodyTop)
            cubicTo(w * 0.34f, h * 0.04f, w * 0.66f, h * 0.04f, bodyRight - w * 0.07f, bodyTop)
        }
        drawPath(strap, tint.copy(alpha = 0.55f), style = Stroke(width = stroke, cap = StrokeCap.Round))

        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyRight - bodyLeft, bodyBottom - bodyTop),
            cornerRadius = CornerRadius((bodyBottom - bodyTop) * 0.34f),
        )
        drawCircle(lensA, radius = w * 0.072f, center = Offset(w * 0.36f, h * 0.54f))
        drawCircle(lensB, radius = w * 0.072f, center = Offset(w * 0.64f, h * 0.54f))
    }
}

@Composable
fun LogoMark(size: Dp = 38.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brush.linearGradient(listOf(Accent, Color(0xFF4F7CFF))))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        VrHeadsetIcon(
            modifier = Modifier.size(size * 0.72f),
            tint = Color.White,
            lensA = Color(0xFF241A54),
            lensB = Color(0xFF0B2A38),
        )
    }
}

@Composable
fun BatteryRing(level: Int, charging: Boolean, modifier: Modifier = Modifier) {
    val color = when {
        level <= 20 -> Bad
        level <= 45 -> Warn
        else -> Good
    }
    val animated by animateFloatAsState(
        targetValue = level.coerceIn(0, 100) / 100f,
        label = "battery",
    )
    Box(modifier.size(94.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (level < 0) "—" else "$level%",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (charging) "зарядка" else "заряд",
                color = TextSecondary,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
fun ConnectionChip(state: ConnState, onClick: () -> Unit) {
    val color = when (state) {
        is ConnState.Connected -> Good
        ConnState.Connecting, ConnState.AwaitingApproval -> Warn
        is ConnState.Error -> Bad
        ConnState.Disconnected -> TextSecondary
    }
    val text = when (state) {
        is ConnState.Connected -> "Онлайн"
        ConnState.Connecting -> "Подключение"
        ConnState.AwaitingApproval -> "Подтвердите"
        is ConnState.Error -> "Ошибка"
        ConnState.Disconnected -> "Офлайн"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppTopBar(
    connection: ConnState,
    onConnectionClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoMark()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "PicoPilot",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            )
            Text(
                "пульт для Pico VR",
                color = TextSecondary,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
            )
        }
        ConnectionChip(connection, onConnectionClick)
        Spacer(Modifier.width(8.dp))
        CircleIconButton(Icons.Rounded.Settings, TextSecondary, onClick = onSettingsClick)
    }
}

@Composable
fun BusyBar(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = text != null, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Accent.copy(alpha = 0.12f))
                .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = Accent2,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(text.orEmpty(), color = TextPrimary, fontSize = 12.sp)
        }
    }
}

@Composable
fun AppAvatar(packageName: String, size: Dp = 42.dp) {
    val palette = listOf(
        Color(0xFF8B5CF6),
        Color(0xFF22D3EE),
        Color(0xFF3DDC97),
        Color(0xFFFFB84D),
        Color(0xFFFF5C8A),
        Color(0xFF5B8CFF),
    )
    val color = palette[(packageName.hashCode() and 0x7fffffff) % palette.size]
    val letter = packageName.substringAfterLast('.').firstOrNull()?.uppercase() ?: "?"
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(CardHi.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
