package app.plainlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import app.plainlauncher.util.dpF
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/**
 * Rounded bar chart with a top grid value and sparse x labels. Tap or scrub to select a bar;
 * tapping the selected bar again clears the selection.
 */
class BarChart(ctx: Context) : View(ctx) {
    var values = IntArray(0)
        set(v) { field = v; invalidate() }
    /** Label under bar i, or null for none. */
    var labels: Array<String?> = emptyArray()
        set(v) { field = v; invalidate() }
    var selected = -1
        set(v) { field = v; invalidate() }
    /** Dashed reference line (e.g. the daily goal); 0 hides it. */
    var refLine = 0
        set(v) { field = v; invalidate() }
    var onSelect: ((Int) -> Unit)? = null

    var barColor = 0xFF6750A4.toInt()
    var textColor = 0xFF49454F.toInt()

    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = ctx.dpF(1f) }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = ctx.dpF(1.2f)
        pathEffect = DashPathEffect(floatArrayOf(ctx.dpF(4f), ctx.dpF(4f)), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = ctx.dpF(11f) }
    private val r = RectF()
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var scrubbing = false

    private fun niceMax(v: Int): Int {
        if (v <= 0) return 100
        val mag = 10.0.pow(kotlin.math.floor(log10(v.toDouble())))
        val steps = doubleArrayOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)
        for (s in steps) if (s * mag >= v) return (s * mag).toInt()
        return (10 * mag).toInt()
    }

    private fun short(v: Int): String = when {
        v >= 10_000 -> "${v / 1000}k"
        v >= 1000 -> if (v % 1000 == 0) "${v / 1000}k" else String.format("%.1fk", v / 1000f)
        else -> v.toString()
    }

    private val labelH get() = context.dpF(20f)
    private val topPad get() = context.dpF(16f)

    override fun onDraw(c: Canvas) {
        val n = values.size
        if (n == 0 || width == 0) return
        val max = niceMax(maxOf(values.maxOrNull() ?: 0, refLine))
        val bottom = height - labelH
        val h = bottom - topPad
        val slot = width / n.toFloat()
        val bw = (slot * 0.62f).coerceIn(context.dpF(3f), context.dpF(22f))

        grid.color = ColorUtils.setAlphaComponent(textColor, 0x26)
        c.drawLine(0f, bottom, width.toFloat(), bottom, grid)
        c.drawLine(0f, topPad, width.toFloat(), topPad, grid)
        text.color = ColorUtils.setAlphaComponent(textColor, 0xB0)
        text.textAlign = Paint.Align.RIGHT
        c.drawText(short(max), width.toFloat(), topPad - context.dpF(4f), text)

        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            val v = values[i]
            bar.color = when {
                selected < 0 || selected == i -> barColor
                else -> ColorUtils.setAlphaComponent(barColor, 0x66)
            }
            if (v <= 0) {
                bar.color = ColorUtils.setAlphaComponent(textColor, 0x33)
                c.drawCircle(cx, bottom - context.dpF(2f), min(bw / 2, context.dpF(2f)), bar)
            } else {
                val bh = (v / max.toFloat() * h).coerceAtLeast(context.dpF(3f))
                r.set(cx - bw / 2, bottom - bh, cx + bw / 2, bottom)
                val rad = min(bw / 2, context.dpF(6f))
                c.drawRoundRect(r, rad, rad, bar)
                if (bh > rad) c.drawRect(r.left, bottom - rad, r.right, bottom, bar) // square base
            }
            labels.getOrNull(i)?.let {
                text.textAlign = Paint.Align.CENTER
                text.color = if (i == selected) textColor else ColorUtils.setAlphaComponent(textColor, 0xB0)
                text.isFakeBoldText = i == selected
                c.drawText(it, cx, height - context.dpF(4f), text)
                text.isFakeBoldText = false
            }
        }
        if (refLine > 0) {
            dash.color = ColorUtils.setAlphaComponent(barColor, 0x99)
            val y = bottom - refLine / max.toFloat() * h
            c.drawLine(0f, y, width.toFloat(), y, dash)
        }
    }

    private fun indexAt(x: Float): Int = if (values.isEmpty()) -1 else (x / (width / values.size.toFloat())).toInt().coerceIn(0, values.size - 1)

    private fun pick(i: Int, toggle: Boolean) {
        val next = if (toggle && i == selected) -1 else i
        if (next == selected) return
        selected = next
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onSelect?.invoke(next)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; scrubbing = false }
            MotionEvent.ACTION_MOVE -> {
                if (!scrubbing && abs(e.x - downX) > slop && abs(e.x - downX) > abs(e.y - downY)) {
                    scrubbing = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (scrubbing) pick(indexAt(e.x), toggle = false)
            }
            MotionEvent.ACTION_UP -> {
                if (!scrubbing && abs(e.y - downY) < slop) pick(indexAt(e.x), toggle = true)
                scrubbing = false
            }
            MotionEvent.ACTION_CANCEL -> scrubbing = false
        }
        return true
    }
}

/** Progress ring around a big step count. */
class StepRing(ctx: Context) : View(ctx) {
    var steps = 0
    var goal = 8000
    var caption = ""
    var ringColor = 0xFF6750A4.toInt()
    var textColor = 0xFF1C1B1F.toInt()
    var subColor = 0xFF49454F.toInt()

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF()

    fun set(steps: Int, goal: Int, caption: String) {
        this.steps = steps; this.goal = goal; this.caption = caption
        contentDescription = "$steps steps, goal $goal"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(context.dpF(210f).toInt())
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), s)
    }

    override fun onDraw(c: Canvas) {
        val size = height.toFloat()
        val stroke = context.dpF(14f)
        track.strokeWidth = stroke; arc.strokeWidth = stroke
        val cx = width / 2f
        oval.set(cx - size / 2 + stroke, stroke, cx + size / 2 - stroke, size - stroke)
        track.color = ColorUtils.setAlphaComponent(ringColor, 0x2E)
        c.drawArc(oval, 0f, 360f, false, track)
        val p = if (goal > 0) (steps / goal.toFloat()).coerceIn(0f, 1f) else 0f
        arc.color = ringColor
        if (p > 0f) c.drawArc(oval, -90f, ceil(360f * p), false, arc)

        big.color = textColor
        big.textSize = context.dpF(40f)
        c.drawText(java.text.NumberFormat.getIntegerInstance().format(steps), cx, size / 2 + context.dpF(8f), big)
        small.color = subColor
        small.textSize = context.dpF(13f)
        c.drawText(caption, cx, size / 2 + context.dpF(34f), small)
    }
}
