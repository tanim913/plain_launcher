package app.plainlauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import app.plainlauncher.data.AppEntry
import app.plainlauncher.util.dp
import app.plainlauncher.util.dpF

class PopupItem(val label: String, val run: () -> Unit)

/** Rounded menu used for app long-press, home long-press and weather setup. */
class ItemPopup(private val ctx: Context, private val palette: Palette) {
    private val la = ctx.getSystemService(LauncherApps::class.java)
    private var window: PopupWindow? = null

    fun shortcutsFor(app: AppEntry): List<PopupItem> {
        if (!la.hasShortcutHostPermission()) return emptyList()
        return try {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setActivity(app.component)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            (la.getShortcuts(q, app.user) ?: emptyList())
                .filter { it.isEnabled }
                .take(4)
                .map { s ->
                    PopupItem((s.shortLabel ?: s.id).toString()) {
                        try { la.startShortcut(s, null, null) } catch (_: Exception) {}
                    }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Show next to a view (above it when it sits in the lower half of the screen). */
    fun show(anchor: View, items: List<PopupItem>) {
        val (pw, content) = build(items)
        val loc = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val below = loc[1] < ctx.resources.displayMetrics.heightPixels / 2
        val xoff = (anchor.width - content.measuredWidth) / 2
        val yoff = if (below) 0 else -(anchor.height + content.measuredHeight)
        pw.showAsDropDown(anchor, xoff, yoff)
        window = pw
    }

    /** Show at a touch point inside [parent]. */
    fun showAt(parent: View, x: Int, y: Int, items: List<PopupItem>) {
        val (pw, content) = build(items)
        val dm = ctx.resources.displayMetrics
        val w = content.measuredWidth; val h = content.measuredHeight
        val px = (x - w / 2).coerceIn(ctx.dp(8), dm.widthPixels - w - ctx.dp(8))
        val py = if (y + h + ctx.dp(16) < dm.heightPixels) y else y - h
        pw.showAtLocation(parent, Gravity.NO_GRAVITY, px, py)
        window = pw
    }

    fun dismiss() {
        window?.dismiss()
        window = null
    }

    private fun build(items: List<PopupItem>): Pair<PopupWindow, LinearLayout> {
        dismiss()
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ctx.dp(8), 0, ctx.dp(8))
            background = GradientDrawable().apply {
                setColor(palette.surface or 0xFF000000.toInt())
                cornerRadius = ctx.dpF(20f)
            }
            elevation = ctx.dpF(8f)
        }
        val pw = PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pw.elevation = ctx.dpF(8f)
        pw.setBackgroundDrawable(null)
        for (it in items) {
            content.addView(
                TextView(ctx).apply {
                    text = it.label
                    textSize = 15f
                    setTextColor(palette.onSurface)
                    gravity = Gravity.CENTER_VERTICAL
                    minWidth = ctx.dp(208)
                    minHeight = ctx.dp(48)
                    setPadding(ctx.dp(20), 0, ctx.dp(20), 0)
                    maxLines = 1
                    setOnClickListener { _ -> pw.dismiss(); it.run() }
                },
            )
        }
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        return pw to content
    }
}
