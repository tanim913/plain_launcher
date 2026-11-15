package app.plainlauncher.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import app.plainlauncher.data.AppEntry
import app.plainlauncher.data.IconCache
import app.plainlauncher.data.IconShape

/** Folder tile: a translucent shape with up to four mini icons in a 2x2 grid. */
class FolderDrawable(
    private val shape: IconShape,
    private val fill: Int,
    private val keys: List<String>,
    private val icons: IconCache,
    apps: Map<String, AppEntry>,
) : Drawable() {
    private val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        for (k in keys.take(4)) if (icons.peek(k) == null) apps[k]?.let { icons.load(it) { invalidateSelf() } }
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val r = RectF(b)
        r.inset(b.width() * 0.04f, b.width() * 0.04f)
        c.drawPath(shape.path(r), tile)
        val pad = r.width() * 0.13f
        val gap = r.width() * 0.07f
        val cell = (r.width() - 2 * pad - gap) / 2f
        keys.take(4).forEachIndexed { i, k ->
            val bitmap = icons.peek(k) ?: return@forEachIndexed
            val x = r.left + pad + (i % 2) * (cell + gap)
            val y = r.top + pad + (i / 2) * (cell + gap)
            c.drawBitmap(bitmap, null, RectF(x, y, x + cell, y + cell), bmp)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
