package shuffle.android.vision

import kotlin.math.roundToInt

/**
 * Where the 6x6 board is on screen, in screen (capture) pixels. Rows and columns are 1-based to
 * match Shuffle Move's Board.
 */
data class BoardRect(val left: Float, val top: Float, val size: Float) {

    val cellSize: Float get() = size / GRID

    /** Center of a cell in screen pixels. */
    fun cellCenter(row: Int, col: Int): Pair<Float, Float> =
        Pair(left + (col - 0.5f) * cellSize, top + (row - 0.5f) * cellSize)

    /** The cell's pixel box (left, top, width, height), rounded. */
    fun cellBox(row: Int, col: Int): IntArray {
        val x = (left + (col - 1) * cellSize).roundToInt()
        val y = (top + (row - 1) * cellSize).roundToInt()
        val s = cellSize.roundToInt()
        return intArrayOf(x, y, s, s)
    }

    /** @return the 1-based (row, col) under a screen point, or null if outside the board. */
    fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        if (x < left || y < top || x >= left + size || y >= top + size) return null
        return Pair(((y - top) / cellSize).toInt() + 1, ((x - left) / cellSize).toInt() + 1)
    }

    fun encode(): String = "$left,$top,$size"

    companion object {
        const val GRID = 6

        fun decode(s: String?): BoardRect? {
            val parts = s?.split(',')?.mapNotNull { it.toFloatOrNull() } ?: return null
            return if (parts.size == 3 && parts[2] > 0) BoardRect(parts[0], parts[1], parts[2]) else null
        }

        /**
         * A starting guess for portrait phones: the board spans almost the full width and sits in the
         * lower part of the screen. Calibration replaces this.
         */
        fun defaultFor(screenW: Int, screenH: Int): BoardRect {
            val size = screenW * 730f / 750f
            val left = screenW * 10f / 750f
            val bottomMargin = screenH * 0.06f
            return BoardRect(left, screenH - bottomMargin - size, size)
        }
    }
}
