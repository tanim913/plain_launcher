package app.plainlauncher.data

/** A continuous walk (or run) built from MIUI's short step segments. */
data class Walk(val begin: Long, val end: Long, val steps: Int, val runSteps: Int) {
    val durationMs get() = end - begin
    val running get() = runSteps * 2 > steps
}

object Walks {
    /** Pauses up to this long (a crossing, a door) stay inside one walk. */
    const val MAX_GAP_MS = 2 * 60_000L

    /**
     * Join segments whose gap is at most [maxGapMs], then keep walks of at least [minDurationMs].
     * Walks that ended less than [settleMs] before [now] are left out: they may still grow, and
     * once exported a walk's start time is its identity.
     */
    fun group(
        segs: List<StepSegment>,
        minDurationMs: Long,
        now: Long,
        maxGapMs: Long = MAX_GAP_MS,
        settleMs: Long = 10 * 60_000L,
    ): List<Walk> {
        val out = ArrayList<Walk>()
        var cur: Walk? = null
        for (s in segs.sortedBy { it.begin }) {
            if (s.steps <= 0 || s.end <= s.begin || !isWalking(s)) continue
            val run = if (s.running) s.steps else 0
            val c = cur
            cur = if (c != null && s.begin - c.end <= maxGapMs) {
                Walk(c.begin, maxOf(c.end, s.end), c.steps + s.steps, c.runSteps + run)
            } else {
                if (c != null) out += c
                Walk(s.begin, s.end, s.steps, run)
            }
        }
        cur?.let { out += it }
        return out.filter { it.durationMs >= minDurationMs && it.end <= now - settleMs }
    }

    /**
     * Hourly totals carry no walking time, and a long stretch at a crawl (e.g. steps the sensor
     * delivered late in one batch) isn't a walk either. Both still count as steps in the charts.
     */
    fun isWalking(s: StepSegment): Boolean {
        if (s.mode == StepSegment.MODE_TOTAL) return false
        val min = (s.end - s.begin) / 60_000.0
        return min <= 10 || s.steps / min >= 30
    }
}
