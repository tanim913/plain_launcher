package app.plainlauncher.data

/** Occupancy grid for one home page. Pure Kotlin so the placement rules unit-test on the JVM. */
class GridModel(val cols: Int, val rows: Int) {
    private val occ = Array(rows) { BooleanArray(cols) }

    fun occupy(x: Int, y: Int, sx: Int, sy: Int) {
        for (yy in maxOf(0, y) until minOf(rows, y + sy)) for (xx in maxOf(0, x) until minOf(cols, x + sx)) occ[yy][xx] = true
    }

    fun isFree(x: Int, y: Int, sx: Int, sy: Int): Boolean {
        if (sx < 1 || sy < 1 || x < 0 || y < 0 || x + sx > cols || y + sy > rows) return false
        for (yy in y until y + sy) for (xx in x until x + sx) if (occ[yy][xx]) return false
        return true
    }

    /** Free origin closest (Euclidean) to the wanted cell, or null if nothing fits. Ties go to the earlier row/column. */
    fun nearestFree(x: Int, y: Int, sx: Int, sy: Int): IntArray? {
        var best: IntArray? = null
        var bestD = Int.MAX_VALUE
        for (yy in 0..rows - sy) for (xx in 0..cols - sx) {
            if (!isFree(xx, yy, sx, sy)) continue
            val d = (xx - x) * (xx - x) + (yy - y) * (yy - y)
            if (d < bestD) { bestD = d; best = intArrayOf(xx, yy) }
        }
        return best
    }

    fun firstFree(sx: Int, sy: Int): IntArray? = nearestFree(0, 0, sx, sy)
}
