package app.plainlauncher

import app.plainlauncher.data.RecorderMath
import app.plainlauncher.data.RecorderMath.Last
import app.plainlauncher.data.RecorderMath.Piece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecorderMathTest {
    private val boot = 1_000_000_000L
    private val m = 60_000L

    @Test fun firstReadingIsOnlyABaseline() {
        val (last, piece) = RecorderMath.next(null, 5000, boot + 10 * m, boot)
        assertNull(piece)
        assertEquals(5000L, last.raw)
    }

    @Test fun stepsBetweenReadingsEndAtTheReading() {
        val (_, p) = RecorderMath.next(Last(100, boot + m, boot), 160, boot + 2 * m, boot)
        assertEquals(Piece(boot + 2 * m - 60_000, boot + 2 * m, 60), p)
    }

    @Test fun lateBatchIsNotSmearedOverHours() {
        // 600 steps arrive 3 hours after the last reading: assume ~1 step/s, so a 10-minute stretch.
        val (_, p) = RecorderMath.next(Last(100, boot, boot), 700, boot + 180 * m, boot)
        assertEquals(boot + 170 * m, p!!.begin)
        assertEquals(600, p.steps)
    }

    @Test fun rebootCountsStepsSinceBoot() {
        val newBoot = boot + 24 * 60 * m
        val (last, p) = RecorderMath.next(Last(9000, boot + 60 * m, boot), 120, newBoot + 30 * m, newBoot)
        assertEquals(120, p!!.steps)
        assertEquals(newBoot + 30 * m - 120_000, p.begin)
        assertEquals(newBoot, last.bootWall)
    }

    @Test fun counterGoingBackwardsAlsoMeansReboot() {
        val (_, p) = RecorderMath.next(Last(9000, boot + m, boot), 50, boot + 2 * m, boot + 5_000)
        assertEquals(50, p!!.steps)
    }

    @Test fun noNewStepsNoPiece() {
        assertNull(RecorderMath.next(Last(100, boot, boot), 100, boot + m, boot).second)
    }

    @Test fun nearbyPiecesJoinFarOnesSplit() {
        val a = Piece(0, 30_000, 40)
        val (j, done) = RecorderMath.join(a, Piece(50_000, 80_000, 30))
        assertNull(done)
        assertEquals(Piece(0, 80_000, 70), j)
        val (j2, done2) = RecorderMath.join(j, Piece(10 * m, 10 * m + 5_000, 8))
        assertEquals(j, done2)
        assertEquals(Piece(10 * m, 10 * m + 5_000, 8), j2)
    }
}
