package com.example.aigamerfriend.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.aigamerfriend.model.Emotion

// Gaming accent colors
val NeonGreen = Color(0xFF39FF14)
val NeonBlue = Color(0xFF00D4FF)
val DarkSurface = Color(0xFF121212)
val DarkBackground = Color(0xFF0A0A0A)

// Emotion accent colors
val EmotionGreen = Color(0xFF00E676)
val EmotionGold = Color(0xFFFFD740)
val EmotionCyan = Color(0xFF00E5FF)
val EmotionPurple = Color(0xFFB388FF)
val EmotionOrange = Color(0xFFFF9100)
val EmotionBlue = Color(0xFF448AFF)
val EmotionNeutral = Color(0xFF00E676)

// Glass effect tokens
val GlassBg = Color(0xB3000000) // 70% black
val GlassBorder = Color(0x14FFFFFF) // 8% white
val GlassHighlight = Color(0x0DFFFFFF) // 5% white

// Status colors
val StatusLive = Color(0xFF00E676)
val StatusWarning = Color(0xFFFFD600)
val StatusError = Color(0xFFFF1744)
val StatusDelayed = Color(0xFFFF9100)

fun Emotion.accentColor(): Color = when (this) {
    Emotion.NEUTRAL -> EmotionNeutral
    Emotion.HAPPY -> EmotionGreen
    Emotion.EXCITED -> EmotionGold
    Emotion.SURPRISED -> EmotionCyan
    Emotion.THINKING -> EmotionPurple
    Emotion.WORRIED -> EmotionOrange
    Emotion.SAD -> EmotionBlue
}
