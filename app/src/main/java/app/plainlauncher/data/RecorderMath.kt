package app.plainlauncher.data

/**
 * Turns readings of Android's cumulative step counter (steps since boot) into timed pieces.
 * Pure, so reboots and gaps are unit-tested on the JVM.
 *
 * [Last] is the last reading already accounted for. A new reading gives a piece of
 * (reading - last) steps that ends at the reading's time. Its start is the earlier of "one step
 * per second back from the reading" and the last reading's time, so a burst delivered late isn't
 * smeared over hours.
 */
object RecorderMath {
    data class Last(val raw: Long, val wall: Long, val bootWall: Long)
    data class Piece(val begin: Long, val end: Long, val steps: Int)

    /** Different boots differ by far more than clock jitter. */
    private const val BOOT_TOLERANCE_MS = 30_000L

    fun next(last: Last?, raw: Long, wall: Long, bootWall: Long): Pair<Last, Piece?> {
        val now = Last(raw, wall, bootWall)
        if (last == null) return now to null // first reading ever: start counting from here
        val rebooted = kotlin.math.abs(bootWall - last.bootWall) > BOOT_TOLERANCE_MS || raw < last.raw
        val steps = if (rebooted) raw else raw - last.raw
        if (steps <= 0) return now to null
        val floor = if (rebooted) bootWall else last.wall
        val begin = maxOf(floor, wall - steps * 1000L).coerceAtMost(wall)
        return now to Piece(begin, wall, steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    /** Join [p] onto [pending] if it starts within [gapMs] of its end. Returns (joined, flushed-or-null). */
    fun join(pending: Piece?, p: Piece, gapMs: Long = 60_000L): Pair<Piece, Piece?> =
        if (pending != null && p.begin - pending.end <= gapMs) Piece(pending.begin, maxOf(pending.end, p.end), pending.steps + p.steps) to null
        else p to pending
}
