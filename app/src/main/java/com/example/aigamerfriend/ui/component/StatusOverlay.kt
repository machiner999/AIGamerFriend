package com.example.aigamerfriend.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.annotation.VisibleForTesting
import com.example.aigamerfriend.viewmodel.SessionState

@VisibleForTesting
internal fun statusOverlayInfo(state: SessionState): Pair<Color, String>? = when (state) {
    is SessionState.Connected -> Color(0xFF00E676) to "LIVE"
    is SessionState.Reconnecting -> Color(0xFFFFD600) to "再接続中"
    is SessionState.Error -> Color(0xFFFF1744) to "エラー"
    is SessionState.Connecting -> Color(0xFFFFD600) to "接続中"
    is SessionState.Idle -> null
}

@Composable
fun StatusOverlay(
    state: SessionState,
    modifier: Modifier = Modifier,
) {
    val (dotColor, label) = statusOverlayInfo(state) ?: return

    val animatedColor by animateColorAsState(targetValue = dotColor, label = "dotColor")

    val pulseAlpha =
        if (state is SessionState.Connected) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.4f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "pulseAlpha",
            )
            alpha
        } else {
            1f
        }

    Row(
        modifier =
            modifier
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(10.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(animatedColor),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}
