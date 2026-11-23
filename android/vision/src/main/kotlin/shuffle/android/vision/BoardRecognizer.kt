package shuffle.android.vision

/**
 * What was seen in one cell. [label] is null when recognition was not confident; [guess] is the best
 * candidate regardless (used for solving, flagged to the user as unsure).
 */
data class CellReading(
    val row: Int,
    val col: Int,
    val label: String?,
    val guess: String?,
    val score: Float,
    val frozen: Boolean,
    val crop: Pixels,
)

class BoardReading(val cells: List<CellReading>) {
    operator fun get(row: Int, col: Int): CellReading = cells[(row - 1) * BoardRect.GRID + (col - 1)]
    val unknownCount: Int get() = cells.count { it.label == null }
}

/**
 * Cuts the board out of a screenshot and classifies every cell.
 *
 * @param labelsCanFreeze labels for which frozen detection applies (species; not blocks or empty).
 */
class BoardRecognizer(
    private val classifier: CellClassifier,
    private val labelsCanFreeze: (String) -> Boolean,
) {
    fun read(screen: Pixels, rect: BoardRect, allowed: Collection<String>? = null): BoardReading {
        val crops = ArrayList<Pixels>(BoardRect.GRID * BoardRect.GRID)
        for (row in 1..BoardRect.GRID) {
            for (col in 1..BoardRect.GRID) {
                val box = rect.cellBox(row, col)
                crops += screen.crop(box[0], box[1], box[2], box[3])
            }
        }
        val frozen = FrozenDetector.detect(crops)
        val cells = crops.mapIndexed { i, crop ->
            val match = classifier.classify(CellFeature.of(crop), allowed)
            val canFreeze = match.best != null && labelsCanFreeze(match.best)
            CellReading(i / BoardRect.GRID + 1, i % BoardRect.GRID + 1, match.label, match.best, match.score,
                canFreeze && frozen[i], crop)
        }
        return BoardReading(cells)
    }
}
