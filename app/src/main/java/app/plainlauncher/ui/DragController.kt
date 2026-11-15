package app.plainlauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import app.plainlauncher.data.AppEntry
import app.plainlauncher.data.ItemInfo
import app.plainlauncher.util.dp
import kotlin.math.hypot

sealed class Payload(val source: View?) {
    class NewApp(val app: AppEntry, source: View?) : Payload(source)
    class Existing(val item: ItemInfo, source: View?) : Payload(source)
}

/**
 * Long-press then move starts a drag. The root view feeds it touch events (so the drag survives
 * the source view being recycled), and the floating icon lives in [layer].
 */
class DragController(private val ctx: Context, private val layer: FrameLayout, private val iconPx: Int, private val listener: Listener) {
    interface Listener {
        fun onDragStart(p: Payload)
        fun onDragMove(p: Payload, x: Float, y: Float)
        fun onDrop(p: Payload, x: Float, y: Float)
        fun onDragEnd(p: Payload)
    }

    private enum class State { IDLE, PRESSED, DRAGGING }

    private var state = State.IDLE
    private var payload: Payload? = null
    private var bitmap: Bitmap? = null
    private var view: ImageView? = null
    private var downX = 0f
    private var downY = 0f
    private val slop = ctx.dp(10)
    private val size get() = (iconPx * 1.12f).toInt()

    /** Icon centre sits this far above the finger so it stays visible. */
    val offsetY = ctx.dp(28)

    val isActive get() = state != State.IDLE
    val isDragging get() = state == State.DRAGGING

    fun press(p: Payload, bmp: Bitmap, rawX: Float, rawY: Float) {
        release()
        payload = p; bitmap = bmp; downX = rawX; downY = rawY
        state = State.PRESSED
    }

    fun shouldStart(x: Float, y: Float) = hypot(x - downX, y - downY) > slop

    fun startDrag(x: Float, y: Float) {
        val p = payload ?: return
        state = State.DRAGGING
        val iv = ImageView(ctx).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(size, size)
            scaleX = 0.85f; scaleY = 0.85f
            elevation = ctx.dp(8).toFloat()
        }
        layer.addView(iv)
        view = iv
        iv.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        position(x, y)
        listener.onDragStart(p)
    }

    fun move(x: Float, y: Float) {
        position(x, y)
        payload?.let { listener.onDragMove(it, x, y) }
    }

    fun drop(x: Float, y: Float) {
        val p = payload
        if (p != null) listener.onDrop(p, x, y)
        end()
    }

    fun cancel() = end()

    /** Finger lifted before any drag began. */
    fun release() {
        if (state == State.PRESSED) { state = State.IDLE; payload = null; bitmap = null }
    }

    private fun position(x: Float, y: Float) {
        val loc = IntArray(2).also { layer.getLocationOnScreen(it) }
        view?.apply {
            translationX = x - loc[0] - size / 2f
            translationY = y - loc[1] - offsetY - size / 2f
        }
    }

    private fun end() {
        val p = payload
        val v = view
        view = null; payload = null; bitmap = null; state = State.IDLE
        v?.animate()?.alpha(0f)?.scaleX(0.7f)?.scaleY(0.7f)?.setDuration(140)?.withEndAction { layer.removeView(v) }?.start()
        if (p != null) listener.onDragEnd(p)
    }
}
