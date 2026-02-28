package com.example.aigamerfriend.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aigamerfriend.model.Emotion
import com.example.aigamerfriend.ui.theme.NeonGreen
import kotlinx.coroutines.delay

@Composable
fun OnboardingOverlay(onDismiss: () -> Unit) {
    // Cycle through emotions for AIFace preview
    val emotions = listOf(Emotion.HAPPY, Emotion.EXCITED, Emotion.SURPRISED, Emotion.THINKING, Emotion.NEUTRAL)
    var emotionIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            emotionIndex = (emotionIndex + 1) % emotions.size
        }
    }

    // Pulsing button scale
    val pulseTransition = rememberInfiniteTransition(label = "onboardPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "onboardPulseScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .glassPanel(shape = RoundedCornerShape(24.dp), bgAlpha = 0.6f, borderAlpha = 0.12f)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // AIFace preview cycling through emotions
            AIFace(
                emotion = emotions[emotionIndex],
                audioLevel = 0f,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ゲーム画面にカメラを向けよう",
                style = MaterialTheme.typography.titleLarge,
                color = NeonGreen,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TVやモニターのゲーム画面を背面カメラで撮影すると、AIの友達「ユウ」が音声でリアクションするよ！",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                modifier = Modifier.scale(pulseScale),
            ) {
                Text(
                    text = "はじめる",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                )
            }
        }
    }
}
