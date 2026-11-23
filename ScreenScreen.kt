package com.picopilot.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.picopilot.ui.components.GhostButton
import com.picopilot.ui.components.GlassCard
import com.picopilot.ui.components.GlowButton
import com.picopilot.ui.components.Pill
import com.picopilot.ui.components.SectionHeader
import com.picopilot.ui.theme.Accent
import com.picopilot.ui.theme.Accent2
import com.picopilot.ui.theme.Bad
import com.picopilot.ui.theme.Good
import com.picopilot.ui.theme.TextPrimary
import com.picopilot.ui.theme.TextSecondary
import com.picopilot.ui.theme.Warn
import com.picopilot.util.MediaSaver
import com.picopilot.vm.EyeMode
import com.picopilot.vm.PicoViewModel

@Composable
fun ScreenScreen(vm: PicoViewModel) {
    val shot by vm.shot.collectAsStateWithLifecycle()
    val shotFile by vm.shotFile.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val recordFile by vm.recordFile.collectAsStateWithLifecycle()
    val eyeMode by vm.eyeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item { SectionHeader("Скриншот") }

        item {
            GlassCard {
                Text(
                    "Кадр",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Pico отдаёт стереокартинку — оба глаза рядом. Выбери, что сохранять.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EyeChip("Оба", eyeMode == EyeMode.BOTH, Modifier.weight(1f)) {
                        vm.setEyeMode(EyeMode.BOTH)
                    }
                    EyeChip("Левый глаз", eyeMode == EyeMode.LEFT, Modifier.weight(1f)) {
                        vm.setEyeMode(EyeMode.LEFT)
                    }
                    EyeChip("Правый глаз", eyeMode == EyeMode.RIGHT, Modifier.weight(1f)) {
                        vm.setEyeMode(EyeMode.RIGHT)
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (shot != null) {
                    val bitmap = shot!!
                    val aspect = (bitmap.width.toFloat() / bitmap.height.toFloat()).coerceIn(0.35f, 2.6f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Экран шлема",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0A0C14))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.PhotoCamera,
                                null,
                                tint = TextSecondary,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Скриншота пока нет", color = TextPrimary, fontSize = 13.sp)
                            Text(
                                "Нажми «Сделать скриншот»",
                                color = TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                GlowButton(
                    text = "Сделать скриншот",
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.PhotoCamera,
                    onClick = vm::takeScreenshot,
                )

                if (shotFile != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(
                            text = "Сохранить",
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Save,
                            onClick = {
                                runCatching { MediaSaver.saveImage(context, shotFile!!) }
                                    .onSuccess { vm.notify("Сохранено: $it") }
                                    .onFailure { vm.notify("Не сохранилось: ${it.message}") }
                            },
                        )
                        GhostButton(
                            text = "Поделиться",
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Share,
                            onClick = {
                                runCatching { MediaSaver.share(context, shotFile!!, "image/png") }
                                    .onFailure { vm.notify("Не получилось: ${it.message}") }
                            },
                        )
                    }
                }
            }
        }

        item { SectionHeader("Запись экрана") }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (recording) Bad.copy(alpha = 0.16f) else Warn.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (recording) Icons.Rounded.Stop else Icons.Rounded.Videocam,
                            null,
                            tint = if (recording) Bad else Warn,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Запись экрана",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (recording) "Идёт запись — до 3 минут" else "MP4 сохранится на телефон",
                            color = TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    if (recording) {
                        Pill("REC", Bad)
                    }
                }

                Spacer(Modifier.height(12.dp))
                GlowButton(
                    text = if (recording) "Остановить и забрать" else "Начать запись",
                    modifier = Modifier.fillMaxWidth(),
                    icon = if (recording) Icons.Rounded.Stop else Icons.Rounded.Videocam,
                    onClick = vm::toggleRecording,
                )

                if (recordFile != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(
                            text = "Сохранить видео",
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Save,
                            tint = Good,
                            onClick = {
                                runCatching { MediaSaver.saveVideo(context, recordFile!!) }
                                    .onSuccess { vm.notify("Сохранено: $it") }
                                    .onFailure { vm.notify("Не сохранилось: ${it.message}") }
                            },
                        )
                        GhostButton(
                            text = "Поделиться",
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Share,
                            onClick = {
                                runCatching { MediaSaver.share(context, recordFile!!, "video/mp4") }
                                    .onFailure { vm.notify("Не получилось: ${it.message}") }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "На некоторых прошивках Pico screenrecord может записывать чёрный экран — это ограничение системы, а не приложения.",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun EyeChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (selected) Accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Accent2 else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
