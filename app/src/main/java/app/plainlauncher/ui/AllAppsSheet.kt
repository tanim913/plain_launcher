package app.plainlauncher.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.plainlauncher.R
import app.plainlauncher.data.AppEntry
import app.plainlauncher.data.AppSearch
import app.plainlauncher.data.IconCache
import app.plainlauncher.util.dp
import app.plainlauncher.util.dpF

/** The swipe-up app drawer: handle, search field, icon grid and a fast-scroll letter rail. */
class AllAppsSheet(
    ctx: Context,
    private val actions: AppActions,
    private val icons: IconCache,
    private val palette: Palette,
) : FrameLayout(ctx) {

    val searchField = EditText(ctx)
    val recycler = RecyclerView(ctx)
    private val handle = View(ctx)
    private val rail = LetterRail(ctx)
    private val column = LinearLayout(ctx)
    private val grid: GridLayoutManager
    private val adapter = Adapter()
    private var all: List<AppEntry> = emptyList()
    private var shown: List<AppEntry> = emptyList()
    private var sectionStarts = IntArray(AppSearch.LETTERS.length) { -1 }

    init {
        val screenDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        grid = GridLayoutManager(ctx, maxOf(4, (screenDp / 88f).toInt()))
        column.orientation = LinearLayout.VERTICAL

        handle.layoutParams = LinearLayout.LayoutParams(ctx.dp(36), ctx.dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = ctx.dp(10)
            bottomMargin = ctx.dp(8)
        }
        column.addView(handle)

        searchField.apply {
            hint = "Search apps"
            maxLines = 1
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_GO
            textSize = 16f
            setPadding(ctx.dp(18), 0, ctx.dp(18), 0)
            compoundDrawablePadding = ctx.dp(12)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(52)).apply {
                marginStart = ctx.dp(16); marginEnd = ctx.dp(16); bottomMargin = ctx.dp(8)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = applyFilter()
            })
            setOnEditorActionListener { _, _, _ ->
                shown.firstOrNull()?.let { actions.launch(it, this) }
                true
            }
        }
        column.addView(searchField)

        recycler.apply {
            layoutManager = grid
            adapter = this@AllAppsSheet.adapter
            setHasFixedSize(true)
            clipToPadding = false
            overScrollMode = OVER_SCROLL_NEVER
            setItemViewCacheSize(8)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(ctx.dp(8), ctx.dp(4), ctx.dp(24), 0)
        }
        column.addView(recycler)
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        rail.layoutParams = LayoutParams(ctx.dp(24), LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.END
            topMargin = ctx.dp(130)
            bottomMargin = ctx.dp(24)
        }
        rail.onLetter = { idx -> jumpTo(idx) }
        addView(rail)

        applyTheme()
    }

    fun applyTheme() {
        background = GradientDrawable().apply {
            setColor(palette.surface)
            val r = context.dpF(28f)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        handle.background = GradientDrawable().apply { setColor(palette.onSurfaceVariant and 0x00FFFFFF or 0x66000000); cornerRadius = context.dpF(2f) }
        searchField.apply {
            background = GradientDrawable().apply { setColor(palette.surfaceVariant); cornerRadius = context.dpF(26f) }
            setTextColor(palette.onSurface)
            setHintTextColor(palette.onSurfaceVariant)
            val icon = ContextCompat.getDrawable(context, R.drawable.ic_search)!!.mutate()
            icon.setTint(palette.onSurfaceVariant)
            icon.setBounds(0, 0, context.dp(22), context.dp(22))
            setCompoundDrawables(icon, null, null, null)
        }
        rail.activeColor = palette.accent
        rail.normalColor = palette.onSurfaceVariant
        adapter.notifyDataSetChanged()
    }

    fun applyInsets(top: Int, bottom: Int) {
        column.setPadding(0, top, 0, 0)
        recycler.setPadding(recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight, bottom + context.dp(8))
        (rail.layoutParams as LayoutParams).apply { topMargin = top + context.dp(130); bottomMargin = bottom + context.dp(24) }
        rail.requestLayout()
    }

    fun submit(apps: List<AppEntry>) {
        all = apps
        applyFilter()
    }

    fun resetSearch() {
        if (searchField.text.isNotEmpty()) searchField.setText("")
        recycler.scrollToPosition(0)
    }

    private fun applyFilter() {
        val q = searchField.text?.toString().orEmpty()
        shown = AppSearch.filter(all, q) { it.label }
        sectionStarts = AppSearch.sectionStarts(shown.map { it.label })
        rail.available = BooleanArray(sectionStarts.size) { sectionStarts[it] >= 0 }
        rail.visibility = if (q.isBlank()) VISIBLE else INVISIBLE
        adapter.notifyDataSetChanged()
    }

    private fun jumpTo(letterIdx: Int) {
        var i = letterIdx
        while (i < sectionStarts.size && sectionStarts[i] < 0) i++
        val pos = if (i < sectionStarts.size) sectionStarts[i] else shown.lastIndex
        if (pos >= 0) grid.scrollToPositionWithOffset(pos, 0)
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val v: AppIconView) : RecyclerView.ViewHolder(v)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = AppIconView(parent.context)
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, parent.context.dp(100))
            v.setPadding(0, parent.context.dp(8), 0, parent.context.dp(8))
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val app = shown[position]
            h.v.bind(app, icons, showLabel = true, textColor = palette.onSurface)
            h.v.setOnClickListener { actions.launch(app, it) }
            h.v.setOnLongClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                actions.showPopup(app, it)
                true
            }
        }

        override fun getItemCount() = shown.size
    }
}
