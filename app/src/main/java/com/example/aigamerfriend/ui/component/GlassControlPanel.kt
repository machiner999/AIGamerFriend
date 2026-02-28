package com.example.aigamerfriend.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aigamerfriend.model.Emotion
import com.example.aigamerfriend.ui.theme.NeonGreen
import com.example.aigamerfriend.ui.theme.StatusError
import com.example.aigamerfriend.ui.theme.StatusWarning
import com.example.aigamerfriend.ui.theme.accentColor
import com.example.aigamerfriend.viewmodel.SessionState

@Composable
fun GlassControlPanel(
    state: SessionState,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    audioLevel: Float,
    currentEmotion: Emotion,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = state is SessionState.Connected ||
        state is SessionState.Connecting ||
        state is SessionState.Reconnecting
    val panelShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(shape = panelShape, bgAlpha = 0.75f, borderAlpha = 0.1f)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatusText(
            state = state,
            modifier = Modifier.weight(1f),
        )

        if (isActive) {
            AudioLevelIndicator(
                level = audioLevel,
                emotionColor = currentEmotion.accentColor(),
                modifier = Modifier.padding(end = 8.dp),
            )

            MuteButton(
                isMuted = isMuted,
                onToggleMute = onToggleMute,
            )
        }

        SessionButton(
            state = state,
            onStart = onStart,
            onStop = onStop,
        )

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "設定",
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun StatusText(state: SessionState, modifier: Modifier = Modifier) {
    val (text, color) = when (state) {
        is SessionState.Idle -> "タップしてゲーム友達を呼ぼう" to Color.White.copy(alpha = 0.7f)
        is SessionState.Connecting -> "接続中..." to StatusWarning
        is SessionState.Connected -> "ゲーム友達が見てるよ！" to NeonGreen
        is SessionState.Reconnecting -> "再接続中..." to StatusWarning
        is SessionState.Error -> state.message to StatusError
    }

    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { it } + fadeIn()) togetherWith
                (slideOutVertically { -it } + fadeOut())
        },
        label = "statusText",
        modifier = modifier,
    ) { targetText ->
        Text(
            text = targetText,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun AudioLevelIndicator(
    level: Float,
    emotionColor: Color,
    modifier: Modifier = Modifier,
) {
    val barCount = 8
    val maxHeight = 22.dp
    val minHeight = 4.dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(barCount) { index ->
            val threshold = (index + 1).toFloat() / barCount
            val targetHeight = if (level >= threshold * 0.5f) {
                minHeight + (maxHeight - minHeight) * (level / 1.0f).coerceAtMost(1f)
            } else {
                minHeight
            }
            val animatedHeight by animateFloatAsState(
                targetValue = targetHeight.value,
                label = "barHeight$index",
            )
            val isActive = level >= threshold * 0.3f
            val barColor = if (isActive) {
                val fraction = index.toFloat() / (barCount - 1).coerceAtLeast(1)
                lerp(NeonGreen, emotionColor, fraction)
            } else {
                Color.White.copy(alpha = 0.15f)
            }
            val glowAlpha = if (isActive) 0.3f * level else 0f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .drawBehind {
                        if (glowAlpha > 0f) {
                            drawRoundRect(
                                color = barColor.copy(alpha = glowAlpha),
                                cornerRadius = CornerRadius(1.5.dp.toPx()),
                                size = Size(size.width + 2.dp.toPx(), size.height + 2.dp.toPx()),
                                topLeft = Offset(-1.dp.toPx(), -1.dp.toPx()),
                            )
                        }
                    }
                    .background(barColor),
            )
        }
    }
}

@Composable
private fun MuteButton(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(isMuted) {
        scale.animateTo(1.15f, spring(dampingRatio = 0.5f, stiffness = 800f))
        scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f))
    }

    IconButton(
        onClick = onToggleMute,
        modifier = Modifier
            .size(40.dp)
            .scale(scale.value)
            .then(
                if (isMuted) {
                    Modifier
                        .clip(CircleShape)
                        .background(StatusError.copy(alpha = 0.2f))
                } else {
                    Modifier
                },
            ),
    ) {
        Icon(
            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = if (isMuted) "ミュート解除" else "ミュート",
            tint = if (isMuted) StatusError else Color.White,
        )
    }
}

@Composable
private fun SessionButton(
    state: SessionState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val isActive = state is SessionState.Connected ||
        state is SessionState.Connecting ||
        state is SessionState.Reconnecting
    val isConnecting = state is SessionState.Connecting || state is SessionState.Reconnecting

    // Pulse ring for connecting state
    val pulseAlpha = if (isConnecting) {
        val transition = rememberInfiniteTransition(label = "btnPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Restart,
            ),
            label = "btnPulseAlpha",
        )
        alpha
    } else {
        0f
    }

    val scale = remember { Animatable(1f) }
    LaunchedEffect(isActive) {
        scale.animateTo(0.9f, tween(50))
        scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
    }

    val buttonColor = if (isActive) StatusError else MaterialTheme.colorScheme.primary

    Button(
        onClick = if (isActive) onStop else onStart,
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        modifier = Modifier
            .scale(scale.value)
            .then(
                if (pulseAlpha > 0f) {
                    Modifier.drawBehind {
                        drawCircle(
                            color = StatusWarning.copy(alpha = pulseAlpha),
                            radius = size.maxDimension / 2f + 8.dp.toPx(),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = if (isActive) "終了" else "開始",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f,
    )
}
