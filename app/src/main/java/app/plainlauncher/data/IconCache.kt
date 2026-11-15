package app.plainlauncher.data

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import app.plainlauncher.util.dp
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Renders every icon into one consistent shape:
 *  - adaptive icons: background + foreground layers, cut to the shape
 *  - full-bleed legacy icons (MIUI's square theme icons): scaled to fill the shape
 *  - transparent legacy icons: trimmed to their content and centred on a white shape
 * Results live in a memory-bounded LruCache.
 */
class IconCache(private val ctx: Context, private val repo: AppRepository, private val prefs: Prefs) {
    val sizePx = ctx.dp(52)

    private val cache: LruCache<String, Bitmap>
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0
    @Volatile var pack: IconPack? = null

    init {
        val memoryClassMb = ctx.getSystemService(ActivityManager::class.java).memoryClass
        cache = object : LruCache<String, Bitmap>(memoryClassMb * 1024 * 1024 / 12) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }
    }

    fun peek(key: String): Bitmap? = cache.get(key)

    fun load(app: AppEntry, done: (Bitmap) -> Unit) {
        val gen = generation
        pool.execute {
            val bmp = cache.get(app.key) ?: repo.iconFor(app.key)?.let { render(it, prefs.iconShape, pack?.drawableFor(app.component)) }
                ?.also { if (gen == generation) cache.put(app.key, it) }
            if (bmp != null && gen == generation) main.post { done(bmp) }
        }
    }

    /** Drop everything, e.g. after the icon shape changes. */
    fun clear() {
        generation++
        cache.evictAll()
    }

    fun trim(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) cache.evictAll()
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) cache.trimToSize(cache.size() / 2)
    }

    private fun render(d: Drawable, shape: IconShape, packIcon: Drawable?): Bitmap {
        val s = sizePx
        if (packIcon != null) {
            // Icon-pack icons come pre-shaped: draw as-is.
            val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            packIcon.setBounds(0, 0, s, s)
            packIcon.draw(Canvas(out))
            return out
        }
        val content = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cc = Canvas(content)
        if (d is AdaptiveIconDrawable) {
            // Draw the layers ourselves so the system's own mask (square on some ROMs) is not applied.
            val o = (s * 0.25f).toInt()
            d.background?.apply { setBounds(-o, -o, s + o, s + o); draw(cc) }
            d.foreground?.apply { setBounds(-o, -o, s + o, s + o); draw(cc) }
        } else {
            val raw = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, s, s)
            d.draw(Canvas(raw))
            if (isFullBleed(raw)) {
                // Slight zoom pushes a square icon's own rounded corners outside the mask.
                val z = if (shape == IconShape.CIRCLE) 1.0f else 1.06f
                val off = (s - s * z) / 2f
                cc.drawBitmap(raw, null, RectF(off, off, s - off, s - off), Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                cc.drawColor(Color.WHITE)
                val box = alphaBounds(raw)
                if (!box.isEmpty) {
                    val target = s * 0.64f
                    val k = target / max(box.width(), box.height())
                    val w = box.width() * k; val h = box.height() * k
                    cc.drawBitmap(raw, box, RectF((s - w) / 2f, (s - h) / 2f, (s + w) / 2f, (s + h) / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
                }
            }
            raw.recycle()
        }

        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        val pad = s * 0.04f
        val rect = RectF(pad, pad, s - pad, s - pad)
        val path = shape.path(rect)
        // soft key shadow
        oc.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(s * 0.03f, 0f, s * 0.012f, 0x40000000)
        })
        val shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { val k = rect.width() / s; setScale(k, k); postTranslate(pad, pad) })
        }
        oc.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
        content.recycle()
        return out
    }

    private fun isFullBleed(b: Bitmap): Boolean {
        val s = b.width - 1
        val pts = floatArrayOf(0.5f, 0.03f, 0.03f, 0.5f, 0.97f, 0.5f, 0.5f, 0.97f, 0.15f, 0.15f, 0.85f, 0.15f, 0.15f, 0.85f, 0.85f, 0.85f)
        var i = 0
        while (i < pts.size) {
            if ((b.getPixel((pts[i] * s).toInt(), (pts[i + 1] * s).toInt()) ushr 24) < 200) return false
            i += 2
        }
        return true
    }

    private fun alphaBounds(b: Bitmap): Rect {
        val w = b.width; val h = b.height
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        var l = w; var t = h; var r = -1; var bt = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > 30) {
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > bt) bt = y
            }
        }
        return if (r < 0) Rect() else Rect(l, t, r + 1, bt + 1)
    }
}
