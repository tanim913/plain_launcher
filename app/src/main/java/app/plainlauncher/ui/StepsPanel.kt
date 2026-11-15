package app.plainlauncher.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.plainlauncher.data.Prefs
import app.plainlauncher.data.StepBucket
import app.plainlauncher.data.StepRepo
import app.plainlauncher.data.StepSegment
import app.plainlauncher.data.StepSource
import app.plainlauncher.data.StepStats
import app.plainlauncher.util.dp
import app.plainlauncher.util.dpF
import java.text.NumberFormat
import java.util.Date

/**
 * The page left of home (swipe right on the first page), like MIUI's App Vault step card:
 * the day's total against a goal, distance / active time / calories, an hourly chart for the
 * chosen day and a 7- or 30-day history where tapping a day opens it.
 */
class StepsPanel(
    ctx: Context,
    private val palette: Palette,
    private val prefs: Prefs,
    private val repo: StepRepo,
    private val dialogTheme: () -> Int,
    private val askPermission: () -> Unit,
) : FrameLayout(ctx) {

    private val nf = NumberFormat.getIntegerInstance()
    private val scroll = ScrollView(ctx)
    private val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    private val title = text(30f, bold = true).apply { text = "Steps" }
    private val prev = navButton("‹")
    private val next = navButton("›")
    private val dayLabel = text(16f, bold = true).apply { gravity = Gravity.CENTER }
    private val ring = StepRing(ctx)
    private val stats = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private val statDistance = stat()
    private val statActive = stat()
    private val statKcal = stat()

    private val hourCard = card()
    private val hourTitle = text(16f, bold = true).apply { text = "Through the day" }
    private val hourCaption = text(13f)
    private val hourChart = BarChart(ctx)

    private val histCard = card()
    private val histTitle = text(16f, bold = true).apply { text = "History" }
    private val chip7 = chip("7 days")
    private val chip30 = chip("30 days")
    private val histCaption = text(13f)
    private val histChart = BarChart(ctx)

    private val footer = text(12f)
    /** Shown when the phone-sensor source still needs the Physical activity permission. */
    private val allowButton = TextView(ctx).apply {
        text = "Allow step counting"
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding(dp(20), dp(12), dp(20), dp(12))
        visibility = View.GONE
        setOnClickListener { askPermission() }
    }
    /** Solid strip behind the transparent status bar so scrolled content doesn't run under the clock. */
    private val topScrim = View(ctx)

    private var segs: List<StepSegment> = emptyList()
    private var loaded = false
    private var range = 7
    /** 0 = today, 1 = yesterday, ... */
    private var dayOffset = 0
    private var hour = -1

    init {
        isClickable = true // nothing falls through to home underneath
        isFocusable = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = OVER_SCROLL_NEVER
        scroll.addView(content)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(topScrim, LayoutParams(LayoutParams.MATCH_PARENT, 0, Gravity.TOP))

        content.addView(title, lp(bottom = 12))
        val nav = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(prev, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(dayLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(next, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        content.addView(nav, lp(bottom = 4))
        content.addView(ring, lp(bottom = 20))
        ring.setOnClickListener { editGoal() }

        for (s in listOf(statDistance, statActive, statKcal)) stats.addView(s.first.parent as View, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(stats, lp(bottom = 20))
        content.addView(allowButton, lp(bottom = 20))

        hourCard.addView(hourTitle)
        hourCard.addView(hourCaption, lp(top = 2, bottom = 10))
        hourCard.addView(hourChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)))
        content.addView(hourCard, lp(bottom = 16))

        val histHead = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(histTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(chip7); addView(chip30, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(6) })
        }
        histCard.addView(histHead)
        histCard.addView(histCaption, lp(top = 2, bottom = 10))
        histCard.addView(histChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)))
        content.addView(histCard, lp(bottom = 16))
        content.addView(footer, lp(bottom = 8))

        prev.setOnClickListener { selectDay(dayOffset + 1) }
        next.setOnClickListener { selectDay(dayOffset - 1) }
        chip7.setOnClickListener { range = 7; render() }
        chip30.setOnClickListener { range = 30; render() }
        hourChart.onSelect = { hour = it; renderHourCaption() }
        histChart.onSelect = { i -> if (i >= 0) selectDay(range - 1 - i) else { histChart.selected = range - 1 - dayOffset } }
        applyPalette()
    }

    // ---- public ------------------------------------------------------------------------------

    fun applyInsets(top: Int, bottom: Int) {
        content.setPadding(dp(20), top + dp(20), dp(20), bottom + dp(24))
        topScrim.layoutParams = (topScrim.layoutParams as LayoutParams).apply { height = top }
    }

    fun applyPalette() {
        val bg = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(if (palette.night) 0xFF141218.toInt() else 0xFFFEF7FF.toInt(), palette.accent, 0.04f), 0xFF)
        setBackgroundColor(bg)
        topScrim.setBackgroundColor(ColorUtils.setAlphaComponent(bg, 0xF2))
        val on = palette.onSurface
        val sub = palette.onSurfaceVariant
        for (t in listOf(title, dayLabel, hourTitle, histTitle)) t.setTextColor(on)
        for (t in listOf(hourCaption, histCaption, footer)) t.setTextColor(sub)
        for (b in listOf(prev, next)) b.setTextColor(on)
        allowButton.background = GradientDrawable().apply { setColor(palette.vivid); cornerRadius = dpF(24f) }
        allowButton.setTextColor(if (palette.night) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt())
        for ((v, l) in listOf(statDistance, statActive, statKcal)) { v.setTextColor(on); l.setTextColor(sub) }
        for (c in listOf(hourCard, histCard)) c.background = GradientDrawable().apply { setColor(palette.surfaceVariant); cornerRadius = dpF(24f) }
        val vivid = palette.vivid
        ring.ringColor = vivid; ring.textColor = on; ring.subColor = sub
        for (ch in listOf(hourChart, histChart)) { ch.barColor = vivid; ch.textColor = sub }
        styleChips()
        ring.invalidate(); hourChart.invalidate(); histChart.invalidate()
    }

    /** Fetch fresh data. Called when the panel starts opening and while it is shown. */
    fun reload() {
        repo.history(30) { s -> segs = s; loaded = true; render() }
    }

    /** Back to today, e.g. after the panel closes. */
    fun resetDay() { dayOffset = 0; hour = -1; hourChart.selected = -1 }

    /** True if a horizontal drag starting here belongs to a chart (scrubbing), not the panel. */
    fun ownsHorizontalDrag(rawX: Float, rawY: Float): Boolean {
        val r = Rect()
        for (ch in listOf(hourChart, histChart)) {
            if (!ch.isShown) continue
            ch.getGlobalVisibleRect(r)
            if (r.contains(rawX.toInt(), rawY.toInt())) return true
        }
        return false
    }

    // ---- rendering ---------------------------------------------------------------------------

    private fun dayStart(offset: Int) = StepRepo.addDays(StepRepo.dayStart(System.currentTimeMillis()), -offset)

    private fun selectDay(offset: Int) {
        val o = offset.coerceIn(0, 29)
        if (o == dayOffset) return
        dayOffset = o
        hour = -1
        hourChart.selected = -1
        if (range == 7 && o > 6) range = 30
        render()
    }

    private fun render() {
        val src = repo.source
        val needsPermission = repo.needsSensorPermission()
        val miui = src != StepSource.NONE && !needsPermission // has a history to chart
        val start = dayStart(dayOffset)
        val day: StepBucket
        if (miui) {
            val hours = StepStats.bucket(segs, StepRepo.hourEdges(start))
            day = StepStats.sum(hours)
            hourChart.values = IntArray(24) { hours[it].steps }
            val h24 = DateFormat.is24HourFormat(context)
            hourChart.labels = Array(24) { h ->
                when (h) { 0 -> if (h24) "00" else "12a"; 6 -> if (h24) "06" else "6a"; 12 -> if (h24) "12" else "12p"; 18 -> if (h24) "18" else "6p"; else -> null }
            }
            hourChart.selected = hour
        } else {
            day = StepBucket(repo.steps() ?: 0, 0, 0)
        }

        dayLabel.text = when (dayOffset) {
            0 -> "Today"
            1 -> "Yesterday"
            else -> DateFormat.format("EEE, d MMM", Date(start)).toString()
        }
        prev.isEnabled = miui && dayOffset < 29; prev.alpha = if (prev.isEnabled) 1f else 0.3f
        next.isEnabled = miui && dayOffset > 0; next.alpha = if (next.isEnabled) 1f else 0.3f

        val goal = prefs.stepGoal
        val left = goal - day.steps
        ring.set(day.steps, goal, if (left > 0) "${nf.format(left)} to go · goal ${nf.format(goal)}" else "Goal ${nf.format(goal)} reached")

        statDistance.first.text = if (day.meters >= 1000) String.format("%.2f km", day.meters / 1000) else "${day.meters.toInt()} m"
        val activeKnown = src != StepSource.HEALTH_CONNECT
        statActive.first.text = if (activeKnown) formatDuration(day.activeMs) else "—"
        statKcal.first.text = "${day.kcal.toInt()} kcal"
        statActive.second.text = "Active"
        allowButton.visibility = if (needsPermission) View.VISIBLE else View.GONE

        hourCard.visibility = if (miui) View.VISIBLE else View.GONE
        histCard.visibility = if (miui) View.VISIBLE else View.GONE
        if (miui) { renderHourCaption(day); renderHistory() }

        val tail = " Distance and calories are estimates. Tap the ring to change your goal."
        footer.text = when {
            !loaded -> "Loading…"
            src == StepSource.NONE -> "This phone has no step counter, and Health Connect isn't sharing steps with Plain Launcher."
            needsPermission -> "Plain Launcher can count your steps with the phone's step sensor. Android asks for the Physical activity permission first. Nothing leaves the phone."
            src == StepSource.MIUI -> "Counted by MIUI's step sensor. MIUI keeps only about a day of steps, so Plain Launcher saves its own history on this phone from your first install." + tail
            src == StepSource.HEALTH_CONNECT -> "From Health Connect: steps your phone or apps like Samsung Health and Fit recorded, as hourly totals (so active time isn't known)." + tail
            else -> "Counted by the phone's step sensor, which Plain Launcher keeps listening to in the background (batched, so it barely uses battery). History starts from when you turned it on." + tail
        }
    }

    private fun renderHourCaption(day: StepBucket? = null) {
        val vals = hourChart.values
        if (hour in vals.indices) {
            val h24 = DateFormat.is24HourFormat(context)
            val fmt = if (h24) "HH:mm" else "h a"
            val edges = StepRepo.hourEdges(dayStart(dayOffset))
            hourCaption.text = "${DateFormat.format(fmt, Date(edges[hour]))} – ${DateFormat.format(fmt, Date(edges[hour + 1]))}  ·  ${nf.format(vals[hour])} steps"
        } else {
            val d = day ?: StepBucket(vals.sum(), 0, 0)
            val busiest = vals.indices.maxByOrNull { vals[it] }?.takeIf { vals[it] > 0 }
            hourCaption.text = when {
                d.steps == 0 -> "No steps recorded"
                d.runSteps > 0 -> "Walking ${nf.format(d.walkSteps)}  ·  Running ${nf.format(d.runSteps)}"
                busiest != null -> "Most active around ${DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:00" else "h a", Date(StepRepo.hourEdges(dayStart(dayOffset))[busiest]))}  ·  tap a bar"
                else -> ""
            }
        }
    }

    private fun renderHistory() {
        styleChips()
        val first = dayStart(range - 1)
        val days = StepStats.bucket(segs, StepRepo.dayEdges(first, range))
        histChart.values = IntArray(range) { days[it].steps }
        histChart.refLine = prefs.stepGoal
        histChart.labels = Array(range) { i ->
            val t = StepRepo.addDays(first, i)
            if (range == 7) DateFormat.format("EEEEE", Date(t)).toString()
            else if ((range - 1 - i) % 7 == 0) DateFormat.format("d", Date(t)).toString() else null
        }
        histChart.selected = if (dayOffset < range) range - 1 - dayOffset else -1
        val withSteps = days.filter { it.steps > 0 }
        val avg = if (withSteps.isEmpty()) 0 else withSteps.sumOf { it.steps } / withSteps.size
        val best = days.maxOfOrNull { it.steps } ?: 0
        val hit = days.count { it.steps >= prefs.stepGoal }
        histCaption.text = if (best == 0) "No steps recorded in this period"
        else "Average ${nf.format(avg)}  ·  Best ${nf.format(best)}  ·  Goal met $hit/${range}"
    }

    private fun formatDuration(ms: Long): String {
        val min = ms / DateUtils.MINUTE_IN_MILLIS
        return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
    }

    private fun editGoal() {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.stepGoal.toString())
            setSelectAllOnFocus(true)
        }
        val box = FrameLayout(context).apply { setPadding(dp(24), dp(8), dp(24), 0); addView(input) }
        AlertDialog.Builder(context, dialogTheme())
            .setTitle("Daily step goal")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                input.text.toString().toIntOrNull()?.takeIf { it in 100..100_000 }?.let { prefs.stepGoal = it; render() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- small view helpers ------------------------------------------------------------------

    private fun styleChips() {
        for ((chip, on) in listOf(chip7 to (range == 7), chip30 to (range == 30))) {
            chip.background = GradientDrawable().apply {
                cornerRadius = dpF(16f)
                if (on) setColor(palette.vivid) else setStroke(dp(1), ColorUtils.setAlphaComponent(palette.onSurfaceVariant, 0x66))
            }
            chip.setTextColor(if (on) (if (palette.night) 0xFF1C1B1F.toInt() else 0xFFFFFFFF.toInt()) else palette.onSurfaceVariant)
        }
    }

    private fun text(size: Float, bold: Boolean = false) = TextView(context).apply {
        textSize = size
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private fun navButton(s: String) = TextView(context).apply {
        text = s
        textSize = 28f
        gravity = Gravity.CENTER
        isClickable = true
        background = android.util.TypedValue().let { tv ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
            context.getDrawable(tv.resourceId)
        }
    }

    private fun chip(s: String) = TextView(context).apply {
        text = s
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
    }

    private fun card() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(14))
    }

    /** value + label, stacked and centred. Returns the two TextViews (their parent is the column). */
    private fun stat(): Pair<TextView, TextView> {
        val v = text(18f, bold = true).apply { gravity = Gravity.CENTER }
        val l = text(12f).apply { gravity = Gravity.CENTER }
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(v); addView(l)
        }
        return v to l
    }

    private fun lp(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top); bottomMargin = dp(bottom)
        }

    private fun dp(v: Int) = context.dp(v)
    private fun dpF(v: Float) = context.dpF(v)

    init {
        statDistance.second.text = "Distance"
        statActive.second.text = "Active"
        statKcal.second.text = "Calories"
    }
}
