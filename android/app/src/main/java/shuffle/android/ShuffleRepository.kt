package shuffle.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import shuffle.android.vision.BitmapCodec
import shuffle.android.vision.BoardReading
import shuffle.android.vision.BoardRecognizer
import shuffle.android.vision.BoardRect
import shuffle.android.vision.Pixels
import shuffle.android.vision.ReferenceStore
import shuffle.android.vision.decodePixels
import shuffle.fwk.config.ConfigPaths
import shuffle.fwk.data.Board
import shuffle.fwk.data.Species
import shuffle.fwk.data.Stage
import shuffle.fwk.data.TeamImpl
import shuffle.fwk.data.simulation.SimulationResult
import shuffle.fwk.headless.ShuffleEngine
import shuffle.fwk.headless.SolveRequest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException

/**
 * The team as the app edits it. [names] are your support Pokémon; [disruptions] are species the stage
 * puts on the board, saved as Shuffle Move "non-support" team members, exactly as the desktop app
 * does: they are recognized and can fall in, they don't count toward your team's types for
 * type-based skills, and skills that remove non-support Pokémon (e.g. Eject) target them.
 * Levels and skill levels live in Shuffle Move's roster.
 */
data class TeamSetup(
    val names: List<String>,
    val megaSlot: String?,
    val disruptions: List<String> = emptyList(),
    /** The mega form's name from species data (not always "Mega_" + name), or null. */
    val megaLabel: String? = null,
) {
    val labels: List<String> get() = Labels.forTeam(names, megaLabel, disruptions)

    /** The species a recognition label stands for on the simulator's board. */
    fun speciesFor(label: String): String = if (megaSlot != null && label == megaLabel) megaSlot else label
}

/** One solve: the board that was solved and its results, best first. */
data class SolveOutcome(
    val board: Board,
    val results: List<SimulationResult>,
    val feedersUsed: Int,
    val millis: Long,
    val timedOut: Boolean,
) {
    val best: SimulationResult? get() = results.firstOrNull { it.move.size >= 4 } ?: results.firstOrNull()
}

/** The most recent screenshot reading, kept for review, correction and teaching. */
data class Capture(val screen: Pixels, val rect: BoardRect, val reading: BoardReading, val outcome: SolveOutcome?)

/**
 * App-wide state: the Shuffle Move engine, settings, team, icons and recognition references.
 *
 * Shuffle Move's config managers are not thread safe, so everything that touches them runs on
 * [engineDispatcher] (one thread). The simulation itself fans out on the engine's own thread pool.
 */
class ShuffleRepository(private val context: Context) {

    val engineDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "shuffle-engine").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val engine: ShuffleEngine by lazy {
        ShuffleEngine().also { it.setMobileMode(mobileMode) }
    }

    private val _capture = MutableStateFlow<Capture?>(null)
    val capture: StateFlow<Capture?> = _capture

    private val iconCache = ConcurrentHashMap<String, Pixels>()

    val references = ReferenceStore(File(context.filesDir, "references"), BitmapCodec) { label -> iconPixels(label) }

    // ---- settings ------------------------------------------------------------------------------

    var mobileMode: Boolean
        get() = prefs.getBoolean("mobile_mode", true)
        set(v) {
            prefs.edit().putBoolean("mobile_mode", v).apply()
            engine.setMobileMode(v)
        }

    /** Random skyfall variations simulated per move. Higher = more accurate but slower. */
    var numFeeders: Int
        get() = prefs.getInt("num_feeders", 30)
        set(v) = prefs.edit().putInt("num_feeders", v.coerceIn(1, 500)).apply()

    var timeLimitMs: Long
        get() = prefs.getLong("time_limit_ms", 5000)
        set(v) = prefs.edit().putLong("time_limit_ms", v.coerceIn(500, 60000)).apply()

    var stageId: String
        get() = prefs.getString("stage", "001") ?: "001"
        set(v) = prefs.edit().putString("stage", v).apply()

    var movesLeft: Int
        get() = prefs.getInt("moves_left", 0)
        set(v) = prefs.edit().putInt("moves_left", v.coerceAtLeast(0)).apply()

    var megaProgress: Int
        get() = prefs.getInt("mega_progress", 0)
        set(v) = prefs.edit().putInt("mega_progress", v.coerceAtLeast(0)).apply()

    fun boardRect(screenW: Int, screenH: Int): BoardRect =
        BoardRect.decode(prefs.getString("rect_${screenW}x$screenH", null)) ?: BoardRect.defaultFor(screenW, screenH)

    fun isCalibrated(screenW: Int, screenH: Int) = prefs.contains("rect_${screenW}x$screenH")

    fun saveBoardRect(screenW: Int, screenH: Int, rect: BoardRect) =
        prefs.edit().putString("rect_${screenW}x$screenH", rect.encode()).apply()

    // ---- engine access (call from engineDispatcher) ---------------------------------------------

    suspend fun <T> onEngine(block: ShuffleEngine.() -> T): T = withContext(engineDispatcher) { engine.block() }

    fun currentStage(): Stage = engine.getStage(stageId) ?: engine.getStage("001")

    fun allStages(): List<Stage> = engine.stageManager.allStages

    fun allSpeciesNames(): List<String> =
        engine.speciesManager.allSpecies.filter { it !in Species.FIXED_SPECIES }.map { it.name }.sorted()

    fun team(): TeamSetup {
        val t = engine.getTeamForStage(currentStage())
        val all = t.names.filter { it !in Labels.SPECIALS }
        val disruptions = all.filter { t.isNonSupport(it) }
        val mega = t.megaSlotName
        val megaLabel = mega?.let { engine.speciesManager.getSpeciesByName(it).megaName }
        return TeamSetup(all - disruptions.toSet(), mega, disruptions, megaLabel)
    }

    fun saveTeam(setup: TeamSetup) {
        val team = TeamImpl()
        val support = setup.names.distinct()
        val disruptions = setup.disruptions.distinct().filter { it !in support && it !in Labels.SPECIALS }
        (support + disruptions).forEachIndexed { i, n -> team.addName(n, ('a' + i)) }
        disruptions.forEach { team.setNonSupport(it, true) }
        team.setMegaSlot(setup.megaSlot?.takeIf { it in support })
        val tm = engine.teamManager
        tm.setTeamForStage(team, currentStage())
        tm.saveDataToConfig()
    }

    fun level(name: String): Int = engine.rosterManager.getLevelForSpecies(name).coerceAtLeast(1)

    fun setLevel(name: String, level: Int) {
        engine.rosterManager.setLevelForSpecies(name, level.coerceIn(1, Species.MAX_LEVEL))
        engine.rosterManager.saveDataToConfig()
    }

    fun skillLevel(name: String): Int = engine.rosterManager.getSkillLevelForSpecies(engine.speciesManager.getSpeciesByName(name))

    fun setSkillLevel(name: String, level: Int) {
        engine.rosterManager.setSkillLevelForSpecies(engine.speciesManager.getSpeciesByName(name), level)
        engine.rosterManager.saveDataToConfig()
    }

    fun recognitionLabels(): List<String> = team().labels

    fun recognizer(): BoardRecognizer {
        warmUp()
        return BoardRecognizer(references.classifierFor(recognitionLabels()), Labels::canFreeze)
    }

    /** Builds the simulator's board from a reading (unsure cells use their best guess). */
    fun boardFrom(reading: BoardReading): Board {
        val sm = engine.speciesManager
        val team = team()
        val board = Board()
        var megaSeen = false
        for (cell in reading.cells) {
            val label = cell.guess ?: Species.AIR.name
            if (label == team.megaLabel) megaSeen = true
            board.setSpeciesAt(cell.row, cell.col, sm.getSpeciesByName(team.speciesFor(label)))
            board.setFrozenAt(cell.row, cell.col, cell.frozen)
        }
        board.setMegaProgress(if (megaSeen) megaThreshold() else megaProgress)
        return board
    }

    private fun megaThreshold(): Int {
        val t = engine.getTeamForStage(currentStage())
        return t.getMegaThreshold(engine.speciesManager, engine.rosterManager, engine.effectManager)
    }

    /**
     * Solves a board for the current stage and team within the time limit. If the full feeder count
     * does not finish in time it retries once with a few feeders, so there is always an answer.
     */
    fun solve(board: Board): SolveOutcome {
        val stage = currentStage()
        val team = engine.getTeamForStage(stage)
        fun request(feeders: Int) = SolveRequest(board, stage, team).setNumFeeders(feeders).also { r ->
            if (movesLeft > 0) r.setRemainingMoves(movesLeft)
        }
        val start = System.currentTimeMillis()
        return try {
            val results = engine.solve(request(numFeeders), timeLimitMs)
            SolveOutcome(board, results, numFeeders, System.currentTimeMillis() - start, false)
        } catch (e: TimeoutException) {
            val fallback = maxOf(1, minOf(5, numFeeders / 4))
            Log.w(TAG, "Solve timed out at $numFeeders feeders; retrying with $fallback")
            val results = engine.solve(request(fallback), timeLimitMs)
            SolveOutcome(board, results, fallback, System.currentTimeMillis() - start, true)
        }
    }

    fun publishCapture(capture: Capture?) {
        _capture.value = capture
    }

    // ---- icons -----------------------------------------------------------------------------------

    /** Species/mega/block name -> bundled icon resource path. Filled by [warmUp]. */
    @Volatile private var iconPaths: Map<String, String> = emptyMap()

    /** Loads the engine's data and icon table. Call once on [engineDispatcher] before using icons. */
    fun warmUp() {
        if (iconPaths.isNotEmpty()) return
        val icons = engine.configFactory.getIconManager(shuffle.fwk.config.ConfigManager::class.java)
        iconPaths = icons.getKeys(shuffle.fwk.config.EntryType.RESOURCE)
            .mapNotNull { k -> icons.getResourceValue(k)?.let { k to it } }.toMap()
        engine.speciesManager
        engine.stageManager
    }

    /** The Shuffle Move icon for a species/mega name or block, from :core's bundled resources. */
    fun iconPixels(name: String): Pixels? = iconCache[name] ?: run {
        val path = iconPaths[name] ?: return null
        ConfigPaths.openResource(path)?.let(::decodePixels)?.also { iconCache[name] = it }
    }

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()

    /** Safe from any thread (only reads the icon table loaded by [warmUp]). */
    fun iconBitmap(name: String): Bitmap? = bitmapCache[name] ?: run {
        val path = iconPaths[name] ?: return null
        ConfigPaths.openResource(path)?.use { BitmapFactory.decodeStream(it) }?.also { bitmapCache[name] = it }
    }

    companion object {
        const val TAG = "ShuffleMove"
    }
}
