package com.example.aigamerfriend.ui.component

import androidx.annotation.VisibleForTesting
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.aigamerfriend.model.Emotion
import com.example.aigamerfriend.ui.theme.accentColor
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val CoreDeep = Color(0xE6091014)
private val CoreGlass = Color(0x9913232A)
private val CoreRim = Color(0x33FFFFFF)
private val ScanLine = Color(0x33FFFFFF)

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
internal data class EmotionStyle(
    val energy: Float,
    val tension: Float,
    val ringSpeed: Float,
    val particleCount: Int,
    val particleSpread: Float,
)

@VisibleForTesting
internal fun paramsFor(emotion: Emotion): FaceParams = when (emotion) {
    Emotion.NEUTRAL -> FaceParams(
        eyeY = 0.42f, eyeRadiusX = 0.07f, eyeRadiusY = 0.065f,
        browY = 0.30f, browAngle = 0f,
        mouthCurve = 0f, mouthOpenY = 0f,
    )
    Emotion.HAPPY -> FaceParams(
        eyeY = 0.40f, eyeRadiusX = 0.075f, eyeRadiusY = 0.04f,
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

@VisibleForTesting
internal fun styleFor(emotion: Emotion): EmotionStyle = when (emotion) {
    Emotion.NEUTRAL -> EmotionStyle(
        energy = 0.40f,
        tension = 0.20f,
        ringSpeed = 0.85f,
        particleCount = 10,
        particleSpread = 0.80f,
    )
    Emotion.HAPPY -> EmotionStyle(
        energy = 0.68f,
        tension = 0.12f,
        ringSpeed = 1.05f,
        particleCount = 14,
        particleSpread = 0.95f,
    )
    Emotion.EXCITED -> EmotionStyle(
        energy = 1.00f,
        tension = 0.18f,
        ringSpeed = 1.65f,
        particleCount = 22,
        particleSpread = 1.28f,
    )
    Emotion.SURPRISED -> EmotionStyle(
        energy = 0.86f,
        tension = 0.42f,
        ringSpeed = 1.35f,
        particleCount = 18,
        particleSpread = 1.18f,
    )
    Emotion.THINKING -> EmotionStyle(
        energy = 0.52f,
        tension = 0.58f,
        ringSpeed = 0.62f,
        particleCount = 12,
        particleSpread = 0.72f,
    )
    Emotion.WORRIED -> EmotionStyle(
        energy = 0.62f,
        tension = 0.82f,
        ringSpeed = 1.18f,
        particleCount = 16,
        particleSpread = 0.86f,
    )
    Emotion.SAD -> EmotionStyle(
        energy = 0.44f,
        tension = 0.64f,
        ringSpeed = 0.72f,
        particleCount = 12,
        particleSpread = 0.64f,
    )
}

private data class Particle(
    val angle: Float,
    val speed: Float,
    val startRadius: Float,
    val size: Float,
    val trail: Float,
)

@Composable
fun AIFace(emotion: Emotion, modifier: Modifier = Modifier, audioLevel: Float = 0f) {
    val target = paramsFor(emotion)
    val style = styleFor(emotion)
    val springSpec = spring<Float>(dampingRatio = 0.62f, stiffness = 320f)
    val accentColor = emotion.accentColor()
    val safeAudioLevel = audioLevel.coerceIn(0f, 1f)

    val eyeY by animateFloatAsState(target.eyeY, springSpec, label = "eyeY")
    val eyeRx by animateFloatAsState(target.eyeRadiusX, springSpec, label = "eyeRx")
    val eyeRy by animateFloatAsState(target.eyeRadiusY, springSpec, label = "eyeRy")
    val browY by animateFloatAsState(target.browY, springSpec, label = "browY")
    val browAngle by animateFloatAsState(target.browAngle, springSpec, label = "browAngle")
    val mouthCurve by animateFloatAsState(target.mouthCurve, springSpec, label = "mouthCurve")
    val mouthOpenY by animateFloatAsState(target.mouthOpenY, springSpec, label = "mouthOpenY")

    val energy by animateFloatAsState(style.energy, tween(450), label = "energy")
    val tension by animateFloatAsState(style.tension, tween(450), label = "tension")
    val ringSpeed by animateFloatAsState(style.ringSpeed, tween(450), label = "ringSpeed")
    val particleSpread by animateFloatAsState(style.particleSpread, tween(450), label = "particleSpread")

    val animatedRed by animateFloatAsState(accentColor.red, tween(400), label = "colorR")
    val animatedGreen by animateFloatAsState(accentColor.green, tween(400), label = "colorG")
    val animatedBlue by animateFloatAsState(accentColor.blue, tween(400), label = "colorB")
    val drawColor = Color(animatedRed, animatedGreen, animatedBlue)

    val breathTransition = rememberInfiniteTransition(label = "breath")
    val breathScale by breathTransition.animateFloat(
        initialValue = 0.975f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathScale",
    )
    val scanOffset by breathTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanOffset",
    )

    val bounceScale = remember { Animatable(1f) }
    LaunchedEffect(emotion) {
        bounceScale.animateTo(
            targetValue = 1.08f,
            animationSpec = spring(dampingRatio = 0.38f, stiffness = 620f),
        )
        bounceScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.54f, stiffness = 420f),
        )
    }

    val blinkFactor = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000L + Random.nextLong(2200))
            blinkFactor.animateTo(0.05f, tween(55))
            blinkFactor.animateTo(1f, tween(130))
        }
    }

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

    val ringTransition = rememberInfiniteTransition(label = "ring")
    val ringRotation by ringTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringRotation",
    )

    val particles = remember { mutableStateListOf<Particle>() }
    val particleProgress = remember { Animatable(1f) }
    LaunchedEffect(emotion) {
        particles.clear()
        repeat(style.particleCount) {
            particles.add(
                Particle(
                    angle = Random.nextFloat() * 360f,
                    speed = 0.42f + Random.nextFloat() * 0.58f,
                    startRadius = 0.38f + Random.nextFloat() * 0.18f,
                    size = 2.0f + Random.nextFloat() * 3.2f,
                    trail = 0.12f + Random.nextFloat() * 0.16f,
                ),
            )
        }
        particleProgress.snapTo(0f)
        particleProgress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }

    val combinedScale = breathScale * bounceScale.value * (1f + safeAudioLevel * 0.015f)

    Canvas(
        modifier = modifier
            .size(180.dp)
            .graphicsLayer(scaleX = combinedScale, scaleY = combinedScale),
    ) {
        val radius = size.minDimension / 2f
        val pulse = safeAudioLevel * (0.45f + energy * 0.25f)
        val rotation = ringRotation * ringSpeed

        drawAmbientGlow(
            color = drawColor,
            radius = radius,
            audioPulse = pulse,
            energy = energy,
        )
        drawOuterRings(
            color = drawColor,
            radius = radius,
            audioPulse = pulse,
            rotation = rotation,
            tension = tension,
        )
        drawEmotionParticles(
            particles = particles,
            progress = particleProgress.value,
            color = drawColor,
            radius = radius,
            spread = particleSpread,
        )
        drawHologramCore(
            color = drawColor,
            radius = radius,
            audioPulse = pulse,
            scanOffset = scanOffset,
            tension = tension,
        )
        drawHudAccents(
            color = drawColor,
            radius = radius,
            rotation = rotation,
            audioPulse = pulse,
        )

        val blinkEyeRy = eyeRy * blinkFactor.value
        val gazeOffset = size.width * gazeDriftX
        val mouthAudioOpen = safeAudioLevel * (0.03f + energy * 0.045f)
        drawEye(
            center = Offset(size.width * 0.35f + gazeOffset, size.height * eyeY),
            radiusX = size.width * eyeRx,
            radiusY = size.height * blinkEyeRy,
            color = drawColor,
            audioPulse = pulse,
        )
        drawEye(
            center = Offset(size.width * 0.65f + gazeOffset, size.height * eyeY),
            radiusX = size.width * eyeRx,
            radiusY = size.height * blinkEyeRy,
            color = drawColor,
            audioPulse = pulse,
        )
        drawBrow(
            centerX = size.width * 0.35f,
            y = size.height * browY,
            length = size.width * (0.15f + tension * 0.015f),
            angle = browAngle,
            leftSide = true,
            color = drawColor,
        )
        drawBrow(
            centerX = size.width * 0.65f,
            y = size.height * browY,
            length = size.width * (0.15f + tension * 0.015f),
            angle = browAngle,
            leftSide = false,
            color = drawColor,
        )
        drawMouth(
            centerX = size.width * 0.5f,
            baseY = size.height * 0.62f,
            width = size.width * (0.26f + energy * 0.018f),
            curve = size.height * mouthCurve,
            openHeight = size.height * (mouthOpenY + mouthAudioOpen),
            color = drawColor,
            tension = tension,
        )
    }
}

private fun DrawScope.drawAmbientGlow(
    color: Color,
    radius: Float,
    audioPulse: Float,
    energy: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.16f + energy * 0.05f),
                color.copy(alpha = 0.06f + audioPulse * 0.08f),
                Color.Transparent,
            ),
            center = center,
            radius = radius * (1.58f + audioPulse * 0.2f),
        ),
        radius = radius * (1.58f + audioPulse * 0.2f),
    )
    drawCircle(
        color = color.copy(alpha = 0.08f + audioPulse * 0.16f),
        radius = radius * (1.12f + audioPulse * 0.06f),
        style = Stroke(width = radius * 0.065f),
    )
}

private fun DrawScope.drawOuterRings(
    color: Color,
    radius: Float,
    audioPulse: Float,
    rotation: Float,
    tension: Float,
) {
    val dashLen = (18f - audioPulse * 8f).coerceAtLeast(7f)
    val gapLen = 11f + tension * 8f
    rotate(rotation, pivot = center) {
        drawCircle(
            color = color.copy(alpha = 0.45f + audioPulse * 0.22f),
            radius = radius * 1.08f,
            style = Stroke(
                width = radius * 0.018f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, gapLen)),
            ),
        )
    }
    rotate(-rotation * 0.72f, pivot = center) {
        drawCircle(
            color = Color.White.copy(alpha = 0.18f),
            radius = radius * 0.94f,
            style = Stroke(
                width = radius * 0.012f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 20f)),
            ),
        )
    }
    drawCircle(
        color = color.copy(alpha = 0.22f + audioPulse * 0.2f),
        radius = radius * (0.82f + audioPulse * 0.08f),
        style = Stroke(width = radius * 0.01f),
    )
}

private fun DrawScope.drawEmotionParticles(
    particles: List<Particle>,
    progress: Float,
    color: Color,
    radius: Float,
    spread: Float,
) {
    if (progress >= 1f || particles.isEmpty()) return

    val alpha = (1f - progress).coerceAtLeast(0f)
    particles.forEach { particle ->
        val rad = particle.angle * PI.toFloat() / 180f
        val dist = radius * (particle.startRadius + progress * particle.speed * spread)
        val px = center.x + cos(rad) * dist
        val py = center.y + sin(rad) * dist
        val tailDist = radius * particle.trail * (1f - progress * 0.25f)
        val tail = Offset(px - cos(rad) * tailDist, py - sin(rad) * tailDist)
        drawLine(
            color = color.copy(alpha = alpha * 0.42f),
            start = tail,
            end = Offset(px, py),
            strokeWidth = particle.size * 0.65f,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.45f),
            radius = particle.size * 0.42f,
            center = Offset(px, py),
        )
        drawCircle(
            color = color.copy(alpha = alpha * 0.72f),
            radius = particle.size * (1f - progress * 0.48f),
            center = Offset(px, py),
        )
    }
}

private fun DrawScope.drawHologramCore(
    color: Color,
    radius: Float,
    audioPulse: Float,
    scanOffset: Float,
    tension: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                CoreGlass,
                CoreDeep,
                Color.Black.copy(alpha = 0.82f),
            ),
            center = Offset(center.x, center.y - radius * 0.22f),
            radius = radius,
        ),
        radius = radius * 0.86f,
    )
    drawCircle(
        color = CoreRim,
        radius = radius * 0.86f,
        style = Stroke(width = radius * 0.018f),
    )
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                Color.Transparent,
            ),
            start = Offset(center.x - radius * 0.34f, center.y - radius * 0.7f),
            end = Offset(center.x + radius * 0.18f, center.y + radius * 0.12f),
        ),
        radius = radius * 0.69f,
    )
    drawScanLines(
        color = color,
        radius = radius,
        scanOffset = scanOffset,
        audioPulse = audioPulse,
        tension = tension,
    )
}

private fun DrawScope.drawScanLines(
    color: Color,
    radius: Float,
    scanOffset: Float,
    audioPulse: Float,
    tension: Float,
) {
    val coreRadius = radius * 0.76f
    val lineGap = radius * (0.105f - tension * 0.018f)
    var y = center.y - coreRadius + (scanOffset * lineGap * 2f)
    while (y < center.y + coreRadius) {
        val dy = y - center.y
        val half = kotlin.math.sqrt((coreRadius * coreRadius - dy * dy).coerceAtLeast(0f))
        drawLine(
            color = ScanLine.copy(alpha = 0.16f + audioPulse * 0.1f),
            start = Offset(center.x - half * 0.72f, y),
            end = Offset(center.x + half * 0.72f, y),
            strokeWidth = radius * 0.006f,
            cap = StrokeCap.Round,
        )
        y += lineGap
    }
    val sweepY = center.y - coreRadius + (coreRadius * 2f * scanOffset)
    drawLine(
        color = color.copy(alpha = 0.26f + audioPulse * 0.22f),
        start = Offset(center.x - coreRadius * 0.58f, sweepY),
        end = Offset(center.x + coreRadius * 0.58f, sweepY),
        strokeWidth = radius * 0.012f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawHudAccents(
    color: Color,
    radius: Float,
    rotation: Float,
    audioPulse: Float,
) {
    rotate(rotation * 0.35f, pivot = center) {
        repeat(4) { index ->
            rotate(index * 90f, pivot = center) {
                drawLine(
                    color = color.copy(alpha = 0.34f + audioPulse * 0.18f),
                    start = Offset(center.x, center.y - radius * 1.04f),
                    end = Offset(center.x, center.y - radius * 0.94f),
                    strokeWidth = radius * 0.022f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = Offset(center.x + radius * 0.13f, center.y - radius * 1.01f),
                    end = Offset(center.x + radius * 0.24f, center.y - radius * 0.98f),
                    strokeWidth = radius * 0.01f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawEye(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    color: Color,
    audioPulse: Float,
) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.42f + audioPulse * 0.24f),
                Color.Transparent,
            ),
            center = center,
            radius = radiusX * 2.4f,
        ),
        topLeft = Offset(center.x - radiusX * 1.7f, center.y - radiusY * 2.0f),
        size = Size(radiusX * 3.4f, radiusY * 4.0f),
    )
    drawOval(
        color = color.copy(alpha = 0.95f),
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2, radiusY * 2),
    )
    drawOval(
        color = Color.White.copy(alpha = 0.48f),
        topLeft = Offset(center.x - radiusX * 0.32f, center.y - radiusY * 0.48f),
        size = Size(radiusX * 0.52f, radiusY * 0.42f),
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
        color = color.copy(alpha = 0.36f),
        start = Offset(centerX - dx, y - dy + size.width * 0.012f),
        end = Offset(centerX + dx, y + dy + size.width * 0.012f),
        strokeWidth = size.width * 0.052f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(centerX - dx, y - dy),
        end = Offset(centerX + dx, y + dy),
        strokeWidth = size.width * 0.027f,
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
    tension: Float,
) {
    val strokeW = size.width * 0.032f
    val lowerPath = Path().apply {
        moveTo(centerX - width, baseY)
        quadraticTo(centerX, baseY + curve * 3 + openHeight, centerX + width, baseY)
    }
    drawPath(
        path = lowerPath,
        color = color.copy(alpha = 0.30f),
        style = Stroke(width = strokeW * 2.3f, cap = StrokeCap.Round),
    )
    drawPath(lowerPath, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round))

    if (openHeight > 1f) {
        val upperPath = Path().apply {
            moveTo(centerX - width, baseY)
            quadraticTo(centerX, baseY - openHeight * 0.42f, centerX + width, baseY)
        }
        val fillPath = Path().apply {
            moveTo(centerX - width, baseY)
            quadraticTo(centerX, baseY + curve * 3 + openHeight, centerX + width, baseY)
            quadraticTo(centerX, baseY - openHeight * 0.42f, centerX - width, baseY)
            close()
        }
        drawPath(fillPath, color = Color.Black.copy(alpha = 0.34f + tension * 0.12f))
        drawPath(
            path = upperPath,
            color = color.copy(alpha = 0.72f),
            style = Stroke(width = strokeW * 0.72f, cap = StrokeCap.Round),
        )
    }
}
