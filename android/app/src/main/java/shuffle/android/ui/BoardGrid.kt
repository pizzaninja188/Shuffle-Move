package shuffle.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** What to draw in one grid cell. */
data class GridCell(val label: String?, val frozen: Boolean, val unsure: Boolean)

/**
 * A 6x6 board of icons with the suggested move drawn on top. Tap / long-press report 1-based
 * (row, col).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoardGrid(
    cell: (row: Int, col: Int) -> GridCell,
    move: List<Int>?,
    onTap: (Int, Int) -> Unit,
    onLongPress: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val cellSize = maxWidth / 6
        Column(
            Modifier.fillMaxSize().drawWithContent {
                drawContent()
                if (move != null && move.size >= 4) {
                    val c = size.width / 6
                    val a = Offset((move[1] - 0.5f) * c, (move[0] - 0.5f) * c)
                    val b = Offset((move[3] - 0.5f) * c, (move[2] - 0.5f) * c)
                    drawCircle(Color(0xFFFFD600), radius = c * 0.42f, center = a, style = Stroke(width = c * 0.08f))
                    drawLine(Color(0xAA000000), a, b, strokeWidth = c * 0.16f, cap = StrokeCap.Round)
                    drawLine(Color(0xFFFF4040), a, b, strokeWidth = c * 0.1f, cap = StrokeCap.Round)
                    drawCircle(Color(0xFFFF4040), radius = c * 0.14f, center = b)
                }
            },
        ) {
            for (row in 1..6) {
                Row {
                    for (col in 1..6) {
                        val g = cell(row, col)
                        Box(
                            Modifier
                                .size(cellSize)
                                .background(if (g.frozen) Color(0xFFBDE7FF) else Color(0xFFF4F0E6))
                                .border(
                                    if (g.unsure) 2.dp else 0.5.dp,
                                    if (g.unsure) Color(0xFF00B0FF) else Color(0x33000000),
                                )
                                .combinedClickable(
                                    onClick = { onTap(row, col) },
                                    onLongClick = { onLongPress(row, col) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (g.label != null) SpeciesIcon(g.label, cellSize * 0.86f)
                        }
                    }
                }
            }
        }
    }
}
