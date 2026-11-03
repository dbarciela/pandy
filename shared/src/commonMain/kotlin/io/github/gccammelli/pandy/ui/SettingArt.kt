package io.github.gccammelli.pandy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A small line drawing of an infill pattern, a brim or a support style, named as in [io.github.gccammelli.pandy.settingArt]. */
@Composable
internal fun SettingArt(art: String, color: Color, size: Dp) =
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val line = Stroke(width = s / 28f, cap = StrokeCap.Round)
        when (art.substringBefore(':')) {
            "infill" -> clipRect { drawInfill(art.substringAfter(':'), color, s, line) }
            "brim" -> drawBrim(art.substringAfter(':'), color, s, line)
            else -> drawSupport(art.substringAfter(':') == "tree", color, s, line)
        }
    }

private fun DrawScope.family(angle: Double, gap: Float, s: Float, color: Color, line: Stroke) {
    val c = cos(angle * PI / 180).toFloat()
    val n = sin(angle * PI / 180).toFloat()
    var k = -s
    while (k <= s) {
        // A line through the tile centre, shifted k along its normal.
        val cx = s / 2 - n * k
        val cy = s / 2 + c * k
        drawLine(color, Offset(cx - c * s, cy - n * s), Offset(cx + c * s, cy + n * s), line.width, line.cap)
        k += gap
    }
}

private fun DrawScope.hexagons(radius: Float, s: Float, color: Color, line: Stroke, spokes: Boolean) {
    val dx = radius * sqrt(3f)
    var y = 0f
    var row = 0
    while (y < s + radius) {
        var x = if (row % 2 == 1) dx / 2 else 0f
        while (x < s + dx) {
            val corners = (0 until 6).map { i ->
                val a = (60 * i - 30) * PI / 180
                Offset(x + radius * cos(a).toFloat(), y + radius * sin(a).toFloat())
            }
            drawPath(Path().apply { moveTo(corners[0].x, corners[0].y); corners.drop(1).forEach { lineTo(it.x, it.y) }; close() }, color, style = line)
            if (spokes) corners.filterIndexed { i, _ -> i % 2 == 0 }.forEach { drawLine(color, Offset(x, y), it, line.width, line.cap) }
            x += dx
        }
        y += radius * 1.5f
        row++
    }
}

private fun DrawScope.drawInfill(kind: String, color: Color, s: Float, line: Stroke) {
    when (kind) {
        "grid" -> { family(0.0, s / 6, s, color, line); family(90.0, s / 6, s, color, line) }
        "triangles" -> listOf(0.0, 60.0, 120.0).forEach { family(it, s / 5, s, color, line) }
        "tri-hexagon" -> hexagons(s / 6, s, color, line, spokes = true)
        "honeycomb" -> hexagons(s / 7, s, color, line, spokes = false)
        "gyroid" -> for (band in 0 until 4) for (phase in listOf(0.0, PI)) {
            val path = Path()
            for (i in 0..40) {
                val x = s * i / 40
                val y = s * (band * 0.25f + 0.12f) + s * 0.1f * sin(i / 40.0 * PI * 4 + phase).toFloat() + if (phase > 0) s * 0.06f else 0f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = line)
        }
        "cubic" -> { family(45.0, s / 4, s, color, line); family(135.0, s / 4, s, color, line); family(0.0, s / 2, s, color, line) }
        "adaptivecubic" -> { family(45.0, s / 8, s, color, line); family(135.0, s / 8, s, color, line) }
        else -> family(45.0, s / 7, s, color, line) // line / rectilinear
    }
}

private fun DrawScope.drawBrim(kind: String, color: Color, s: Float, line: Stroke) {
    fun ring(inset: Float, dashed: Boolean = false) = drawRect(color, Offset(s * inset, s * inset), Size(s * (1 - 2 * inset), s * (1 - 2 * inset)),
        style = if (dashed) Stroke(line.width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s / 14, s / 14))) else line)
    ring(0.36f) // the model's footprint
    when (kind) {
        "auto_brim" -> ring(0.2f, dashed = true)
        "outer_only" -> ring(0.22f)
        "inner_only" -> ring(0.5f - 0.07f).also { ring(0.5f - 0.15f) }
        "outer_and_inner" -> { ring(0.2f); ring(0.1f) }
    }
}

private fun DrawScope.drawSupport(tree: Boolean, color: Color, s: Float, line: Stroke) {
    drawLine(color, Offset(s * .1f, s * .88f), Offset(s * .9f, s * .88f), line.width, line.cap) // build plate
    drawRect(color, Offset(s * .12f, s * .18f), Size(s * .76f, s * .12f), style = line) // the overhang
    if (tree) {
        drawLine(color, Offset(s * .5f, s * .88f), Offset(s * .5f, s * .55f), line.width, line.cap)
        drawLine(color, Offset(s * .5f, s * .55f), Offset(s * .3f, s * .3f), line.width, line.cap)
        drawLine(color, Offset(s * .5f, s * .55f), Offset(s * .7f, s * .3f), line.width, line.cap)
    } else listOf(.22f, .4f, .6f, .78f).forEach { drawLine(color, Offset(s * it, s * .3f), Offset(s * it, s * .88f), line.width, line.cap) }
}
