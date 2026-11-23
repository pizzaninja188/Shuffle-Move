package shuffle.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import shuffle.android.repo

/** A species/block icon from Shuffle Move's bundled art, or its initials if it has none. */
@Composable
fun SpeciesIcon(name: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp = remember(name) { context.repo.iconBitmap(name) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = name, modifier = Modifier.size(size))
        } else {
            Text(name.take(3), fontSize = 10.sp, maxLines = 1)
        }
    }
}

/** Searchable list dialog for picking a species (or any label). */
@Composable
fun PickerDialog(
    title: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    searchable: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, options) {
        if (query.isBlank()) options else options.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (searchable) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(shown.take(300)) { name ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(name) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SpeciesIcon(name, 36.dp)
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A small "− value +" control. */
@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        Text("$value", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
    }
}

/** A selectable chip showing an icon, used for the paint palette. */
@Composable
fun IconChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(2.dp)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                RoundedCornerShape(8.dp),
            )
            .background(Color(0xFFF4F0E6), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        SpeciesIcon(name, 40.dp)
    }
}
