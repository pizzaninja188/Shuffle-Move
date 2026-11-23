package shuffle.android.vision

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Reads and writes images; Android uses Bitmap, JVM tests use ImageIO. */
interface ImageCodec {
    fun read(file: File): Pixels?
    fun writePng(pixels: Pixels, file: File)
}

/**
 * Reference examples for each label:
 * - built-in: the Shuffle Move icon drawn onto plain cell backgrounds (works out of the box, roughly);
 * - taught: real cell crops the user confirmed from their own screenshots (much more accurate, since
 *   the game draws each icon the same way every time). Stored as PNGs under dir/label/.
 */
class ReferenceStore(
    private val dir: File,
    private val codec: ImageCodec,
    private val iconFor: (String) -> Pixels?,
) {
    private val builtinCache = ConcurrentHashMap<String, List<Reference>>()
    private val taughtCache = ConcurrentHashMap<String, List<Reference>>()

    /**
     * The label's icon drawn on plain tiles. A label without an icon gets no built-in examples (a blank
     * tile would just compete with empty cells); it is recognized only once taught.
     */
    fun builtin(label: String): List<Reference> = builtinCache.getOrPut(label) {
        val icon = iconFor(label) ?: return@getOrPut emptyList()
        BACKGROUNDS.map { bg -> Reference(label, CellFeature.of(icon.fitInto(CELL, CELL, ICON_SCALE, bg)), taught = false) }
    }

    fun taught(label: String): List<Reference> = taughtCache.getOrPut(label) {
        labelDir(label).listFiles { f -> f.extension == "png" }.orEmpty().sortedBy { it.name }.mapNotNull { f ->
            codec.read(f)?.let { Reference(label, CellFeature.of(it), taught = true) }
        }
    }

    fun taughtCount(label: String): Int = taught(label).size

    /** Saves a confirmed crop as a new example for the label (keeps the newest [MAX_TAUGHT]). */
    fun teach(label: String, crop: Pixels) {
        val d = labelDir(label)
        d.mkdirs()
        codec.writePng(crop.resize(TAUGHT_SIZE, TAUGHT_SIZE), File(d, "${System.currentTimeMillis()}.png"))
        d.listFiles { f -> f.extension == "png" }.orEmpty().sortedByDescending { it.name }.drop(MAX_TAUGHT)
            .forEach { it.delete() }
        taughtCache.remove(label)
    }

    fun forget(label: String) {
        labelDir(label).deleteRecursively()
        taughtCache.remove(label)
    }

    fun classifierFor(labels: Collection<String>): CellClassifier =
        CellClassifier(labels.flatMap { builtin(it) + taught(it) })

    private fun labelDir(label: String) = File(dir, label.replace(Regex("[^A-Za-z0-9_\\-]"), "_"))

    companion object {
        private const val CELL = 96
        private const val TAUGHT_SIZE = 64
        private const val ICON_SCALE = 0.92f
        private const val MAX_TAUGHT = 12
        /** Light board tile and a darker variant; the pattern feature is mostly background-agnostic. */
        private val BACKGROUNDS = listOf(0xFFEDE6D6.toInt(), 0xFF9A9A9A.toInt())
    }
}
