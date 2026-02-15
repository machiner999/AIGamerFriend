package com.example.aigamerfriend.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.aigamerfriend.model.Emotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val NeonGreen = Color(0xFF00E676)
private val FaceBg = Color(0xCC000000)

private data class FaceParams(
    val eyeY: Float,
    val eyeRadiusX: Float,
    val eyeRadiusY: Float,
    val browY: Float,
    val browAngle: Float,
    val mouthCurve: Float,
    val mouthOpenY: Float,
)

private fun paramsFor(emotion: Emotion): FaceParams = when (emotion) {
    Emotion.NEUTRAL -> FaceParams(
        eyeY = 0.42f, eyeRadiusX = 0.06f, eyeRadiusY = 0.06f,
        browY = 0.30f, browAngle = 0f,
        mouthCurve = 0f, mouthOpenY = 0f,
    )
    Emotion.HAPPY -> FaceParams(
        eyeY = 0.40f, eyeRadiusX = 0.06f, eyeRadiusY = 0.04f,
        browY = 0.27f, browAngle = -5f,
        mouthCurve = 0.08f, mouthOpenY = 0f,
    )
    Emotion.EXCITED -> FaceParams(
        eyeY = 0.40f, eyeRadiusX = 0.08f, eyeRadiusY = 0.08f,
        browY = 0.24f, browAngle = -8f,
        mouthCurve = 0.10f, mouthOpenY = 0.06f,
    )
    Emotion.SURPRISED -> FaceParams(
        eyeY = 0.40f, eyeRadiusX = 0.08f, eyeRadiusY = 0.09f,
        browY = 0.22f, browAngle = 0f,
        mouthCurve = 0f, mouthOpenY = 0.08f,
    )
    Emotion.THINKING -> FaceParams(
        eyeY = 0.43f, eyeRadiusX = 0.06f, eyeRadiusY = 0.04f,
        browY = 0.29f, browAngle = 8f,
        mouthCurve = -0.02f, mouthOpenY = 0f,
    )
    Emotion.WORRIED -> FaceParams(
        eyeY = 0.43f, eyeRadiusX = 0.06f, eyeRadiusY = 0.06f,
        browY = 0.28f, browAngle = 10f,
        mouthCurve = -0.06f, mouthOpenY = 0f,
    )
    Emotion.SAD -> FaceParams(
        eyeY = 0.44f, eyeRadiusX = 0.06f, eyeRadiusY = 0.04f,
        browY = 0.30f, browAngle = 12f,
        mouthCurve = -0.08f, mouthOpenY = 0f,
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

    Canvas(
        modifier = modifier
            .size(120.dp)
            .clip(CircleShape),
    ) {
        // Background circle
        drawCircle(color = FaceBg)

        val w = size.width
        val h = size.height

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
            length = w * 0.12f, angle = browAngle, leftSide = true,
        )
        drawBrow(
            centerX = w * 0.65f, y = h * browY,
            length = w * 0.12f, angle = browAngle, leftSide = false,
        )

        // Mouth
        drawMouth(
            centerX = w * 0.5f,
            baseY = h * 0.62f,
            width = w * 0.22f,
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
        strokeWidth = size.width * 0.025f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawMouth(centerX: Float, baseY: Float, width: Float, curve: Float, openHeight: Float) {
    val strokeW = size.width * 0.025f
    // Lower lip curve
    val lowerPath = Path().apply {
        moveTo(centerX - width, baseY)
        quadraticTo(centerX, baseY + curve * 3 + openHeight, centerX + width, baseY)
    }
    drawPath(lowerPath, color = NeonGreen, style = Stroke(width = strokeW, cap = StrokeCap.Round))
    // Upper lip curve (only visible when mouth opens)
    if (openHeight > 0.5f) {
        val upperPath = Path().apply {
            moveTo(centerX - width, baseY)
            quadraticTo(centerX, baseY - openHeight * 0.4f, centerX + width, baseY)
        }
        drawPath(upperPath, color = NeonGreen, style = Stroke(width = strokeW, cap = StrokeCap.Round))
    }
}
