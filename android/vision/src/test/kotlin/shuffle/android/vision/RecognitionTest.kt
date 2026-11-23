package shuffle.android.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.imageio.ImageIO

/**
 * Recognition on synthetic screenshots built from Shuffle Move's real icons (bundled in :core), drawn
 * at a different size, brightness and background than the references, like a real phone would.
 * Runs on the JVM: ImageIO stands in for Android's BitmapFactory.
 */
class RecognitionTest {

    private val team = listOf("Audino", "Pikachu", "Squirtle", "Charmander")
    private val labels = team + listOf("Mega_Audino", "Air", "Wood", "Metal", "Coin")
    private val iconPaths = mapOf(
        "Audino" to "img/icons/531.png",
        "Mega_Audino" to "img/icons/531-m.png",
        "Pikachu" to "img/icons/025.png",
        "Squirtle" to "img/icons/007.png",
        "Charmander" to "img/icons/004.png",
        "Air" to "img/icons/air.png",
        "Wood" to "img/icons/woodIc.png",
        "Metal" to "img/icons/metalIc.png",
        "Coin" to "img/icons/coin.png",
    )

    private fun icon(label: String): Pixels? {
        val path = iconPaths[label] ?: return null
        val stream = javaClass.classLoader!!.getResourceAsStream(path) ?: return null
        val img = ImageIO.read(stream) ?: return null
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return Pixels(img.width, img.height, px)
    }

    private object ImageIoCodec : ImageCodec {
        override fun read(file: java.io.File): Pixels? = ImageIO.read(file)?.let { img ->
            val px = IntArray(img.width * img.height)
            img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
            Pixels(img.width, img.height, px)
        }

        override fun writePng(pixels: Pixels, file: java.io.File) {
            val img = java.awt.image.BufferedImage(pixels.width, pixels.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            img.setRGB(0, 0, pixels.width, pixels.height, pixels.argb, 0, pixels.width)
            ImageIO.write(img, "png", file)
        }
    }

    private fun store() = ReferenceStore(createTempDir(), ImageIoCodec, ::icon)

    /** Draws a fake phone screen with the given 6x6 labels inside [rect]. */
    private fun screen(grid: List<String>, rect: BoardRect, w: Int, h: Int, bg: Int, iconScale: Float, dim: Float): Pixels {
        val out = IntArray(w * h) { 0xFF203040.toInt() }
        for (row in 1..6) for (col in 1..6) {
            val label = grid[(row - 1) * 6 + (col - 1)]
            val box = rect.cellBox(row, col)
            val s = box[2]
            val cell = icon(label)?.fitInto(s, s, iconScale, bg) ?: Pixels.solid(s, s, bg)
            for (y in 0 until s) for (x in 0 until s) {
                val sx = box[0] + x
                val sy = box[1] + y
                if (sx in 0 until w && sy in 0 until h) out[sy * w + sx] = dimmed(cell[x, y], dim)
            }
        }
        return Pixels(w, h, out)
    }

    private fun dimmed(p: Int, f: Float): Int {
        val r = ((p shr 16 and 0xFF) * f).toInt().coerceIn(0, 255)
        val g = ((p shr 8 and 0xFF) * f).toInt().coerceIn(0, 255)
        val b = ((p and 0xFF) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun randomGrid(seed: Long): List<String> {
        val rnd = java.util.Random(seed)
        return List(36) { labels[rnd.nextInt(labels.size)] }
    }

    @Test
    fun recognizesBuiltInIconsOnSyntheticScreens() {
        val recognizer = BoardRecognizer(store().classifierFor(labels)) { it !in listOf("Air", "Wood", "Metal", "Coin") }
        var correct = 0
        var total = 0
        for (seed in 1L..5L) {
            val w = 1080
            val h = 2340
            val rect = BoardRect(14f, 1310f, 1050f)
            val grid = randomGrid(seed)
            // Different tile color, icon size and brightness than the references were built with.
            val shot = screen(grid, rect, w, h, bg = 0xFFE4DCC8.toInt(), iconScale = 0.86f, dim = 0.9f)
            val reading = recognizer.read(shot, rect)
            for (c in reading.cells) {
                total++
                if (c.guess == grid[(c.row - 1) * 6 + (c.col - 1)]) correct++
            }
        }
        val accuracy = correct.toFloat() / total
        assertTrue("accuracy $accuracy", accuracy >= 0.95f)
    }

    @Test
    fun taughtCropsWin() {
        val store = store()
        // A "game rendering" of Pikachu that differs a lot from the icon: heavy tint and crop.
        val gameCell = icon("Pikachu")!!.crop(10, 10, 40, 40).fitInto(80, 80, 1f, 0xFF808080.toInt())
            .let { p -> Pixels(p.width, p.height, IntArray(p.argb.size) { i -> dimmed(p.argb[i], 0.6f) }) }
        store.teach("Pikachu", gameCell)
        val match = store.classifierFor(labels).classify(CellFeature.of(gameCell))
        assertEquals("Pikachu", match.label)
        assertTrue(match.score > 0.95f)
    }

    @Test
    fun unsureBelowThreshold() {
        val classifier = CellClassifier(
            listOf(Reference("A", CellFeature.of(stripes(true)), false)),
            minScore = 0.9f,
        )
        val m = classifier.classify(CellFeature.of(stripes(false)))
        assertNull("orthogonal pattern should not be confident", m.label)
        assertEquals("A", m.best)
    }

    @Test
    fun frozenJudgedRelativeToBoard() {
        // Light tiles everywhere (bright borders are normal), two cells with a whiter ice frame.
        val tile = 0xFFE0D8C4.toInt()
        val icon = 0xFF705030.toInt()
        fun cell(ice: Boolean) = Pixels(60, 60, IntArray(60 * 60) { i ->
            val x = i % 60
            val y = i / 60
            val edge = x < 7 || y < 7 || x >= 53 || y >= 53
            when {
                edge && ice -> 0xFFFFFFFF.toInt()
                edge -> tile
                else -> icon
            }
        })
        val cells = List(36) { cell(ice = it == 5 || it == 20) }
        val flags = FrozenDetector.detect(cells)
        assertEquals(listOf(5, 20), flags.indices.filter { flags[it] })
        // A board of plain light tiles has nothing frozen.
        assertFalse(FrozenDetector.detect(List(36) { cell(false) }).any { it })
    }

    @Test
    fun boardRectGeometry() {
        val r = BoardRect(100f, 200f, 600f)
        assertEquals(Pair(150f, 250f), r.cellCenter(1, 1))
        assertEquals(Pair(650f, 750f), r.cellCenter(6, 6))
        assertEquals(Pair(2, 3), r.cellAt(310f, 330f))
        assertNull(r.cellAt(99f, 300f))
        val decoded = BoardRect.decode(r.encode())
        assertNotNull(decoded)
        assertEquals(r, decoded)
    }

    private fun stripes(vertical: Boolean): Pixels {
        val n = 64
        return Pixels(n, n, IntArray(n * n) { i ->
            val v = if (vertical) (i % n) / 8 % 2 else (i / n) / 8 % 2
            if (v == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        })
    }

    @Suppress("DEPRECATION")
    private fun createTempDir() = kotlin.io.createTempDir("refs")
}
