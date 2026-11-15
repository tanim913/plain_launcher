package app.plainlauncher.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import app.plainlauncher.data.AppEntry
import app.plainlauncher.data.IconCache
import app.plainlauncher.data.ItemInfo
import app.plainlauncher.util.dp
import app.plainlauncher.util.dpF

/** An open folder: dimmed backdrop, rounded card, editable title, grid of apps. */
class FolderOverlay(
    ctx: Context,
    private val palette: Palette,
    private val icons: IconCache,
    private val host: Host,
) : FrameLayout(ctx) {
    interface Host {
        fun launchFromFolder(app: AppEntry, v: View)
        fun renameFolder(folderId: Long, title: String)
        fun folderAppMenu(folderId: Long, app: AppEntry, v: View)
    }

    private val card = LinearLayout(ctx)
    private val title = EditText(ctx)
    private val grid = GridLayout(ctx)
    private var folderId = -1L

    val isOpen get() = visibility == VISIBLE

    init {
        visibility = GONE
        setBackgroundColor(0x80000000.toInt())
        setOnClickListener { close() }
        card.orientation = LinearLayout.VERTICAL
        card.isClickable = true
        card.setPadding(ctx.dp(12), ctx.dp(16), ctx.dp(12), ctx.dp(16))
        title.apply {
            gravity = Gravity.CENTER
            textSize = 18f
            setSingleLine()
            background = null
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, _, _ -> commitTitle(); true }
        }
        card.addView(title, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ctx.dp(48)))
        grid.columnCount = 3
        card.addView(grid)
        addView(card, LayoutParams(minOf(resources.displayMetrics.widthPixels - ctx.dp(48), ctx.dp(340)), LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun open(folder: ItemInfo, apps: List<AppEntry>) {
        folderId = folder.id
        card.background = GradientDrawable().apply { setColor(palette.surface or 0xFF000000.toInt()); cornerRadius = context.dpF(28f) }
        title.setTextColor(palette.onSurface)
        title.setText(folder.title ?: "Folder")
        grid.removeAllViews()
        val cardW = minOf(resources.displayMetrics.widthPixels - context.dp(48), context.dp(340)) - context.dp(24)
        val cellW = cardW / 3
        for (app in apps) {
            val v = AppIconView(context)
            v.bind(app, icons, showLabel = true, textColor = palette.onSurface)
            v.setOnClickListener { host.launchFromFolder(app, it) }
            v.setOnLongClickListener { host.folderAppMenu(folderId, app, it); true }
            grid.addView(v, GridLayout.LayoutParams().apply { width = cellW; height = context.dp(92) })
        }
        visibility = VISIBLE
        alpha = 0f
        card.scaleX = 0.9f; card.scaleY = 0.9f
        animate().alpha(1f).setDuration(160).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun commitTitle() {
        val t = title.text.toString().trim().ifEmpty { "Folder" }
        if (folderId >= 0) host.renameFolder(folderId, t)
        title.clearFocus()
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
    }

    fun close() {
        if (!isOpen) return
        commitTitle()
        animate().alpha(0f).setDuration(120).withEndAction { visibility = GONE }.start()
    }
}
