package app.plainlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.widget.TextView
import app.plainlauncher.data.AppEntry
import app.plainlauncher.data.IconCache
import app.plainlauncher.data.NotifDots
import app.plainlauncher.util.dp
import app.plainlauncher.util.dpF

/** Icon + optional label in one TextView (compound drawable). Used on home, dock, drawer and in folders. */
class AppIconView(ctx: Context) : TextView(ctx) {
    private var iconPx = ctx.dp(52)
    private var pkg: String? = null
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5252.toInt() }
    private val onDots = { invalidate() }

    init {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        compoundDrawablePadding = ctx.dp(6)
        textSize = 12f
        isClickable = true
        isHapticFeedbackEnabled = true
    }

    val iconDrawable: Drawable? get() = compoundDrawables[1]

    fun bind(app: AppEntry, icons: IconCache, showLabel: Boolean, textColor: Int, topPad: Int = context.dp(8), shadow: Boolean = false) {
        tag = app.key
        pkg = app.packageName
        iconPx = icons.sizePx
        style(app.label, showLabel, textColor, topPad, shadow)
        val cached = icons.peek(app.key)
        if (cached != null) {
            setIcon(BitmapDrawable(resources, cached))
        } else {
            setIcon(ColorDrawable(0))
            icons.load(app) { bmp -> if (tag == app.key) setIcon(BitmapDrawable(resources, bmp)) }
        }
    }

    fun bindFolder(title: String, icon: Drawable, iconSize: Int, showLabel: Boolean, textColor: Int, topPad: Int, shadow: Boolean) {
        tag = "folder:$title"
        pkg = null
        iconPx = iconSize
        style(title, showLabel, textColor, topPad, shadow)
        setIcon(icon)
    }

    private fun style(label: String, showLabel: Boolean, textColor: Int, topPad: Int, shadow: Boolean) {
        text = if (showLabel) label else ""
        contentDescription = label
        setTextColor(textColor)
        setPadding(paddingLeft, topPad, paddingRight, paddingBottom)
        if (shadow) setShadowLayer(context.dpF(3f), 0f, context.dpF(0.5f), 0x66000000) else setShadowLayer(0f, 0f, 0f, 0)
    }

    fun setIcon(d: Drawable) {
        d.setBounds(0, 0, iconPx, iconPx)
        setCompoundDrawables(null, d, null, null)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); NotifDots.listeners.add(onDots) }
    override fun onDetachedFromWindow() { NotifDots.listeners.remove(onDots); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val p = pkg
        if (p != null && NotifDots.has(p)) {
            c.drawCircle((width + iconPx) / 2f - context.dpF(6f), paddingTop + context.dpF(6f), context.dpF(5f), dotPaint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        return super.onTouchEvent(e)
    }
}
