package app.plainlauncher.data

/**
 * One recorded stretch of walking or running, as MIUI's step service stores it
 * (`com.miui.providers.steps`: _begin_time, _end_time, _mode, _steps). Times are epoch millis.
 */
data class StepSegment(val begin: Long, val end: Long, val mode: Int, val steps: Int) {
    val running get() = mode == MODE_RUNNING

    companion object {
        /** An hourly total (e.g. from Health Connect): steps are real, but the walking time inside the hour is unknown. */
        const val MODE_TOTAL = 0
        // StepMode.run() in MIUI's PowerKeeper stores 2 when walking steps outnumber running ones, else 3.
        // The phone-sensor recorder uses MODE_WALKING too.
        const val MODE_WALKING = 2
        const val MODE_RUNNING = 3
    }
}

/** Totals for one bucket (an hour or a day). */
data class StepBucket(val steps: Int, val runSteps: Int, val activeMs: Long) {
    val walkSteps get() = steps - runSteps
    // Rough, height-independent estimates, labelled as such in the UI.
    val meters get() = walkSteps * 0.70 + runSteps * 1.05
    val kcal get() = walkSteps * 0.040 + runSteps * 0.065

    operator fun plus(o: StepBucket) = StepBucket(steps + o.steps, runSteps + o.runSteps, activeMs + o.activeMs)

    companion object { val EMPTY = StepBucket(0, 0, 0) }
}

object StepStats {
    /**
     * Spread segments over consecutive buckets [edges[i], edges[i+1]). A segment that straddles
     * a boundary is split in proportion to the time it spent on each side, so an hour chart
     * never shows steps in an hour you were not walking. Zero-length segments go to the bucket
     * that contains their timestamp.
     */
    fun bucket(segs: List<StepSegment>, edges: LongArray): Array<StepBucket> {
        val n = edges.size - 1
        if (n <= 0) return emptyArray()
        val steps = DoubleArray(n)
        val run = DoubleArray(n)
        val active = LongArray(n)
        for (s in segs) {
            if (s.steps <= 0) continue
            val b = minOf(s.begin, s.end)
            val e = maxOf(s.begin, s.end)
            if (e < edges[0] || b >= edges[n]) continue
            if (e == b) {
                val i = indexOf(edges, b)
                if (i >= 0) { steps[i] += s.steps.toDouble(); if (s.running) run[i] += s.steps.toDouble() }
                continue
            }
            val len = (e - b).toDouble()
            var i = maxOf(0, indexOf(edges, maxOf(b, edges[0])))
            while (i < n && edges[i] < e) {
                val lo = maxOf(b, edges[i])
                val hi = minOf(e, edges[i + 1])
                if (hi > lo) {
                    val part = s.steps * (hi - lo) / len
                    steps[i] += part
                    if (s.running) run[i] += part
                    if (s.mode != StepSegment.MODE_TOTAL) active[i] += hi - lo
                }
                i++
            }
        }
        return Array(n) { StepBucket(Math.round(steps[it]).toInt(), Math.round(run[it]).toInt(), active[it]) }
    }

    /** Index of the bucket containing t, or -1 if t is outside all buckets. */
    private fun indexOf(edges: LongArray, t: Long): Int {
        if (t < edges[0] || t >= edges[edges.size - 1]) return -1
        var lo = 0
        var hi = edges.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (edges[mid] <= t) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun sum(buckets: Array<StepBucket>): StepBucket = buckets.fold(StepBucket.EMPTY) { a, b -> a + b }
}
