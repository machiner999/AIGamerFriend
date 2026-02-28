package com.example.aigamerfriend.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.annotation.VisibleForTesting
import com.example.aigamerfriend.model.Emotion
import com.example.aigamerfriend.ui.theme.accentColor
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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

private data class Particle(
    val angle: Float,
    val speed: Float,
    val startRadius: Float,
    val size: Float,
)

@Composable
fun AIFace(emotion: Emotion, modifier: Modifier = Modifier, audioLevel: Float = 0f) {
    val target = paramsFor(emotion)
    val springSpec = spring<Float>(dampingRatio = 0.6f, stiffness = 300f)
    val accentColor = emotion.accentColor()

    // Animated face params
    val eyeY by animateFloatAsState(target.eyeY, springSpec, label = "eyeY")
    val eyeRx by animateFloatAsState(target.eyeRadiusX, springSpec, label = "eyeRx")
    val eyeRy by animateFloatAsState(target.eyeRadiusY, springSpec, label = "eyeRy")
    val browY by animateFloatAsState(target.browY, springSpec, label = "browY")
    val browAngle by animateFloatAsState(target.browAngle, springSpec, label = "browAngle")
    val mouthCurve by animateFloatAsState(target.mouthCurve, springSpec, label = "mouthCurve")
    val mouthOpenY by animateFloatAsState(target.mouthOpenY, springSpec, label = "mouthOpenY")

    // Animated accent color
    val animatedRed by animateFloatAsState(accentColor.red, tween(400), label = "colorR")
    val animatedGreen by animateFloatAsState(accentColor.green, tween(400), label = "colorG")
    val animatedBlue by animateFloatAsState(accentColor.blue, tween(400), label = "colorB")
    val drawColor = Color(animatedRed, animatedGreen, animatedBlue)

    // Breathing animation
    val breathTransition = rememberInfiniteTransition(label = "breath")
    val breathScale by breathTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
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

    // Blink animation: periodically squish eyeRadiusY to near 0
    val blinkFactor = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000L + Random.nextLong(2000))
            blinkFactor.animateTo(0.05f, tween(60))
            blinkFactor.animateTo(1f, tween(120))
        }
    }

    // Eye drift (subtle horizontal gaze movement)
    val gazeTransition = rememberInfiniteTransition(label = "gaze")
    val gazeDriftX by gazeTransition.animateFloat(
        initialValue = -0.015f,
        targetValue = 0.015f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5000
                -0.015f at 0 using LinearEasing
                0.015f at 2000 using FastOutSlowInEasing
                0.005f at 3500 using FastOutSlowInEasing
                -0.015f at 5000 using FastOutSlowInEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "gazeDriftX",
    )

    // Audio-reactive ring rotation
    val ringTransition = rememberInfiniteTransition(label = "ring")
    val ringRotation by ringTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringRotation",
    )

    // Emotion change particles
    val particles = remember { mutableStateListOf<Particle>() }
    val particleProgress = remember { Animatable(0f) }
    LaunchedEffect(emotion) {
        // Generate particles on emotion change
        particles.clear()
        repeat(10) {
            particles.add(
                Particle(
                    angle = Random.nextFloat() * 360f,
                    speed = 0.6f + Random.nextFloat() * 0.6f,
                    startRadius = 0.5f,
                    size = 2f + Random.nextFloat() * 3f,
                ),
            )
        }
        particleProgress.snapTo(0f)
        particleProgress.animateTo(1f, tween(500))
    }

    val combinedScale = breathScale * bounceScale.value

    Canvas(
        modifier = modifier
            .size(180.dp)
            .graphicsLayer(scaleX = combinedScale, scaleY = combinedScale),
    ) {
        val w = size.width
        val h = size.height
        val radius = size.minDimension / 2f

        // 3-layer radial glow (bloom effect)
        drawCircle(
            color = drawColor.copy(alpha = 0.05f),
            radius = radius * 1.4f,
        )
        drawCircle(
            color = drawColor.copy(alpha = 0.08f),
            radius = radius * 1.2f,
        )
        drawCircle(
            color = drawColor.copy(alpha = 0.14f),
            radius = radius * 1.05f,
        )

        // Audio-reactive dashed ring
        val ringAlpha = (0.15f + audioLevel * 0.5f).coerceAtMost(0.6f)
        val dashLen = (16f - audioLevel * 8f).coerceAtLeast(6f)
        val gapLen = (12f + audioLevel * 4f).coerceAtMost(20f)
        drawCircle(
            color = drawColor.copy(alpha = ringAlpha),
            radius = radius * 1.12f,
            style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(dashLen, gapLen),
                    phase = ringRotation,
                ),
            ),
        )

        // Emotion change particles
        if (particleProgress.value < 1f || particles.isNotEmpty()) {
            val progress = particleProgress.value
            val pAlpha = (1f - progress).coerceAtLeast(0f)
            particles.forEach { p ->
                val dist = radius * (p.startRadius + progress * p.speed)
                val rad = p.angle * PI.toFloat() / 180f
                val px = center.x + cos(rad) * dist
                val py = center.y + sin(rad) * dist
                drawCircle(
                    color = drawColor.copy(alpha = pAlpha * 0.7f),
                    radius = p.size * (1f - progress * 0.5f),
                    center = Offset(px, py),
                )
            }
        }

        // Background circle
        drawCircle(color = FaceBg)

        // Eyes (with blink + gaze drift)
        val blinkEyeRy = eyeRy * blinkFactor.value
        val gazeOffset = w * gazeDriftX
        drawEye(
            center = Offset(w * 0.35f + gazeOffset, h * eyeY),
            radiusX = w * eyeRx,
            radiusY = h * blinkEyeRy,
            color = drawColor,
        )
        drawEye(
            center = Offset(w * 0.65f + gazeOffset, h * eyeY),
            radiusX = w * eyeRx,
            radiusY = h * blinkEyeRy,
            color = drawColor,
        )

        // Eyebrows
        drawBrow(
            centerX = w * 0.35f, y = h * browY,
            length = w * 0.15f, angle = browAngle, leftSide = true,
            color = drawColor,
        )
        drawBrow(
            centerX = w * 0.65f, y = h * browY,
            length = w * 0.15f, angle = browAngle, leftSide = false,
            color = drawColor,
        )

        // Mouth
        drawMouth(
            centerX = w * 0.5f,
            baseY = h * 0.62f,
            width = w * 0.26f,
            curve = h * mouthCurve,
            openHeight = h * mouthOpenY,
            color = drawColor,
        )
    }
}

private fun DrawScope.drawEye(center: Offset, radiusX: Float, radiusY: Float, color: Color) {
    // Eye glow
    drawOval(
        color = color.copy(alpha = 0.3f),
        topLeft = Offset(center.x - radiusX * 1.4f, center.y - radiusY * 1.4f),
        size = Size(radiusX * 2.8f, radiusY * 2.8f),
    )
    drawOval(
        color = color,
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2, radiusY * 2),
    )
}

private fun DrawScope.drawBrow(
    centerX: Float,
    y: Float,
    length: Float,
    angle: Float,
    leftSide: Boolean,
    color: Color,
) {
    val rad = angle * PI.toFloat() / 180f
    val sign = if (leftSide) 1f else -1f
    val dx = cos(rad) * length
    val dy = sin(rad * sign) * length
    drawLine(
        color = color,
        start = Offset(centerX - dx, y - dy),
        end = Offset(centerX + dx, y + dy),
        strokeWidth = size.width * 0.035f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawMouth(
    centerX: Float,
    baseY: Float,
    width: Float,
    curve: Float,
    openHeight: Float,
    color: Color,
) {
    val strokeW = size.width * 0.035f
    // Lower lip curve
    val lowerPath = Path().apply {
        moveTo(centerX - width, baseY)
        quadraticTo(centerX, baseY + curve * 3 + openHeight, centerX + width, baseY)
    }
    drawPath(lowerPath, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
    // Upper lip curve (only visible when mouth opens)
    if (openHeight > 1f) {
        val upperPath = Path().apply {
            moveTo(centerX - width, baseY)
            quadraticTo(centerX, baseY - openHeight * 0.4f, centerX + width, baseY)
        }
        drawPath(upperPath, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round))
    }
}
