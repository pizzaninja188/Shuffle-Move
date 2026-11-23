package shuffle.android.vision

/**
 * An ARGB image as a plain int array (row-major), so the recognition code is pure Kotlin and runs
 * in JVM unit tests as well as on the device.
 */
class Pixels(val width: Int, val height: Int, val argb: IntArray) {

    init {
        require(width > 0 && height > 0) { "empty image ${width}x$height" }
        require(argb.size >= width * height) { "pixel array too small" }
    }

    operator fun get(x: Int, y: Int): Int = argb[y * width + x]

    /** Copies the given region, clamped to the image. */
    fun crop(left: Int, top: Int, w: Int, h: Int): Pixels {
        val l = left.coerceIn(0, width - 1)
        val t = top.coerceIn(0, height - 1)
        val cw = w.coerceIn(1, width - l)
        val ch = h.coerceIn(1, height - t)
        val out = IntArray(cw * ch)
        for (y in 0 until ch) {
            System.arraycopy(argb, (t + y) * width + l, out, y * cw, cw)
        }
        return Pixels(cw, ch, out)
    }

    /** Box-filter (area average) resize: smooth, and stable against small shifts and scaling. */
    fun resize(newW: Int, newH: Int): Pixels {
        val out = IntArray(newW * newH)
        for (oy in 0 until newH) {
            val y0 = oy * height / newH
            val y1 = maxOf(y0 + 1, (oy + 1) * height / newH)
            for (ox in 0 until newW) {
                val x0 = ox * width / newW
                val x1 = maxOf(x0 + 1, (ox + 1) * width / newW)
                var a = 0L; var r = 0L; var g = 0L; var b = 0L
                for (y in y0 until y1) {
                    val row = y * width
                    for (x in x0 until x1) {
                        val p = argb[row + x]
                        a += p ushr 24 and 0xFF
                        r += p shr 16 and 0xFF
                        g += p shr 8 and 0xFF
                        b += p and 0xFF
                    }
                }
                val n = (y1 - y0) * (x1 - x0)
                out[oy * newW + ox] =
                    ((a / n).toInt() shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
            }
        }
        return Pixels(newW, newH, out)
    }

    /** Composites this (possibly transparent) image over an opaque background color. */
    fun over(backgroundArgb: Int): Pixels {
        val br = backgroundArgb shr 16 and 0xFF
        val bg = backgroundArgb shr 8 and 0xFF
        val bb = backgroundArgb and 0xFF
        val out = IntArray(width * height)
        for (i in out.indices) {
            val p = argb[i]
            val a = p ushr 24 and 0xFF
            val r = ((p shr 16 and 0xFF) * a + br * (255 - a)) / 255
            val g = ((p shr 8 and 0xFF) * a + bg * (255 - a)) / 255
            val b = ((p and 0xFF) * a + bb * (255 - a)) / 255
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Pixels(width, height, out)
    }

    /** Scales this image to fit inside a box of the given size, centered on a background. */
    fun fitInto(boxW: Int, boxH: Int, scale: Float, backgroundArgb: Int): Pixels {
        val w = maxOf(1, (boxW * scale).toInt())
        val h = maxOf(1, (boxH * scale).toInt())
        val scaled = resize(w, h).over(backgroundArgb)
        val out = IntArray(boxW * boxH) { backgroundArgb or (0xFF shl 24) }
        val ox = (boxW - w) / 2
        val oy = (boxH - h) / 2
        for (y in 0 until h) {
            System.arraycopy(scaled.argb, y * w, out, (oy + y) * boxW + ox, w)
        }
        return Pixels(boxW, boxH, out)
    }

    companion object {
        fun solid(w: Int, h: Int, argb: Int) = Pixels(w, h, IntArray(w * h) { argb })
    }
}
