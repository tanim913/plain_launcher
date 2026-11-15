package app.plainlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import app.plainlauncher.util.dpF
import kotlin.math.roundToInt

/** Horizontally paged home grid: a RecyclerView with snapping, one CellLayout per page. */
class Workspace(ctx: Context) : RecyclerView(ctx) {
    interface Host {
        val pageCount: Int
        fun newPage(): CellLayout
        fun bindPage(page: CellLayout, screen: Int)
    }

    lateinit var host: Host
    var onScrolledFraction: ((Float) -> Unit)? = null
    private val lm = LinearLayoutManager(ctx, HORIZONTAL, false)

    init {
        layoutManager = lm
        PagerSnapHelper().attachToRecyclerView(this)
        itemAnimator = null
        overScrollMode = OVER_SCROLL_NEVER
        setItemViewCacheSize(3)
        adapter = object : Adapter<ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val page = host.newPage()
                page.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                return object : ViewHolder(page) {}
            }
            override fun onBindViewHolder(h: ViewHolder, position: Int) = host.bindPage(h.itemView as CellLayout, position)
            override fun getItemCount() = if (this@Workspace::host.isInitialized) host.pageCount else 0
        }
        addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) { onScrolledFraction?.invoke(fraction()) }
        })
    }

    fun fraction(): Float = if (width == 0) 0f else computeHorizontalScrollOffset() / width.toFloat()
    fun currentPage(): Int = fraction().roundToInt().coerceIn(0, maxOf(0, host.pageCount - 1))
    fun pageView(i: Int): CellLayout? = lm.findViewByPosition(i) as? CellLayout
    fun scrollToPage(i: Int, smooth: Boolean) { if (smooth) smoothScrollToPosition(i) else scrollToPosition(i) }
    fun refreshAll() { adapter?.notifyDataSetChanged() }
    fun pageAdded() { adapter?.notifyItemInserted(host.pageCount - 1) }
}

/** Row of page dots; the active dot stretches as you swipe. */
class PageDots(ctx: Context) : View(ctx) {
    var count = 1
        set(v) { field = v; invalidate() }
    var position = 0f
        set(v) { field = v; invalidate() }
    var activeColor = 0xFFFFFFFF.toInt()
    var idleColor = 0x66FFFFFF
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        if (count <= 1) return
        val r = context.dpF(3f)
        val gap = context.dpF(10f)
        val total = count * 2 * r + (count - 1) * gap
        var x = (width - total) / 2f
        val cy = height / 2f
        for (i in 0 until count) {
            val near = (1f - kotlin.math.abs(position - i)).coerceIn(0f, 1f)
            paint.color = if (near > 0.5f) activeColor else idleColor
            paint.alpha = (110 + 145 * near).toInt()
            c.drawCircle(x + r, cy, r + near * context.dpF(1.5f), paint)
            x += 2 * r + gap
        }
    }
}
