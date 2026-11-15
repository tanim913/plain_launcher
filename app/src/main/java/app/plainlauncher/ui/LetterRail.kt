package app.plainlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import app.plainlauncher.data.AppSearch
import app.plainlauncher.util.dpF

/** A-Z# fast scroller. Ticks once per letter change. */
class LetterRail(ctx: Context) : View(ctx) {
    var onLetter: ((Int) -> Unit)? = null
    var available: BooleanArray = BooleanArray(AppSearch.LETTERS.length)
        set(v) { field = v; invalidate() }
    var activeColor = 0
    var normalColor = 0
    private var active = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = ctx.dpF(11f) }

    override fun onDraw(c: Canvas) {
        val n = AppSearch.LETTERS.length
        val step = height / n.toFloat()
        for (i in 0 until n) {
            paint.color = when {
                i == active -> activeColor
                available.getOrElse(i) { false } -> normalColor
                else -> (normalColor and 0x00FFFFFF) or 0x55000000
            }
            paint.isFakeBoldText = i == active
            c.drawText(AppSearch.LETTERS[i].toString(), width / 2f, step * i + step / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val n = AppSearch.LETTERS.length
                val idx = (e.y / height * n).toInt().coerceIn(0, n - 1)
                if (idx != active) {
                    active = idx
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onLetter?.invoke(idx)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { active = -1; invalidate() }
        }
        return true
    }
}
