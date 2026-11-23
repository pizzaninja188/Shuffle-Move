package shuffle.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import shuffle.android.vision.BoardRect
import shuffle.android.vision.toBitmap
import kotlin.math.abs

/**
 * Shows the last screenshot with the board rectangle on top. Drag inside to move it; drag the
 * bottom-right corner to resize. The board is always square (6x6 equal cells).
 */
@Composable
fun CalibrateScreen(vm: MainViewModel, onDone: () -> Unit) {
    val capture by vm.capture.collectAsState()
    val cap = capture ?: run {
        Column(Modifier.padding(16.dp)) {
            Text("Long-press the overlay bubble in the game first, so there is a screenshot to calibrate on.")
            TextButton(onClick = onDone) { Text("Back") }
        }
        return
    }
    val sw = cap.screen.width.toFloat()
    val sh = cap.screen.height.toFloat()
    val image = remember(cap.screen) { cap.screen.toBitmap().asImageBitmap() }
    var rect by remember(cap.rect) { mutableStateOf(cap.rect) }

    Column(Modifier.padding(12.dp)) {
        Text("Calibrate board position", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Fit the grid exactly over the 6×6 board: drag inside to move, drag the corner dot to resize.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Button(onClick = { vm.saveCalibration(rect); onDone() }) { Text("Save") }
            OutlinedButton(onClick = { rect = BoardRect.defaultFor(cap.screen.width, cap.screen.height) }) {
                Text("Reset")
            }
            TextButton(onClick = onDone) { Text("Cancel") }
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            // Fit the screenshot into the available space, keeping its aspect ratio.
            val boxW = constraints.maxWidth.toFloat()
            val boxH = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else boxW * sh / sw
            val scale = minOf(boxW / sw, boxH / sh)
            Box(Modifier.fillMaxWidth(scale * sw / boxW).aspectRatio(sw / sh)) {
                Image(image, contentDescription = "Screenshot", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                Canvas(
                    Modifier.fillMaxSize().pointerInput(cap.screen) {
                        var resizing = false
                        detectDragGestures(
                            onDragStart = { p ->
                                val k = size.width / sw
                                val cx = (rect.left + rect.size) * k
                                val cy = (rect.top + rect.size) * k
                                resizing = abs(p.x - cx) < 48.dp.toPx() && abs(p.y - cy) < 48.dp.toPx()
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                val k = size.width / sw
                                val dx = drag.x / k
                                val dy = drag.y / k
                                rect = if (resizing) {
                                    rect.copy(size = (rect.size + (dx + dy) / 2).coerceIn(sw * 0.3f, sw))
                                } else {
                                    rect.copy(
                                        left = (rect.left + dx).coerceIn(-rect.size / 2, sw - rect.size / 2),
                                        top = (rect.top + dy).coerceIn(0f, sh - rect.size / 2),
                                    )
                                }
                            },
                        )
                    },
                ) {
                    val k = size.width / sw
                    val l = rect.left * k
                    val t = rect.top * k
                    val s = rect.size * k
                    drawRect(Color(0x33FFD600), Offset(l, t), Size(s, s))
                    for (i in 0..6) {
                        val o = s * i / 6
                        drawLine(Color.Yellow, Offset(l + o, t), Offset(l + o, t + s), strokeWidth = 2f)
                        drawLine(Color.Yellow, Offset(l, t + o), Offset(l + s, t + o), strokeWidth = 2f)
                    }
                    drawRect(Color.Red, Offset(l, t), Size(s, s), style = Stroke(width = 4f))
                    drawCircle(Color.Red, radius = 14.dp.toPx(), center = Offset(l + s, t + s))
                }
            }
        }
    }
}
