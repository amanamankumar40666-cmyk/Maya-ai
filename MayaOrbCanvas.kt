package com.maya.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.maya.assistant.ui.AssistantUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MayaOrbCanvas(
    state: AssistantUiState,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "rot"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(if (state == AssistantUiState.THINKING) 550 else 1400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.size(240.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.34f * pulse + (rmsLevel * 18f)

        // Outer ambient radial aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0EA5E9).copy(alpha = 0.32f),
                    Color(0xFF040711).copy(alpha = 0f)
                ),
                center = center,
                radius = size.minDimension * 0.5f
            ),
            radius = size.minDimension * 0.5f,
            center = center
        )

        // Orbital segmented ring
        drawArc(
            color = Color(0xFF38BDF8).copy(alpha = 0.65f),
            startAngle = rotation,
            sweepAngle = 220f,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Radial voice waveform bars
        val bars = 48
        for (i in 0 until bars) {
            val angle = (i * (360f / bars) + rotation * 0.4f) * (PI / 180.0)
            val amp = when (state) {
                AssistantUiState.LISTENING -> 12f + rmsLevel * 38f * sin(i * 0.7f + rotation * 0.08f)
                AssistantUiState.SPEAKING -> 10f + 26f * kotlin.math.abs(sin(i * 0.5f + rotation * 0.12f))
                AssistantUiState.THINKING -> 8f + 12f * cos(i * 0.4f + rotation * 0.1f)
                else -> 4f
            }.coerceAtLeast(3f)

            val start = Offset(
                center.x + (baseRadius + 10f) * cos(angle).toFloat(),
                center.y + (baseRadius + 10f) * sin(angle).toFloat()
            )
            val end = Offset(
                center.x + (baseRadius + 10f + amp) * cos(angle).toFloat(),
                center.y + (baseRadius + 10f + amp) * sin(angle).toFloat()
            )
            drawLine(
                color = Color(0xFF38BDF8).copy(alpha = 0.85f),
                start = start,
                end = end,
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Inner neon core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF7DD3FC), Color(0xFF0284C7), Color(0xFF0B1221)),
                center = center,
                radius = baseRadius
            ),
            radius = baseRadius,
            center = center
        )
    }
}
