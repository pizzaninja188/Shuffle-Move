package shuffle.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import shuffle.android.Capture
import shuffle.android.Labels
import shuffle.android.SolveOutcome
import shuffle.android.TeamSetup
import shuffle.android.repo
import shuffle.android.vision.BoardRect
import shuffle.android.vision.CellReading
import shuffle.fwk.data.Board
import shuffle.fwk.data.Species

data class StageItem(val id: String, val title: String)

data class MemberInfo(val name: String, val level: Int, val skillLevel: Int, val hasMega: Boolean)

data class SetupState(
    val loading: Boolean = true,
    val stage: StageItem? = null,
    val team: TeamSetup = TeamSetup(emptyList(), null),
    val members: List<MemberInfo> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo = app.repo

    private val _setup = MutableStateFlow(SetupState())
    val setup: StateFlow<SetupState> = _setup

    private val _stages = MutableStateFlow<List<StageItem>>(emptyList())
    val stages: StateFlow<List<StageItem>> = _stages

    private val _species = MutableStateFlow<List<String>>(emptyList())
    val species: StateFlow<List<String>> = _species

    /** Board being edited on the practice screen, and its last solve. */
    private val _board = MutableStateFlow<Board?>(null)
    val board: StateFlow<Board?> = _board
    private val _outcome = MutableStateFlow<SolveOutcome?>(null)
    val outcome: StateFlow<SolveOutcome?> = _outcome
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    val capture: StateFlow<Capture?> = repo.capture

    init {
        viewModelScope.launch {
            repo.onEngine {
                repo.warmUp()
                _stages.value = repo.allStages().map { StageItem(it.name, it.toString()) }
                _species.value = repo.allSpeciesNames()
            }
            refreshSetup()
        }
    }

    private suspend fun refreshSetup() {
        _setup.value = repo.onEngine {
            val stage = repo.currentStage()
            val team = repo.team()
            SetupState(
                loading = false,
                stage = StageItem(stage.name, stage.toString()),
                team = team,
                members = team.names.map { n ->
                    MemberInfo(n, repo.level(n), repo.skillLevel(n), speciesManager.getSpeciesByName(n).megaName != null)
                },
            )
        }
    }

    fun selectStage(id: String) = viewModelScope.launch {
        repo.stageId = id
        _board.value = null
        _outcome.value = null
        refreshSetup()
    }

    fun setTeam(team: TeamSetup) = viewModelScope.launch {
        val mega = team.megaSlot?.takeIf { it in team.names }
        repo.onEngine { repo.saveTeam(team.copy(megaSlot = mega)) }
        refreshSetup()
    }

    fun setLevel(name: String, level: Int) = viewModelScope.launch {
        repo.onEngine { repo.setLevel(name, level) }
        refreshSetup()
    }

    fun setSkillLevel(name: String, level: Int) = viewModelScope.launch {
        repo.onEngine { repo.setSkillLevel(name, level) }
        refreshSetup()
    }

    fun setMobileMode(on: Boolean) = viewModelScope.launch {
        repo.onEngine {
            repo.mobileMode = on
            _stages.value = repo.allStages().map { StageItem(it.name, it.toString()) }
        }
        refreshSetup()
    }

    // ---- practice board -------------------------------------------------------------------------

    fun loadDefaultBoard() = viewModelScope.launch {
        _board.value = repo.onEngine { getDefaultBoard(repo.currentStage()) }
        _outcome.value = null
    }

    fun loadCaptureBoard() = viewModelScope.launch {
        val cap = capture.value ?: return@launch
        _board.value = repo.onEngine { repo.boardFrom(cap.reading) }
        _outcome.value = cap.outcome
    }

    fun paint(row: Int, col: Int, label: String) = viewModelScope.launch {
        val b = Board(_board.value ?: Board())
        repo.onEngine {
            b.setSpeciesAt(row, col, speciesManager.getSpeciesByName(Labels.speciesName(label)))
            if (!Labels.canFreeze(label)) b.setFrozenAt(row, col, false)
        }
        _board.value = b
        _outcome.value = null
    }

    fun toggleFrozen(row: Int, col: Int) {
        val b = Board(_board.value ?: return)
        if (Labels.canFreeze(b.getSpeciesAt(row, col).name)) {
            b.setFrozenAt(row, col, !b.isFrozenAt(row, col))
            _board.value = b
            _outcome.value = null
        }
    }

    fun solveBoard() = viewModelScope.launch {
        val b = _board.value ?: return@launch
        _busy.value = true
        try {
            _outcome.value = repo.onEngine { repo.solve(b) }
        } finally {
            _busy.value = false
        }
    }

    // ---- capture review -------------------------------------------------------------------------

    /** The user says this cell shows [label]: learn from the crop and update the reading. */
    fun correctCell(cell: CellReading, label: String) = viewModelScope.launch {
        val cap = capture.value ?: return@launch
        repo.onEngine { repo.references.teach(label, cell.crop) }
        val cells = cap.reading.cells.map {
            if (it.row == cell.row && it.col == cell.col) {
                it.copy(label = label, guess = label, score = 1f, frozen = it.frozen && Labels.canFreeze(label))
            } else {
                it
            }
        }
        repo.publishCapture(cap.copy(reading = shuffle.android.vision.BoardReading(cells), outcome = null))
    }

    fun toggleCaptureFrozen(cell: CellReading) {
        val cap = capture.value ?: return
        val guess = cell.guess ?: return
        if (!Labels.canFreeze(guess)) return
        val cells = cap.reading.cells.map {
            if (it.row == cell.row && it.col == cell.col) it.copy(frozen = !it.frozen) else it
        }
        repo.publishCapture(cap.copy(reading = shuffle.android.vision.BoardReading(cells), outcome = null))
    }

    /** Confirms every confident cell as a taught example (quick way to build references). */
    fun teachAllConfident() = viewModelScope.launch {
        val cap = capture.value ?: return@launch
        repo.onEngine { cap.reading.cells.filter { it.label != null }.forEach { repo.references.teach(it.label!!, it.crop) } }
    }

    fun rereadCapture() = viewModelScope.launch {
        val cap = capture.value ?: return@launch
        val reading = repo.onEngine { repo.recognizer().read(cap.screen, cap.rect) }
        repo.publishCapture(cap.copy(reading = reading, outcome = null))
    }

    fun solveCapture() = viewModelScope.launch {
        val cap = capture.value ?: return@launch
        _busy.value = true
        try {
            val outcome = repo.onEngine { repo.solve(repo.boardFrom(cap.reading)) }
            repo.publishCapture(cap.copy(outcome = outcome))
        } finally {
            _busy.value = false
        }
    }

    fun saveCalibration(rect: BoardRect) = viewModelScope.launch {
        val cap = capture.value ?: return@launch
        repo.saveBoardRect(cap.screen.width, cap.screen.height, rect)
        val reading = repo.onEngine { repo.recognizer().read(cap.screen, rect) }
        repo.publishCapture(cap.copy(rect = rect, reading = reading, outcome = null))
    }

    fun recognitionLabels(): List<String> = setup.value.team.let { Labels.forTeam(it.names, it.megaSlot) }

    companion object {
        val SPECIAL_LABELS = listOf(Species.AIR.name, Species.WOOD.name, Species.METAL.name, Species.COIN.name)
    }
}
