package app.plainlauncher

import app.plainlauncher.data.GridModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridModelTest {
    @Test fun emptyGrid_everyCellFree_andBoundsRespected() {
        val g = GridModel(4, 5)
        assertTrue(g.isFree(0, 0, 1, 1))
        assertTrue(g.isFree(3, 4, 1, 1))
        assertFalse(g.isFree(4, 0, 1, 1))
        assertFalse(g.isFree(3, 0, 2, 1))
        assertFalse(g.isFree(0, 4, 1, 2))
        assertFalse(g.isFree(-1, 0, 1, 1))
    }

    @Test fun occupy_blocksSpannedCells() {
        val g = GridModel(4, 5)
        g.occupy(1, 1, 2, 2)
        assertFalse(g.isFree(2, 2, 1, 1))
        assertFalse(g.isFree(0, 1, 2, 1))
        assertTrue(g.isFree(3, 1, 1, 1))
        assertTrue(g.isFree(0, 0, 4, 1))
    }

    @Test fun nearestFree_prefersWantedCellThenClosest() {
        val g = GridModel(4, 5)
        assertArrayEquals(intArrayOf(2, 2), g.nearestFree(2, 2, 1, 1))
        g.occupy(2, 2, 1, 1)
        val n = g.nearestFree(2, 2, 1, 1)!!
        assertEquals(1, Math.abs(n[0] - 2) + Math.abs(n[1] - 2))
    }

    @Test fun nearestFree_respectsSpan_andReturnsNullWhenFull() {
        val g = GridModel(4, 2)
        g.occupy(0, 0, 1, 1)
        g.occupy(0, 1, 1, 1)
        assertArrayEquals(intArrayOf(1, 0), g.nearestFree(0, 0, 3, 2))
        assertNull(g.nearestFree(0, 0, 4, 1))
        val full = GridModel(2, 2).also { it.occupy(0, 0, 2, 2) }
        assertNull(full.firstFree(1, 1))
    }
}
