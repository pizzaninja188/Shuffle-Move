package shuffle.android.overlay

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import shuffle.android.vision.Pixels
import shuffle.android.vision.toPixels

/**
 * Mirrors the screen into an ImageReader for as long as the projection lives and hands out the
 * latest frame on demand. Only the newest frame is kept, so idle cost is just the GPU copy.
 */
class ScreenCapturer(
    private val projection: MediaProjection,
    val width: Int,
    val height: Int,
    densityDpi: Int,
) {
    private val thread = HandlerThread("screen-capture").apply { start() }
    private val handler = Handler(thread.looper)
    @SuppressLint("WrongConstant")
    private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
    private var display: VirtualDisplay? = null

    private val lock = Object()
    private var latest: Image? = null
    @Volatile private var frameCount = 0L

    init {
        reader.setOnImageAvailableListener({ r ->
            val img = try { r.acquireLatestImage() } catch (e: IllegalStateException) { null }
            if (img != null) {
                synchronized(lock) {
                    latest?.close()
                    latest = img
                    frameCount++
                }
            }
        }, handler)
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = release()
        }, handler)
        display = projection.createVirtualDisplay(
            "shuffle-move-capture", width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler,
        )
    }

    /**
     * Waits for a frame newer than the moment of the call (so overlays hidden just before are not in
     * it), then returns it. Falls back to the newest available frame after [timeoutMs].
     */
    suspend fun captureFresh(minNewFrames: Int = 2, timeoutMs: Long = 400): Pixels? {
        val start = frameCount
        withTimeoutOrNull(timeoutMs) {
            while (frameCount < start + minNewFrames) delay(16)
        }
        return synchronized(lock) { latest?.let(::toPixels) }
    }

    private fun toPixels(image: Image): Pixels {
        val plane = image.planes[0]
        val rowPixels = plane.rowStride / plane.pixelStride
        val bmp = Bitmap.createBitmap(rowPixels, image.height, Bitmap.Config.ARGB_8888)
        plane.buffer.rewind()
        bmp.copyPixelsFromBuffer(plane.buffer)
        val cropped = if (rowPixels != image.width) Bitmap.createBitmap(bmp, 0, 0, image.width, image.height) else bmp
        return cropped.toPixels()
    }

    fun release() {
        display?.release()
        display = null
        synchronized(lock) {
            latest?.close()
            latest = null
        }
        reader.close()
        thread.quitSafely()
    }
}
