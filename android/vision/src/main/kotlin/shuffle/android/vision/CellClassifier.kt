package shuffle.android.vision

import kotlin.math.sqrt

/**
 * A compact appearance descriptor for one board cell.
 *
 * - [pattern]: the cell's inner area downscaled to [GRID]x[GRID] RGB, mean-removed and unit length.
 *   Comparing two patterns with a dot product is normalized cross-correlation, which ignores overall
 *   brightness/contrast changes (screen dimming, flashes, compression).
 * - [color]: mean RGB in 0..1, which separates flat cells (empty, wood) that have no pattern.
 */
class CellFeature(val pattern: FloatArray, val color: FloatArray) {

    fun similarity(other: CellFeature): Float {
        var dot = 0f
        for (i in pattern.indices) dot += pattern[i] * other.pattern[i]
        var d2 = 0f
        for (i in 0 until 3) {
            val d = color[i] - other.color[i]
            d2 += d * d
        }
        val colorSim = 1f - sqrt(d2) / SQRT3
        return PATTERN_WEIGHT * dot + (1f - PATTERN_WEIGHT) * colorSim
    }

    companion object {
        const val GRID = 16
        /** Fraction trimmed from each side: cell frames and frozen borders are not the species. */
        const val MARGIN = 0.12f
        private const val PATTERN_WEIGHT = 0.7f
        private val SQRT3 = sqrt(3f)

        fun of(cell: Pixels): CellFeature {
            val mx = (cell.width * MARGIN).toInt()
            val my = (cell.height * MARGIN).toInt()
            val inner = cell.crop(mx, my, cell.width - 2 * mx, cell.height - 2 * my).resize(GRID, GRID)
            val n = GRID * GRID
            val v = FloatArray(n * 3)
            val color = FloatArray(3)
            for (i in 0 until n) {
                val p = inner.argb[i]
                val r = (p shr 16 and 0xFF) / 255f
                val g = (p shr 8 and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                v[i * 3] = r; v[i * 3 + 1] = g; v[i * 3 + 2] = b
                color[0] += r; color[1] += g; color[2] += b
            }
            for (c in 0 until 3) color[c] /= n
            val mean = (color[0] + color[1] + color[2]) / 3f
            var norm = 0f
            for (i in v.indices) {
                v[i] -= mean
                norm += v[i] * v[i]
            }
            norm = sqrt(norm)
            // A flat cell has no pattern to correlate; leave it zero so only color counts.
            if (norm < 1e-3f) v.fill(0f) else for (i in v.indices) v[i] /= norm
            return CellFeature(v, color)
        }
    }
}

/** One labelled example: a bundled icon or a crop the user taught from a real screenshot. */
data class Reference(val label: String, val feature: CellFeature, val taught: Boolean)

/** [label] is the confident answer or null; [best] is the top candidate either way. */
data class CellMatch(val label: String?, val best: String?, val score: Float, val runnerUp: String?, val margin: Float)

/**
 * Nearest-neighbour classifier over reference features. Each label's score is its best reference's
 * similarity. Cells whose best score is under [minScore] are reported as unknown (label null) rather
 * than guessed, so the user can fix them.
 */
class CellClassifier(references: Collection<Reference>, private val minScore: Float = DEFAULT_MIN_SCORE) {

    private val byLabel: Map<String, List<Reference>> = references.groupBy { it.label }

    val labels: Set<String> get() = byLabel.keys

    fun classify(feature: CellFeature, allowed: Collection<String>? = null): CellMatch {
        var best: String? = null
        var bestScore = -2f
        var second: String? = null
        var secondScore = -2f
        for ((label, refs) in byLabel) {
            if (allowed != null && label !in allowed) continue
            var s = -2f
            for (r in refs) {
                val sim = feature.similarity(r.feature)
                if (sim > s) s = sim
            }
            if (s > bestScore) {
                second = best; secondScore = bestScore
                best = label; bestScore = s
            } else if (s > secondScore) {
                second = label; secondScore = s
            }
        }
        val margin = if (second == null) bestScore else bestScore - secondScore
        return CellMatch(if (bestScore >= minScore) best else null, best, bestScore, second, margin)
    }

    companion object {
        const val DEFAULT_MIN_SCORE = 0.55f
    }
}

/**
 * Frozen (barrier) cells have a bright ice frame around the icon. Absolute brightness is useless
 * (many tile backgrounds are light), so each cell's border is compared with the same border strip
 * of the other cells on the board: a cell is frozen when at least three of its four strips are
 * clearly brighter than the board's median for that strip.
 */
object FrozenDetector {
    private const val SIZE = 32
    private const val TRIM = 1
    private const val STRIP = 3
    /** How much brighter (0..255) than the board median a strip must be. */
    const val DELTA = 28

    /** Mean brightness of the top, bottom, left and right strips just inside the cell edge. */
    fun stripMeans(cell: Pixels): IntArray {
        val p = cell.resize(SIZE, SIZE)
        val lo = TRIM
        val hi = SIZE - TRIM
        fun mean(x0: Int, y0: Int, x1: Int, y1: Int): Int {
            var sum = 0L
            var n = 0
            for (y in y0 until y1) for (x in x0 until x1) {
                val c = p[x, y]
                sum += ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3
                n++
            }
            return (sum / n).toInt()
        }
        return intArrayOf(
            mean(lo, lo, hi, lo + STRIP),
            mean(lo, hi - STRIP, hi, hi),
            mean(lo, lo, lo + STRIP, hi),
            mean(hi - STRIP, lo, hi, hi),
        )
    }

    /** Frozen flags for a set of cells (normally the whole board), judged relative to each other. */
    fun detect(cells: List<Pixels>): BooleanArray {
        val strips = cells.map(::stripMeans)
        val medians = IntArray(4) { k -> strips.map { it[k] }.sorted()[strips.size / 2] }
        return BooleanArray(cells.size) { i -> (0 until 4).count { k -> strips[i][k] >= medians[k] + DELTA } >= 3 }
    }
}
