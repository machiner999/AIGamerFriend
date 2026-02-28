package com.example.aigamerfriend.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.annotation.VisibleForTesting
import com.example.aigamerfriend.ui.theme.StatusDelayed
import com.example.aigamerfriend.ui.theme.StatusError
import com.example.aigamerfriend.ui.theme.StatusLive
import com.example.aigamerfriend.ui.theme.StatusWarning
import com.example.aigamerfriend.viewmodel.SessionState

@VisibleForTesting
internal fun statusOverlayInfo(state: SessionState, isDelayed: Boolean = false): Pair<Color, String>? = when (state) {
    is SessionState.Connected -> if (isDelayed) StatusDelayed to "応答待機中" else StatusLive to "LIVE"
    is SessionState.Reconnecting -> StatusWarning to "再接続中"
    is SessionState.Error -> StatusError to "エラー"
    is SessionState.Connecting -> StatusWarning to "接続中"
    is SessionState.Idle -> null
}

@Composable
fun StatusOverlay(
    state: SessionState,
    isDelayed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (dotColor, label) = statusOverlayInfo(state, isDelayed) ?: return

    val animatedColor by animateColorAsState(targetValue = dotColor, label = "dotColor")

    val pulseAlpha =
        if (state is SessionState.Connected) {
            val pulseDuration = if (isDelayed) 500 else 1000
            val transition = rememberInfiniteTransition(label = "pulse")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.4f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(pulseDuration),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "pulseAlpha",
            )
            alpha
        } else {
            1f
        }

    // Shake animation for error state
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state is SessionState.Error) {
            repeat(3) {
                shakeOffset.animateTo(6f, tween(40))
                shakeOffset.animateTo(-6f, tween(40))
            }
            shakeOffset.animateTo(0f, tween(40))
        }
    }

    Row(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .glassPanel(shape = RoundedCornerShape(16.dp), bgAlpha = 0.7f, borderAlpha = 0.1f)
            .graphicsLayer { translationX = shakeOffset.value }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Dot with glow
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(pulseAlpha)
                .drawBehind {
                    drawCircle(
                        color = animatedColor.copy(alpha = 0.4f),
                        radius = size.minDimension / 2f * 1.8f,
                    )
                }
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(color = animatedColor)
                },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}
