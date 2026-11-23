package shuffle.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import shuffle.android.Labels
import shuffle.android.SolveOutcome
import shuffle.android.TeamSetup
import shuffle.android.vision.toBitmap
import kotlin.math.roundToInt

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

// ---- Home --------------------------------------------------------------------------------------

@Composable
fun HomeScreen(
    vm: MainViewModel,
    overlayRunning: Boolean,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onEditTeam: () -> Unit,
    onPractice: () -> Unit,
    onReview: () -> Unit,
) {
    val setup by vm.setup.collectAsState()
    val capture by vm.capture.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Shuffle Move", style = MaterialTheme.typography.headlineMedium)
        Text("Best-move overlay for Pokémon Shuffle", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))

        Section("Overlay") {
            Text(
                if (overlayRunning) {
                    "Running. In the game, tap the bubble to see the best move. Long-press it to review what was " +
                        "recognized, fix cells and calibrate."
                } else {
                    "Starts a floating bubble over the game. Android will ask to allow drawing over other apps and " +
                        "to record the screen (used only to read the board, on this phone)."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            if (overlayRunning) {
                OutlinedButton(onClick = onStopOverlay) { Text("Stop overlay") }
            } else {
                Button(onClick = onStartOverlay, enabled = !setup.loading) { Text("Start overlay") }
            }
        }

        Section("Stage & team") {
            if (setup.loading) {
                Text("Loading Shuffle Move data…")
            } else {
                Text(setup.stage?.title ?: "-", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.padding(vertical = 6.dp)) {
                    setup.team.names.forEach { n ->
                        SpeciesIcon(if (n == setup.team.megaSlot) setup.team.megaLabel ?: n else n, 44.dp)
                    }
                    if (setup.team.names.isEmpty()) Text("No team yet", style = MaterialTheme.typography.bodySmall)
                }
                if (setup.team.disruptions.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        Text("Disruptions:", style = MaterialTheme.typography.bodySmall)
                        setup.team.disruptions.forEach { SpeciesIcon(it, 32.dp) }
                    }
                }
                Button(onClick = onEditTeam) { Text("Change stage & team") }
            }
        }

        Section("Last capture") {
            val cap = capture
            if (cap == null) {
                Text("Nothing captured yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    "${cap.reading.unknownCount} unsure cell(s). Review to correct them; corrections teach the " +
                        "recognizer.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onReview) { Text("Review capture") }
            }
        }

        Section("Practice board") {
            Text("Enter a board by hand and solve it (no capture needed).", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onPractice, enabled = !setup.loading) { Text("Open board editor") }
        }

        SettingsSection(vm)
    }
}

@Composable
private fun SettingsSection(vm: MainViewModel) {
    val repo = vm.repo
    var mobile by remember { mutableStateOf(repo.mobileMode) }
    var feeders by remember { mutableFloatStateOf(repo.numFeeders.toFloat()) }
    var seconds by remember { mutableFloatStateOf(repo.timeLimitMs / 1000f) }
    var moves by remember { mutableIntStateOf(repo.movesLeft) }
    var mega by remember { mutableIntStateOf(repo.megaProgress) }
    Section("Settings") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mobile game data (off = 3DS)", Modifier.weight(1f))
            Switch(checked = mobile, onCheckedChange = { mobile = it; vm.setMobileMode(it) })
        }
        Text("Accuracy: ${feeders.roundToInt()} skyfall samples per move", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = feeders, onValueChange = { feeders = it }, valueRange = 5f..200f,
            onValueChangeFinished = { repo.numFeeders = feeders.roundToInt() },
        )
        Text("Time limit: ${"%.1f".format(seconds)} s", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = seconds, onValueChange = { seconds = it }, valueRange = 1f..15f,
            onValueChangeFinished = { repo.timeLimitMs = (seconds * 1000).toLong() },
        )
        Stepper("Moves left (0 = stage default)", moves, 0..99) { moves = it; repo.movesLeft = it }
        Stepper("Mega progress", mega, 0..99) { mega = it; repo.megaProgress = it }
    }
}

// ---- Stage & team ------------------------------------------------------------------------------

@Composable
fun SetupScreen(vm: MainViewModel, onDone: () -> Unit) {
    val setup by vm.setup.collectAsState()
    val stages by vm.stages.collectAsState()
    val species by vm.species.collectAsState()
    var query by remember { mutableStateOf("") }
    var pickSlot by remember { mutableStateOf<Int?>(null) }
    var pickDisruption by remember { mutableStateOf(false) }
    val team = setup.team

    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Stage & team", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done") }
        }
        Text("Stage: ${setup.stage?.title ?: "-"}", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Find stage (number or Pokémon)") }, modifier = Modifier.fillMaxWidth(),
        )
        if (query.isNotBlank()) {
            val q = query.trim()
            LazyColumn(Modifier.height(200.dp)) {
                items(stages.filter { it.id.contains(q, true) || it.title.contains(q, true) }.take(100)) { s ->
                    Text(
                        s.title,
                        Modifier.fillMaxWidth().clickable { vm.selectStage(s.id); query = "" }.padding(8.dp),
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        Text("Team (saved for this stage)", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            for (slot in 0 until 4) {
                val name = team.names.getOrNull(slot)
                val info = setup.members.firstOrNull { it.name == name }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (name != null) SpeciesIcon(name, 44.dp) else Spacer(Modifier.width(44.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        TextButton(onClick = { pickSlot = slot }) { Text(name ?: "+ Add Pokémon") }
                        if (info != null) {
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                Stepper("Lv", info.level, 1..30) { vm.setLevel(info.name, it) }
                                Stepper("SL", info.skillLevel, 1..5) { vm.setSkillLevel(info.name, it) }
                            }
                            if (info.hasMega) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = team.megaSlot == name,
                                        onClick = { vm.setTeam(team.copy(megaSlot = name)) },
                                    )
                                    Text("Mega slot", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (name != null) {
                        TextButton(onClick = {
                            val names = team.names.toMutableList().also { it.removeAt(slot) }
                            vm.setTeam(team.copy(names = names))
                        }) { Text("Remove") }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text("Disruptions (saved for this stage)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pokémon the stage adds to your board. They are recognized and can fall in. Like Shuffle Move's " +
                    "non-support Pokémon, they don't count toward your team's types, and skills that remove " +
                    "non-support Pokémon (e.g. Eject) target them. Wood, Metal, Coin and empty cells are always " +
                    "recognized.",
                style = MaterialTheme.typography.bodySmall,
            )
            team.disruptions.forEach { name ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpeciesIcon(name, 40.dp)
                    Text(name, Modifier.weight(1f).padding(start = 8.dp))
                    TextButton(onClick = { vm.removeDisruption(name) }) { Text("Remove") }
                }
            }
            TextButton(onClick = { pickDisruption = true }) { Text("+ Add disruption") }
        }
    }

    if (pickDisruption) {
        PickerDialog(
            title = "Add disruption",
            options = species.filter { it !in team.names && it !in team.disruptions },
            onDismiss = { pickDisruption = false },
            onPick = { picked ->
                vm.addDisruption(picked)
                pickDisruption = false
            },
        )
    }

    val slot = pickSlot
    if (slot != null) {
        PickerDialog(
            title = "Choose Pokémon",
            options = species,
            onDismiss = { pickSlot = null },
            onPick = { picked ->
                val names = team.names.toMutableList()
                if (slot < names.size) names[slot] = picked else names += picked
                vm.setTeam(team.copy(names = names.distinct(), disruptions = team.disruptions - picked))
                pickSlot = null
            },
        )
    }
}

// ---- Practice board ----------------------------------------------------------------------------

@Composable
fun PracticeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val board by vm.board.collectAsState()
    val outcome by vm.outcome.collectAsState()
    val busy by vm.busy.collectAsState()
    val capture by vm.capture.collectAsState()
    val labels = remember(vm.setup.collectAsState().value) { vm.recognitionLabels() }
    var paint by remember { mutableStateOf(labels.firstOrNull() ?: "Air") }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (vm.board.value == null) vm.loadDefaultBoard()
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Board editor", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("Tap to paint, long-press to freeze.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            labels.forEach { l -> IconChip(l, l == paint) { paint = l } }
        }
        val b = board
        BoardGrid(
            cell = { r, c ->
                val s = b?.getSpeciesAt(r, c)
                GridCell(s?.name, b?.isFrozenAt(r, c) == true, unsure = false)
            },
            move = outcome?.best?.move,
            onTap = { r, c -> vm.paint(r, c, paint) },
            onLongPress = { r, c -> vm.toggleFrozen(r, c) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { vm.solveBoard() }, enabled = !busy && b != null) { Text(if (busy) "Solving…" else "Solve") }
            OutlinedButton(onClick = { vm.loadDefaultBoard() }) { Text("Stage start") }
            if (capture != null) OutlinedButton(onClick = { vm.loadCaptureBoard() }) { Text("Last capture") }
        }
        ResultsList(outcome)
    }
}

@Composable
fun ResultsList(outcome: SolveOutcome?) {
    if (outcome == null) return
    Text(
        "Solved in ${outcome.millis} ms with ${outcome.feedersUsed} samples" + if (outcome.timedOut) " (time limit hit)" else "",
        style = MaterialTheme.typography.bodySmall,
    )
    if (outcome.results.isEmpty()) Text("No moves found.")
    outcome.results.take(5).forEachIndexed { i, r ->
        val m = r.move
        val moveText = if (m.size >= 4) "(${m[0]},${m[1]}) → (${m[2]},${m[3]})" else "settle"
        Text(
            "${i + 1}. $moveText  score ≈${r.netScore.average.roundToInt()}  combos ${"%.1f".format(r.combosCleared.average)}" +
                "  disruptions ${"%.1f".format(r.disruptionsCleared.average)}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ---- Capture review ----------------------------------------------------------------------------

@Composable
fun ReviewScreen(vm: MainViewModel, onBack: () -> Unit, onCalibrate: () -> Unit) {
    val capture by vm.capture.collectAsState()
    val busy by vm.busy.collectAsState()
    val setup by vm.setup.collectAsState()
    val cap = capture
    var fixing by remember { mutableStateOf<shuffle.android.vision.CellReading?>(null) }
    var fixingOther by remember { mutableStateOf<shuffle.android.vision.CellReading?>(null) }
    val species by vm.species.collectAsState()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Review capture", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back") }
        }
        if (cap == null) {
            Text("Nothing captured yet. Long-press the overlay bubble in the game.")
            return@Column
        }
        Text(
            "Top: what the phone saw. Bottom: what was recognized. Tap a wrong cell to correct it (this teaches " +
                "the recognizer); long-press to toggle frozen.",
            style = MaterialTheme.typography.bodySmall,
        )
        val crop = remember(cap.screen, cap.rect) {
            val r = cap.rect
            cap.screen.crop(r.left.roundToInt(), r.top.roundToInt(), r.size.roundToInt(), r.size.roundToInt())
                .toBitmap().asImageBitmap()
        }
        Image(crop, contentDescription = "Captured board", modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        BoardGrid(
            cell = { r, c ->
                val cell = cap.reading[r, c]
                GridCell(cell.guess, cell.frozen, unsure = cell.label == null)
            },
            move = cap.outcome?.best?.move,
            onTap = { r, c -> fixing = cap.reading[r, c] },
            onLongPress = { r, c -> vm.toggleCaptureFrozen(cap.reading[r, c]) },
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { vm.solveCapture() }, enabled = !busy) { Text(if (busy) "Solving…" else "Solve") }
            OutlinedButton(onClick = onCalibrate) { Text("Calibrate position") }
            OutlinedButton(onClick = { vm.teachAllConfident() }) { Text("Confirm all") }
            OutlinedButton(onClick = { vm.rereadCapture() }) { Text("Re-read") }
        }
        ResultsList(cap.outcome)
    }

    val cell = fixing
    if (cell != null) {
        PickerDialog(
            title = "Cell (${cell.row}, ${cell.col}) is…",
            options = setup.team.labels + OTHER_SPECIES,
            searchable = false,
            onDismiss = { fixing = null },
            onPick = { label ->
                if (label == OTHER_SPECIES) fixingOther = cell else vm.correctCell(cell, label)
                fixing = null
            },
        )
    }

    // A species the recognizer wasn't looking for: add it as a stage disruption and teach the crop.
    val other = fixingOther
    if (other != null) {
        PickerDialog(
            title = "Which Pokémon? (added as a disruption)",
            options = species.filter { it !in setup.team.labels },
            onDismiss = { fixingOther = null },
            onPick = { picked ->
                vm.addDisruption(picked)
                vm.correctCell(other, picked)
                fixingOther = null
            },
        )
    }
}

private const val OTHER_SPECIES = "Other Pokémon…"
