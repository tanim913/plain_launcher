package app.plainlauncher.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Where step numbers come from on this phone. */
enum class StepSource { MIUI, HEALTH_CONNECT, SENSOR, NONE }

/**
 * Steps for the home screen and the Steps page, from the best source this phone has:
 *  1. MIUI's own step store (Xiaomi, Redmi, POCO): the same history App Vault shows.
 *  2. Health Connect, if the user allowed reading steps (Samsung Health, Fit, Android 14+…).
 *  3. The phone's step counter, recorded in the background by [StepRecorder].
 * The user can force one in Home settings. Nothing leaves the phone.
 */
class StepRepo(private val ctx: Context, private val prefs: Prefs) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    var onChange: (() -> Unit)? = null

    val miui: Boolean = MiuiSteps.available(ctx)
    val hasSensor: Boolean = StepRecorder.hasSensor(ctx)

    /** Best guess until [resolve] has checked Health Connect (which needs a background call). */
    @Volatile var source: StepSource = when {
        miui && prefs.stepSource in setOf("auto", "miui") -> StepSource.MIUI
        hasSensor -> StepSource.SENSOR
        else -> StepSource.NONE
    }
        private set

    private var today: Int? = null
    private var todayKey = ""

    init { StepArchive.schedule(ctx) }

    val available get() = source != StepSource.NONE

    /** The phone-sensor source needs the Physical activity permission (Android 10+). */
    fun needsSensorPermission() = source == StepSource.SENSOR && !StepRecorder.hasPermission(ctx)

    private fun dayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Today's steps, or null until the first load. */
    fun steps(): Int? = today.takeIf { todayKey == dayKey() }

    private val tick = object : Runnable {
        override fun run() { refreshToday(); main.postDelayed(this, 60_000) }
    }

    /** Home is visible: pick the source, make sure recording runs, refresh every minute. */
    fun start() {
        io.execute {
            resolve()
            if (source == StepSource.SENSOR) StepRecorder.ensure(ctx)
            if (source == StepSource.MIUI) StepArchive.sync(ctx) // keep the archive fresh even if the counter is hidden
        }
        main.removeCallbacks(tick)
        if (prefs.stepsEnabled) tick.run()
    }

    /** Home is hidden. The recorder keeps running; it is the whole point on non-MIUI phones. */
    fun stop() = main.removeCallbacks(tick)

    /** Re-check the source, e.g. after a settings or permission change. */
    fun reconfigure(done: (() -> Unit)? = null) {
        io.execute {
            resolve()
            if (source == StepSource.SENSOR) StepRecorder.ensure(ctx) else StepRecorder.stop(ctx)
            main.post { done?.invoke(); refreshToday() }
        }
    }

    // io thread
    private fun resolve() {
        val hcRead = { HealthSync.canRead(ctx) }
        source = when (prefs.stepSource) {
            "miui" -> if (miui) StepSource.MIUI else fallback(hcRead)
            "health" -> if (hcRead()) StepSource.HEALTH_CONNECT else if (hasSensor) StepSource.SENSOR else StepSource.NONE
            "sensor" -> if (hasSensor) StepSource.SENSOR else StepSource.NONE
            else -> if (miui) StepSource.MIUI else fallback(hcRead)
        }
        prefs.activeArchive = when (source) {
            StepSource.MIUI -> StepArchive.MIUI
            StepSource.SENSOR -> StepArchive.SENSOR
            else -> ""
        }
    }

    private fun fallback(hcRead: () -> Boolean) = when {
        hcRead() -> StepSource.HEALTH_CONNECT
        hasSensor -> StepSource.SENSOR
        else -> StepSource.NONE
    }

    fun refreshToday() {
        io.execute {
            val start = dayStart(System.currentTimeMillis())
            val end = addDays(start, 1)
            val n = StepStats.sum(StepStats.bucket(load(start, end), longArrayOf(start, end))).steps
            val key = dayKey()
            main.post { today = n; todayKey = key; onChange?.invoke() }
        }
    }

    /** Step segments for the last [days] days including today, delivered on the main thread. */
    fun history(days: Int, cb: (List<StepSegment>) -> Unit) {
        io.execute {
            val now = System.currentTimeMillis()
            val segs = load(addDays(dayStart(now), -(days - 1)), addDays(dayStart(now), 1))
            main.post { cb(segs) }
        }
    }

    // io thread
    private fun load(from: Long, to: Long): List<StepSegment> {
        val all = when (source) {
            StepSource.MIUI -> StepArchive.sync(ctx)
            StepSource.SENSOR -> {
                StepRecorder.ensure(ctx)
                val saved = StepArchive.all(ctx, StepArchive.SENSOR)
                val p = StepRecorder.pendingPiece()
                if (p == null) saved else saved + StepSegment(p.begin, p.end, StepSegment.MODE_WALKING, p.steps)
            }
            StepSource.HEALTH_CONNECT -> return HealthSync.hourlySteps(ctx, from, to)
            StepSource.NONE -> emptyList()
        }
        return all.filter { it.end >= from && it.begin < to }
    }

    companion object {
        fun dayStart(t: Long): Long = Calendar.getInstance().run {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            timeInMillis
        }

        fun addDays(t: Long, n: Int): Long = Calendar.getInstance().run { timeInMillis = t; add(Calendar.DAY_OF_YEAR, n); timeInMillis }

        /** n+1 edges for n consecutive days starting at [firstDay]. */
        fun dayEdges(firstDay: Long, n: Int) = LongArray(n + 1) { addDays(firstDay, it) }

        /** 25 edges for the 24 clock hours of the day starting at [day]. */
        fun hourEdges(day: Long) = LongArray(25) { h ->
            Calendar.getInstance().run { timeInMillis = day; add(Calendar.HOUR_OF_DAY, h); timeInMillis }
        }
    }
}
