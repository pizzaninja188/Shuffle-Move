package shuffle.android.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import shuffle.android.vision.BoardRect
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Full-screen, touch-through view that draws the suggested move over the game: a ring on the cell
 * to pick up, an arrow to where it should be dropped, and a one-line summary above the board.
 * Coordinates are screen pixels (as captured); the view offsets itself by its own screen position.
 */
@SuppressLint("ViewConstructor")
class MoveArrowView(context: Context) : View(context) {

    data class Hint(
        val rect: BoardRect,
        /** pickup row, col, drop row, col (1-based), or empty for "no move". */
        val move: List<Int>,
        val text: String,
        /** Cells that recognition was unsure about, as (row, col). */
        val unsure: List<Pair<Int, Int>>,
    )

    var hint: Hint? = null
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val loc = IntArray(2)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5 * density
        color = Color.rgb(255, 214, 0)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7 * density
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(255, 64, 64)
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(255, 64, 64)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 11 * density
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(160, 0, 0, 0)
    }
    private val unsurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.rgb(0, 200, 255)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15 * density
        isFakeBoldText = true
    }
    private val textBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 20, 20, 30) }

    override fun onDraw(canvas: Canvas) {
        val h = hint ?: return
        getLocationOnScreen(loc)
        canvas.save()
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
        val r = h.rect
        val cell = r.cellSize

        for ((row, col) in h.unsure) {
            val (cx, cy) = r.cellCenter(row, col)
            val s = cell * 0.42f
            canvas.drawRect(cx - s, cy - s, cx + s, cy + s, unsurePaint)
        }

        if (h.move.size >= 4) {
            val (x1, y1) = r.cellCenter(h.move[0], h.move[1])
            val (x2, y2) = r.cellCenter(h.move[2], h.move[3])
            canvas.drawCircle(x1, y1, cell * 0.42f, ringPaint)
            val angle = atan2(y2 - y1, x2 - x1)
            val len = hypot(x2 - x1, y2 - y1)
            val start = cell * 0.42f
            val head = cell * 0.32f
            val sx = x1 + cos(angle) * start
            val sy = y1 + sin(angle) * start
            val ex = x1 + cos(angle) * (len - head * 0.4f)
            val ey = y1 + sin(angle) * (len - head * 0.4f)
            canvas.drawLine(sx, sy, ex, ey, outlinePaint)
            canvas.drawLine(sx, sy, ex, ey, arrowPaint)
            val tip = Path().apply {
                moveTo(x2 + cos(angle) * head * 0.6f, y2 + sin(angle) * head * 0.6f)
                lineTo(x2 + cos(angle + 2.5f) * head, y2 + sin(angle + 2.5f) * head)
                lineTo(x2 + cos(angle - 2.5f) * head, y2 + sin(angle - 2.5f) * head)
                close()
            }
            canvas.drawPath(tip, headPaint)
        }

        if (h.text.isNotEmpty()) {
            val pad = 8 * density
            val tw = textPaint.measureText(h.text)
            val ty = r.top - pad * 2
            val box = RectF(r.left, ty - textPaint.textSize - pad, r.left + tw + pad * 2, ty + pad)
            canvas.drawRoundRect(box, pad, pad, textBg)
            canvas.drawText(h.text, r.left + pad, ty, textPaint)
        }
        canvas.restore()
    }
}
