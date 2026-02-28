package com.example.aigamerfriend.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

fun Modifier.glassPanel(
    shape: Shape = RectangleShape,
    bgAlpha: Float = 0.82f,
    borderAlpha: Float = 0.08f,
): Modifier {
    val bgColor = Color.Black.copy(alpha = bgAlpha)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = borderAlpha * 2.5f),
            Color.White.copy(alpha = borderAlpha * 0.5f),
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )
    val highlightBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.06f),
            Color.Transparent,
        ),
        startY = 0f,
        endY = 80f,
    )

    return this
        .background(color = bgColor, shape = shape)
        .border(width = 1.dp, brush = borderBrush, shape = shape)
        .drawBehind {
            drawRect(brush = highlightBrush)
        }
}
