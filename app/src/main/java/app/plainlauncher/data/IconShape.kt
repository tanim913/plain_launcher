package app.plainlauncher.data

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

enum class IconShape(val title: String) {
    CIRCLE("Circle"),
    SQUIRCLE("Squircle"),
    ROUNDED_SQUARE("Rounded square");

    fun path(r: RectF): Path = Path().apply {
        val cx = r.centerX(); val cy = r.centerY(); val half = r.width() / 2f
        when (this@IconShape) {
            CIRCLE -> addCircle(cx, cy, half, Path.Direction.CW)
            ROUNDED_SQUARE -> addRoundRect(r, r.width() * 0.24f, r.width() * 0.24f, Path.Direction.CW)
            SQUIRCLE -> {
                // superellipse |x|^4 + |y|^4 = 1
                val n = 96
                for (i in 0 until n) {
                    val t = 2.0 * Math.PI * i / n
                    val c = cos(t); val s = sin(t)
                    val x = cx + half * (sign(c) * abs(c).pow(0.5)).toFloat()
                    val y = cy + half * (sign(s) * abs(s).pow(0.5)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
    }
}
