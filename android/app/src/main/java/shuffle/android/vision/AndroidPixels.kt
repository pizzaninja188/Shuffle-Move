package shuffle.android.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream

fun Bitmap.toPixels(): Pixels {
    val src = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
    val px = IntArray(src.width * src.height)
    src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
    return Pixels(src.width, src.height, px)
}

fun Pixels.toBitmap(): Bitmap = Bitmap.createBitmap(argb, 0, width, width, height, Bitmap.Config.ARGB_8888)

fun decodePixels(input: InputStream): Pixels? = input.use { BitmapFactory.decodeStream(it) }?.toPixels()

/** PNG storage for taught references, via Android's Bitmap. */
object BitmapCodec : ImageCodec {
    override fun read(file: File): Pixels? = file.inputStream().let(::decodePixels)

    override fun writePng(pixels: Pixels, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { pixels.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
