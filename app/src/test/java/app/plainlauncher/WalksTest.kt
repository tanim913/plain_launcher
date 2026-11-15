package app.plainlauncher

import app.plainlauncher.data.StepSegment
import app.plainlauncher.data.Walks
import org.junit.Assert.assertEquals
import org.junit.Test

class WalksTest {
    private val m = 60_000L
    private val now = 1000 * m

    @Test fun shortPausesJoinOneWalk() {
        val segs = listOf(StepSegment(0, 3 * m, 2, 300), StepSegment(4 * m, 8 * m, 2, 400), StepSegment(9 * m, 12 * m, 2, 300))
        val w = Walks.group(segs, 5 * m, now)
        assertEquals(1, w.size)
        assertEquals(0L, w[0].begin); assertEquals(12 * m, w[0].end); assertEquals(1000, w[0].steps)
    }

    @Test fun longGapSplitsAndShortWalksDropped() {
        val segs = listOf(StepSegment(0, 6 * m, 2, 600), StepSegment(30 * m, 32 * m, 2, 150))
        val w = Walks.group(segs, 5 * m, now)
        assertEquals(listOf(600), w.map { it.steps })
        assertEquals(2, Walks.group(segs, 1 * m, now).size)
    }

    @Test fun walkStillGoingIsHeldBack() {
        val segs = listOf(StepSegment(now - 20 * m, now - 2 * m, 2, 1500))
        assertEquals(0, Walks.group(segs, 5 * m, now).size)
        assertEquals(1, Walks.group(segs, 5 * m, now + 20 * m).size)
    }

    @Test fun mostlyRunningIsARun() {
        val segs = listOf(StepSegment(0, 5 * m, 3, 800), StepSegment(5 * m, 7 * m, 2, 200))
        assertEquals(true, Walks.group(segs, 5 * m, now)[0].running)
    }
}

class WalkFilterTest {
    private val m = 60_000L
    @Test fun hourlyTotalsAndCrawlsAreNotWalks() {
        assertEquals(false, Walks.isWalking(StepSegment(0, 60 * m, StepSegment.MODE_TOTAL, 3000)))
        assertEquals(false, Walks.isWalking(StepSegment(0, 120 * m, 2, 2000)))   // 17 steps/min over 2 h
        assertEquals(true, Walks.isWalking(StepSegment(0, 20 * m, 2, 2000)))     // 100 steps/min
        assertEquals(true, Walks.isWalking(StepSegment(0, 3 * m, 2, 50)))        // short bits always count
    }
}
