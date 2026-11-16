package com.browser.client.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Windows XP (Luna theme) segmented glossy green progress bar.
 *
 * - Pass a non-null [progress] in `0f..1f` for determinate block progress.
 * - Pass `null` for the classic Windows XP sliding 3-block marquee animation.
 */
@Composable
fun WinXpProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    isPaused: Boolean = false,
    isFailed: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "winxp_marquee")
    val marqueePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "winxp_marquee_phase"
    )

    // Authentic Windows XP Luna progress palette
    val trackOuterBorder = Color(0xFF686868)
    val trackBevelShadow = Color(0xFFBEBEB6)
    val trackBackgroundTop = Color(0xFFE3E2DC)
    val trackBackgroundBottom = Color(0xFFF7F6F2)

    val blockColors = when {
        isFailed -> listOf(
            Color(0xFFFCA5A5),
            Color(0xFFEF4444),
            Color(0xFFDC2626),
            Color(0xFFB91C1C)
        )
        isPaused -> listOf(
            Color(0xFFFDE68A),
            Color(0xFFF59E0B),
            Color(0xFFD97706),
            Color(0xFFB45309)
        )
        else -> listOf(
            Color(0xFF9DF796), // Classic XP glossy top highlight
            Color(0xFF39DE46), // Upper vibrant Luna green
            Color(0xFF1EC42D), // Mid Luna green
            Color(0xFF4BE357), // Lower specular bounce
            Color(0xFF149E22)  // Bottom crisp edge
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val corner = (h * 0.22f).coerceAtMost(4.dp.toPx())
        val strokePx = 1.dp.toPx()

        // 1. Recessed classic XP silver/cream track
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(trackBackgroundTop, trackBackgroundBottom)
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(corner, corner)
        )

        // Subtle inner top bevel shadow
        drawLine(
            color = trackBevelShadow,
            start = Offset(corner, strokePx * 1.4f),
            end = Offset(w - corner, strokePx * 1.4f),
            strokeWidth = strokePx
        )

        // Crisp 1dp XP track border
        drawRoundRect(
            color = trackOuterBorder,
            topLeft = Offset(strokePx / 2f, strokePx / 2f),
            size = Size(w - strokePx, h - strokePx),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = strokePx)
        )

        // Inner content box
        val pad = (strokePx * 2f).coerceAtMost(h * 0.2f)
        val innerLeft = pad
        val innerTop = pad
        val innerWidth = (w - pad * 2f).coerceAtLeast(0f)
        val innerHeight = (h - pad * 2f).coerceAtLeast(0f)
        if (innerWidth <= 0f || innerHeight <= 0f) return@Canvas

        val innerClip = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(innerLeft, innerTop, innerLeft + innerWidth, innerTop + innerHeight),
                    cornerRadius = CornerRadius((corner - pad).coerceAtLeast(1f))
                )
            )
        }

        val blockWidth = (innerHeight * 0.68f).coerceIn(5.dp.toPx(), 11.dp.toPx())
        val blockGap = (blockWidth * 0.28f).coerceIn(1.5.dp.toPx(), 3.dp.toPx())
        val step = blockWidth + blockGap
        val blockCorner = (blockWidth * 0.18f).coerceAtMost(2.dp.toPx())

        val blockBrush = Brush.verticalGradient(
            colors = blockColors,
            startY = innerTop,
            endY = innerTop + innerHeight
        )

        clipPath(innerClip) {
            if (progress == null) {
                // Classic Windows XP 3-block sliding marquee
                val groupCount = 3
                val groupWidth = groupCount * blockWidth + (groupCount - 1) * blockGap
                val startX = -groupWidth + marqueePhase * (innerWidth + groupWidth)
                for (i in 0 until groupCount) {
                    val bx = innerLeft + startX + i * step
                    drawRoundRect(
                        brush = blockBrush,
                        topLeft = Offset(bx, innerTop),
                        size = Size(blockWidth, innerHeight),
                        cornerRadius = CornerRadius(blockCorner, blockCorner)
                    )
                }
            } else {
                // Determinate segmented XP blocks up to progress fraction
                val clamped = progress.coerceIn(0f, 1f)
                val fillRight = innerLeft + innerWidth * clamped
                var bx = innerLeft
                while (bx < fillRight - 0.5f) {
                    val remaining = fillRight - bx
                    val drawW = remaining.coerceAtMost(blockWidth)
                    if (drawW >= 1.dp.toPx()) {
                        drawRoundRect(
                            brush = blockBrush,
                            topLeft = Offset(bx, innerTop),
                            size = Size(drawW, innerHeight),
                            cornerRadius = CornerRadius(blockCorner, blockCorner)
                        )
                    }
                    bx += step
                }
            }
        }
    }
}
