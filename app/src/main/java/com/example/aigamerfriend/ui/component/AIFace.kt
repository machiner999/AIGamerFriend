package com.example.aigamerfriend.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.annotation.VisibleForTesting
import com.example.aigamerfriend.model.Emotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val NeonGreen = Color(0xFF00E676)
private val FaceBg = Color(0xCC000000)

@VisibleForTesting
internal data class FaceParams(
    val eyeY: Float,
    val eyeRadiusX: Float,
    val eyeRadiusY: Float,
    val browY: Float,
    val browAngle: Float,
    val mouthCurve: Float,
    val mouthOpenY: Float,
)

@VisibleForTesting
internal fun paramsFor(emotion: Emotion): FaceParams = when (emotion) {
    Emotion.NEUTRAL -> FaceParams(
        eyeY = 0.42f, eyeRadiusX = 0.07f, eyeRadiusY = 0.07f,
        browY = 0.30f, browAngle = 0f,
        mouthCurve = 0f, mouthOpenY = 0f,
    )
    Emotion.HAPPY -> FaceParams(
        eyeY = 0.40f, eyeRadiusX = 0.07f, eyeRadiusY = 0.04f,
        browY = 0.25f, browAngle = -12f,
        mouthCurve = 0.16f, mouthOpenY = 0f,
    )
    Emotion.EXCITED -> FaceParams(
        eyeY = 0.38f, eyeRadiusX = 0.10f, eyeRadiusY = 0.10f,
        browY = 0.20f, browAngle = -16f,
        mouthCurve = 0.18f, mouthOpenY = 0.12f,
    )
    Emotion.SURPRISED -> FaceParams(
        eyeY = 0.38f, eyeRadiusX = 0.10f, eyeRadiusY = 0.12f,
        browY = 0.18f, browAngle = 0f,
        mouthCurve = 0f, mouthOpenY = 0.14f,
    )
    Emotion.THINKING -> FaceParams(
        eyeY = 0.44f, eyeRadiusX = 0.07f, eyeRadiusY = 0.04f,
        browY = 0.28f, browAngle = 15f,
        mouthCurve = -0.04f, mouthOpenY = 0f,
    )
    Emotion.WORRIED -> FaceParams(
        eyeY = 0.44f, eyeRadiusX = 0.06f, eyeRadiusY = 0.06f,
        browY = 0.26f, browAngle = 20f,
        mouthCurve = -0.12f, mouthOpenY = 0f,
    )
    Emotion.SAD -> FaceParams(
        eyeY = 0.46f, eyeRadiusX = 0.06f, eyeRadiusY = 0.04f,
        browY = 0.30f, browAngle = 22f,
        mouthCurve = -0.16f, mouthOpenY = 0f,
    )
}

@Composable
fun AIFace(emotion: Emotion, modifier: Modifier = Modifier) {
    val target = paramsFor(emotion)
    val springSpec = spring<Float>(dampingRatio = 0.6f, stiffness = 300f)

    val eyeY by animateFloatAsState(target.eyeY, springSpec, label = "eyeY")
    val eyeRx by animateFloatAsState(target.eyeRadiusX, springSpec, label = "eyeRx")
    val eyeRy by animateFloatAsState(target.eyeRadiusY, springSpec, label = "eyeRy")
    val browY by animateFloatAsState(target.browY, springSpec, label = "browY")
    val browAngle by animateFloatAsState(target.browAngle, springSpec, label = "browAngle")
    val mouthCurve by animateFloatAsState(target.mouthCurve, springSpec, label = "mouthCurve")
    val mouthOpenY by animateFloatAsState(target.mouthOpenY, springSpec, label = "mouthOpenY")

    // Breathing animation: subtle scale oscillation
    val breathTransition = rememberInfiniteTransition(label = "breath")
    val breathScale by breathTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "breathScale",
    )

    // Emotion change bounce
    val bounceScale = remember { Animatable(1f) }
    LaunchedEffect(emotion) {
        bounceScale.animateTo(
            targetValue = 1.08f,
            animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
        )
        bounceScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        )
    }

    val combinedScale = breathScale * bounceScale.value

    Canvas(
        modifier = modifier
            .size(150.dp)
            .graphicsLayer(scaleX = combinedScale, scaleY = combinedScale),
    ) {
        val w = size.width
        val h = size.height

        // Neon glow behind face
        drawCircle(
            color = NeonGreen.copy(alpha = 0.12f),
            radius = size.minDimension / 2f * 1.15f,
        )

        // Background circle
        drawCircle(color = FaceBg)

        // Eyes
        drawEye(
            center = Offset(w * 0.35f, h * eyeY),
            radiusX = w * eyeRx,
            radiusY = h * eyeRy,
        )
        drawEye(
            center = Offset(w * 0.65f, h * eyeY),
            radiusX = w * eyeRx,
            radiusY = h * eyeRy,
        )

        // Eyebrows
        drawBrow(
            centerX = w * 0.35f, y = h * browY,
            length = w * 0.15f, angle = browAngle, leftSide = true,
        )
        drawBrow(
            centerX = w * 0.65f, y = h * browY,
            length = w * 0.15f, angle = browAngle, leftSide = false,
        )

        // Mouth
        drawMouth(
            centerX = w * 0.5f,
            baseY = h * 0.62f,
            width = w * 0.26f,
            curve = h * mouthCurve,
            openHeight = h * mouthOpenY,
        )
    }
}

private fun DrawScope.drawEye(center: Offset, radiusX: Float, radiusY: Float) {
    drawOval(
        color = NeonGreen,
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2, radiusY * 2),
    )
}

private fun DrawScope.drawBrow(centerX: Float, y: Float, length: Float, angle: Float, leftSide: Boolean) {
    val rad = angle * PI.toFloat() / 180f
    val sign = if (leftSide) 1f else -1f
    val dx = cos(rad) * length
    val dy = sin(rad * sign) * length
    drawLine(
        color = NeonGreen,
        start = Offset(centerX - dx, y - dy),
        end = Offset(centerX + dx, y + dy),
        strokeWidth = size.width * 0.035f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawMouth(centerX: Float, baseY: Float, width: Float, curve: Float, openHeight: Float) {
    val strokeW = size.width * 0.035f
    // Lower lip curve
    val lowerPath = Path().apply {
        moveTo(centerX - width, baseY)
        quadraticTo(centerX, baseY + curve * 3 + openHeight, centerX + width, baseY)
    }
    drawPath(lowerPath, color = NeonGreen, style = Stroke(width = strokeW, cap = StrokeCap.Round))
    // Upper lip curve (only visible when mouth opens)
    if (openHeight > 1f) {
        val upperPath = Path().apply {
            moveTo(centerX - width, baseY)
            quadraticTo(centerX, baseY - openHeight * 0.4f, centerX + width, baseY)
        }
        drawPath(upperPath, color = NeonGreen, style = Stroke(width = strokeW, cap = StrokeCap.Round))
    }
}
