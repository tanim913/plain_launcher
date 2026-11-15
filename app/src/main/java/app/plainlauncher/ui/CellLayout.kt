package app.plainlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import app.plainlauncher.util.dpF

/** One page of the home grid (also the 5-slot dock). Children sit on cells and may span several. */
class CellLayout(ctx: Context, var cols: Int, var rows: Int) : ViewGroup(ctx) {
    class LP(var cellX: Int, var cellY: Int, var spanX: Int = 1, var spanY: Int = 1) :
        ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

    var onEmptyLongPress: ((Float, Float) -> Unit)? = null
    var onEmptyDoubleTap: (() -> Unit)? = null
    private var highlight: RectF? = null
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onLongPress(e: MotionEvent) { onEmptyLongPress?.invoke(e.rawX, e.rawY) }
        override fun onDoubleTap(e: MotionEvent): Boolean { onEmptyDoubleTap?.invoke(); return true }
    })

    fun cellW(): Float = (width - paddingLeft - paddingRight) / cols.toFloat()
    fun cellH(): Float = (height - paddingTop - paddingBottom) / rows.toFloat()

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LP(0, 0)
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?) = p is LP
    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams = LP(0, 0)

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        val cw = (w - paddingLeft - paddingRight) / cols.toFloat()
        val ch = (h - paddingTop - paddingBottom) / rows.toFloat()
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val lp = c.layoutParams as LP
            c.measure(
                MeasureSpec.makeMeasureSpec((cw * lp.spanX).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((ch * lp.spanY).toInt(), MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = cellW(); val ch = cellH()
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val lp = c.layoutParams as LP
            val left = paddingLeft + (lp.cellX * cw).toInt()
            val top = paddingTop + (lp.cellY * ch).toInt()
            c.layout(left, top, left + c.measuredWidth, top + c.measuredHeight)
        }
    }

    /** Cell under a point in this view's own coordinates, clamped to the grid. */
    fun cellAt(x: Float, y: Float): IntArray {
        val cx = ((x - paddingLeft) / cellW()).toInt().coerceIn(0, cols - 1)
        val cy = ((y - paddingTop) / cellH()).toInt().coerceIn(0, rows - 1)
        return intArrayOf(cx, cy)
    }

    fun setHighlight(cx: Int, cy: Int, sx: Int, sy: Int) {
        val cw = cellW(); val ch = cellH()
        highlight = RectF(
            paddingLeft + cx * cw + 4f, paddingTop + cy * ch + 4f,
            paddingLeft + (cx + sx) * cw - 4f, paddingTop + (cy + sy) * ch - 4f,
        )
        invalidate()
    }

    fun clearHighlight() { if (highlight != null) { highlight = null; invalidate() } }

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        highlight?.let { c.drawRoundRect(it, context.dpF(18f), context.dpF(18f), highlightPaint) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        gestures.onTouchEvent(e)
        return true
    }
}
