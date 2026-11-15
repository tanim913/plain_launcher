package app.plainlauncher

import app.plainlauncher.data.StepSegment
import app.plainlauncher.data.StepStats
import org.junit.Assert.assertEquals
import org.junit.Test

class StepStatsTest {
    private val h = 3_600_000L
    private val edges = LongArray(5) { it * h } // 4 one-hour buckets

    @Test fun segmentInsideOneBucket() {
        val b = StepStats.bucket(listOf(StepSegment(10 * 60_000L, 20 * 60_000L, 1, 600)), edges)
        assertEquals(listOf(600, 0, 0, 0), b.map { it.steps })
        assertEquals(10 * 60_000L, b[0].activeMs)
    }

    @Test fun straddlingSegmentIsSplitByTime() {
        // 45 min in hour 0, 15 min in hour 1
        val b = StepStats.bucket(listOf(StepSegment(15 * 60_000L, h + 15 * 60_000L, 1, 1000)), edges)
        assertEquals(listOf(750, 250, 0, 0), b.map { it.steps })
    }

    @Test fun runningCountedSeparately() {
        val b = StepStats.bucket(listOf(StepSegment(h, h + 60_000L, StepSegment.MODE_RUNNING, 180), StepSegment(h + 60_000L, h + 120_000L, 1, 100)), edges)
        assertEquals(280, b[1].steps)
        assertEquals(180, b[1].runSteps)
        assertEquals(100, b[1].walkSteps)
    }

    @Test fun outsideRangeIgnoredAndPointSegmentsKept() {
        val b = StepStats.bucket(listOf(StepSegment(-h, -1, 1, 50), StepSegment(5 * h, 6 * h, 1, 50), StepSegment(2 * h, 2 * h, 1, 7)), edges)
        assertEquals(listOf(0, 0, 7, 0), b.map { it.steps })
    }

    @Test fun totalMatchesSum() {
        val segs = (0 until 30).map { StepSegment(it * 7 * 60_000L, it * 7 * 60_000L + 5 * 60_000L, 1, 100 + it) }
        val total = StepStats.sum(StepStats.bucket(segs, edges)).steps
        assertEquals(segs.sumOf { it.steps }.toDouble(), total.toDouble(), 4.0) // per-bucket rounding only
    }
}

class StepArchiveTest {
    @Test fun mergeKeepsHistoryAndTakesGrownRows() {
        val old = listOf(StepSegment(100, 200, 2, 10), StepSegment(300, 400, 2, 20))
        // MIUI dropped row 100, extended row 300 and added row 500
        val fresh = listOf(StepSegment(300, 450, 2, 25), StepSegment(500, 600, 3, 30))
        val (all, changed) = app.plainlauncher.data.StepArchive.merge(old, fresh, cutoff = 0)
        assertEquals(true, changed)
        assertEquals(listOf(10, 25, 30), all.map { it.steps })
    }

    @Test fun unchangedWhenNothingNew() {
        val old = listOf(StepSegment(100, 200, 2, 10))
        val (_, changed) = app.plainlauncher.data.StepArchive.merge(old, old, cutoff = 0)
        assertEquals(false, changed)
    }

    @Test fun dropsRowsOlderThanCutoff() {
        val (all, _) = app.plainlauncher.data.StepArchive.merge(listOf(StepSegment(1, 2, 2, 5), StepSegment(100, 200, 2, 10)), emptyList(), cutoff = 50)
        assertEquals(listOf(10), all.map { it.steps })
    }
}
