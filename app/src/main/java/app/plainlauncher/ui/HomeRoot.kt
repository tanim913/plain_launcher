package app.plainlauncher.ui

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

interface SheetHost {
    val isSheetOpen: Boolean
    val sheetHeight: Int
    val sheetTranslation: Float
    fun canSheetScrollUp(): Boolean
    fun onSheetDragStart()
    fun onSheetDrag(translationY: Float)
    fun onSheetRelease(velocityY: Float)
    fun onSwipeDown()

    // Steps page to the left of home.
    val isPanelOpen: Boolean
    val panelWidth: Int
    val panelTranslation: Float
    fun canOpenPanel(): Boolean
    fun panelOwnsDrag(rawX: Float, rawY: Float): Boolean
    fun onPanelDragStart()
    fun onPanelDrag(translationX: Float)
    fun onPanelRelease(velocityX: Float)
}

/**
 * Root view. Owns three gestures: pull the app drawer up/down, swipe down for notifications,
 * and the drag-and-drop stream (it sees every touch, so a drag survives recycled views).
 */
class HomeRoot(ctx: Context, private val host: SheetHost) : FrameLayout(ctx) {
    var drag: DragController? = null
    var downRawX = 0f; private set
    var downRawY = 0f; private set

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var originY = 0f
    private var startTy = 0f
    private var dragging = false
    private var swallow = false
    private var scrollableAtDown = false
    private var panelDragging = false
    private var panelAllowedAtDown = false
    private var originX = 0f
    private var startTx = 0f
    private var vt: VelocityTracker? = null

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { downRawX = e.rawX; downRawY = e.rawY }
        val d = drag
        if (d != null && d.isActive) {
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (d.isDragging) { d.move(e.rawX, e.rawY); return true }
                    if (d.shouldStart(e.rawX, e.rawY)) {
                        val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        d.startDrag(e.rawX, e.rawY)
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (d.isDragging) { d.drop(e.rawX, e.rawY); return true }
                    d.release()
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (d.isDragging) { d.cancel(); return true }
                    d.release()
                }
            }
            if (d.isDragging) return true
        }
        return super.dispatchTouchEvent(e)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (drag?.isActive == true) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; dragging = false; swallow = false; panelDragging = false
                scrollableAtDown = host.canSheetScrollUp()
                panelAllowedAtDown = if (host.isPanelOpen) !host.panelOwnsDrag(e.rawX, e.rawY)
                else !host.isSheetOpen && host.canOpenPanel()
                vt?.recycle(); vt = VelocityTracker.obtain(); vt?.addMovement(e)
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dy = e.y - downY
                val dx = e.x - downX
                if (!dragging && !swallow && !panelDragging && panelAllowedAtDown && host.panelWidth > 0 &&
                    abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) {
                    val opening = !host.isPanelOpen && dx > 0
                    val closing = host.isPanelOpen && dx < 0
                    if (opening || closing) {
                        panelDragging = true
                        originX = e.x
                        startTx = host.panelTranslation
                        host.onPanelDragStart()
                    }
                }
                if (!dragging && !swallow && !panelDragging && !host.isPanelOpen && host.sheetHeight > 0) {
                    if (abs(dy) > slop && abs(dy) > abs(dx) * 1.5f) {
                        val opening = !host.isSheetOpen && dy < 0
                        val closing = host.isSheetOpen && dy > 0 && !scrollableAtDown
                        if (opening || closing) {
                            dragging = true
                            originY = e.y
                            startTy = host.sheetTranslation
                            host.onSheetDragStart()
                        } else if (!host.isSheetOpen && dy > slop * 4) {
                            swallow = true
                            host.onSwipeDown()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging && !panelDragging) { vt?.recycle(); vt = null }
        }
        return dragging || swallow || panelDragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (swallow) {
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) swallow = false
            return true
        }
        if (panelDragging) {
            vt?.addMovement(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE ->
                    host.onPanelDrag((startTx + e.x - originX).coerceIn(-host.panelWidth.toFloat(), 0f))
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vt?.computeCurrentVelocity(1000)
                    val v = if (e.actionMasked == MotionEvent.ACTION_UP) vt?.xVelocity ?: 0f else 0f
                    panelDragging = false
                    vt?.recycle(); vt = null
                    host.onPanelRelease(v)
                }
            }
            return true
        }
        if (!dragging) return super.onTouchEvent(e)
        vt?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE ->
                host.onSheetDrag((startTy + e.y - originY).coerceIn(0f, host.sheetHeight.toFloat()))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                vt?.computeCurrentVelocity(1000)
                val v = if (e.actionMasked == MotionEvent.ACTION_UP) vt?.yVelocity ?: 0f else 0f
                dragging = false
                vt?.recycle(); vt = null
                host.onSheetRelease(v)
            }
        }
        return true
    }
}
