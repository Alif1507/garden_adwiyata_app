package com.example.home_garden_system.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.example.home_garden_system.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

enum class GardenSymbol { LEAF, DROP, SUN, THERMOMETER, SIGNAL, TUNE, ARROW, POWER, REFRESH }

@Composable
fun GardenIcon(symbol: GardenSymbol, modifier: Modifier = Modifier, color: Color = GardenGreen) {
    Canvas(modifier.size(24.dp)) {
        val unit = size.minDimension / 24f
        scale(unit, unit, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round)
            when (symbol) {
                GardenSymbol.LEAF -> {
                    drawPath(Path().apply { moveTo(5f, 18f); cubicTo(-1f, 8f, 11f, 3f, 21f, 3f)
                        cubicTo(21f, 14f, 18f, 24f, 5f, 18f); close() }, color, style = stroke)
                    drawLine(color, Offset(3f, 22f), Offset(15f, 10f), 1.7f, StrokeCap.Round)
                    drawLine(color, Offset(10f, 15f), Offset(10f, 9f), 1.7f, StrokeCap.Round)
                }
                GardenSymbol.DROP -> drawPath(Path().apply { moveTo(12f, 2f)
                    cubicTo(10f, 6f, 4f, 11f, 4f, 15f); cubicTo(4f, 25f, 20f, 25f, 20f, 15f)
                    cubicTo(20f, 11f, 14f, 6f, 12f, 2f); close() }, color, style = stroke)
                GardenSymbol.SUN -> {
                    drawCircle(color, 4f, Offset(12f, 12f), style = stroke)
                    repeat(8) { index ->
                        val angle = index * Math.PI / 4
                        drawLine(color, Offset(12f + cos(angle).toFloat() * 8, 12f + sin(angle).toFloat() * 8),
                            Offset(12f + cos(angle).toFloat() * 10, 12f + sin(angle).toFloat() * 10), 1.7f, StrokeCap.Round)
                    }
                }
                GardenSymbol.THERMOMETER -> {
                    drawPath(Path().apply { moveTo(9f, 14f); lineTo(9f, 5f)
                        cubicTo(9f, 1f, 15f, 1f, 15f, 5f); lineTo(15f, 14f)
                        cubicTo(22f, 22f, 2f, 26f, 9f, 14f) }, color, style = stroke)
                    drawLine(color, Offset(12f, 8f), Offset(12f, 18f), 2f, StrokeCap.Round)
                    drawCircle(color, 2f, Offset(12f, 18f))
                    drawLine(color, Offset(18f, 6f), Offset(21f, 6f), 1.7f, StrokeCap.Round)
                }
                GardenSymbol.SIGNAL -> repeat(3) { index ->
                    drawLine(color, Offset(5f + index * 7f, 20f), Offset(5f + index * 7f, 14f - index * 5), 3f, StrokeCap.Round)
                }
                GardenSymbol.TUNE -> repeat(3) { index ->
                    val y = 5f + index * 7
                    drawLine(color, Offset(3f, y), Offset(21f, y), 1.7f, StrokeCap.Round)
                    drawCircle(GardenBackground, 2.5f, Offset(if (index == 1) 15f else 8f, y))
                    drawCircle(color, 2.5f, Offset(if (index == 1) 15f else 8f, y), style = stroke)
                }
                GardenSymbol.ARROW -> {
                    drawLine(color, Offset(3f, 12f), Offset(21f, 12f), 1.7f, StrokeCap.Round)
                    drawPath(Path().apply { moveTo(15f, 6f); lineTo(21f, 12f); lineTo(15f, 18f) }, color, style = stroke)
                }
                GardenSymbol.POWER -> {
                    drawArc(color, -50f, 280f, false, Offset(3f, 3f), Size(18f, 18f), style = stroke)
                    drawLine(color, Offset(12f, 1f), Offset(12f, 11f), 1.7f, StrokeCap.Round)
                }
                GardenSymbol.REFRESH -> {
                    drawArc(color, 45f, 275f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                    drawPath(Path().apply {
                        moveTo(13f, 1f)
                        lineTo(17f, 4.5f)
                        lineTo(13f, 8f)
                    }, color, style = stroke)
                }
            }
        }
    }
}

/** Decorative code-native illustration; no network or image assets needed. */
@Composable
fun GardenIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 260f
        scale(unit, unit, pivot = Offset(size.width / 2, size.height / 2)) {
            val cx = size.width / 2
            val cy = size.height / 2
            drawCircle(Color(0xFFE9EFD9), 104f, Offset(cx, cy))
            drawCircle(GardenLime, 30f, Offset(cx + 74, cy - 70))
            drawOval(Color(0xFFD7E1C9), Offset(cx - 74, cy + 80), Size(148f, 16f))
            drawPath(Path().apply { moveTo(cx - 46, cy + 28); lineTo(cx + 46, cy + 28)
                lineTo(cx + 32, cy + 85); lineTo(cx - 32, cy + 85); close() }, Color(0xFFCB8962))
            drawRoundRect(Color(0xFFE1A17A), Offset(cx - 51, cy + 21), Size(102f, 15f), androidx.compose.ui.geometry.CornerRadius(5f))
            drawPath(Path().apply { moveTo(cx, cy + 22); cubicTo(cx - 3, cy - 3, cx + 4, cy - 29, cx + 21, cy - 54) },
                GardenGreen, style = Stroke(5f, cap = StrokeCap.Round))
            drawPath(Path().apply { moveTo(cx + 1, cy - 5); cubicTo(cx - 45, cy - 3, cx - 65, cy - 36, cx - 55, cy - 64)
                cubicTo(cx - 12, cy - 62, cx + 3, cy - 38, cx + 1, cy - 5); close() }, GardenGreen)
            drawPath(Path().apply { moveTo(cx + 7, cy - 23); cubicTo(cx + 8, cy - 64, cx + 40, cy - 78, cx + 69, cy - 75)
                cubicTo(cx + 69, cy - 37, cx + 40, cy - 19, cx + 7, cy - 23); close() }, Color(0xFF73965A))
            drawLine(Color(0xFF92B779), Offset(cx - 4, cy - 13), Offset(cx - 38, cy - 47), 2f, StrokeCap.Round)
            drawLine(GardenLime, Offset(cx + 14, cy - 29), Offset(cx + 52, cy - 61), 2f, StrokeCap.Round)
            drawCircle(GardenGreen.copy(alpha = .3f), 3f, Offset(cx - 84, cy - 45))
            drawCircle(GardenGreen.copy(alpha = .3f), 2f, Offset(cx + 92, cy + 29))
        }
    }
}
