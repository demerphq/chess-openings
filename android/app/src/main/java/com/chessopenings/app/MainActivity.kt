package com.chessopenings.app

import android.content.SharedPreferences
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engineAssets = AndroidStockfishAssets.prepare(this)
        SharedCoreBridge.configureSharedEngine(
            engineAssets.executablePath,
            engineAssets.nnueDirectory,
        )
        setContent {
            ChessOpeningsApp()
        }
    }
}

data class AndroidStockfishAssetConfig(
    val executablePath: String?,
    val nnueDirectory: String?,
)

object AndroidStockfishAssets {
    private const val ASSET_ROOT = "stockfish"

    fun prepare(context: Context): AndroidStockfishAssetConfig {
        val assets = context.assets
        val abi = Build.SUPPORTED_ABIS.firstOrNull { candidate ->
            runCatching {
                assets.list("$ASSET_ROOT/$candidate")
                    ?.contains("stockfish") == true
            }.getOrDefault(false)
        }

        val stockfishPath = abi?.let { selectedAbi ->
            val target = File(context.filesDir, "$ASSET_ROOT/$selectedAbi/stockfish")
            copyAssetIfNeeded(context, "$ASSET_ROOT/$selectedAbi/stockfish", target)
            target.setExecutable(true, true)
            target.absolutePath.takeIf { target.canExecute() }
        }

        val nnueDirectory = if (stockfishPath != null) {
            val directory = File(context.filesDir, "$ASSET_ROOT/nnue")
            val copied = copyNnueAssets(context, ASSET_ROOT, directory) +
                copyNnueAssets(context, "$ASSET_ROOT/nnue", directory)
            directory.absolutePath.takeIf { copied > 0 }
        } else {
            null
        }

        return AndroidStockfishAssetConfig(stockfishPath, nnueDirectory)
    }

    private fun copyNnueAssets(context: Context, assetDirectory: String, targetDirectory: File): Int {
        val names = runCatching {
            context.assets.list(assetDirectory).orEmpty().filter { it.endsWith(".nnue") }
        }.getOrDefault(emptyList())

        names.forEach { name ->
            copyAssetIfNeeded(
                context,
                "$assetDirectory/$name",
                File(targetDirectory, name),
            )
        }
        return names.size
    }

    private fun copyAssetIfNeeded(context: Context, assetPath: String, target: File) {
        val expectedSize = runCatching {
            context.assets.openFd(assetPath).use { it.length }
        }.getOrDefault(-1L)

        if (expectedSize > 0 && target.exists() && target.length() == expectedSize) {
            return
        }

        target.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

data class OpeningSummary(
    val name: String,
    val eco: String,
    val side: String,
    val description: String?,
    val isSeed: Boolean,
    val lines: List<LineSummary>,
)

data class LineSummary(
    val name: String,
    val source: String,
    val tags: List<String>,
    val plies: List<PlySummary>,
) {
    val sans: List<String>
        get() = plies.map { it.san }
}

data class PlySummary(
    val san: String,
    val uci: String,
    val annotation: String?,
    val alternativeSans: List<String>,
)

data class BoardSquare(
    val file: Char,
    val rank: Int,
    val pieceCode: String,
    val highlighted: Boolean,
) {
    val coordinate: String = "$file$rank"
}

data class DrillSelection(
    val opening: OpeningSummary,
    val line: LineSummary,
    val restoredSnapshot: PersistedDrillSnapshot? = null,
)

data class PersistedDrillSnapshot(
    val lineKey: String,
    val plyIndex: Int,
    val madeMistake: Boolean,
    val phase: String = SNAPSHOT_PHASE_DRILL,
    val playoutFen: String? = null,
    val playoutStartFen: String? = null,
    val playoutMovesJson: String? = null,
    val engineLevel: Int = DEFAULT_ENGINE_LEVEL,
)

data class LineProgressSummary(
    val correctStreak: Int = 0,
    val isLearned: Boolean = false,
    val timesAttempted: Int = 0,
    val timesCompleted: Int = 0,
)

data class AndroidMistake(
    val expectedSan: String,
    val expectedUci: String,
    val playedUci: String,
    val atMillis: Long,
)

class AndroidSoundPlayer : AutoCloseable {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)

    fun playMove() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 80)
    }

    fun playWrongMove() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 120)
    }

    fun playCompletion() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
    }

    override fun close() {
        toneGenerator.release()
    }
}

class AndroidProgressStore(private val preferences: SharedPreferences) {
    fun lineProgress(
        opening: OpeningSummary,
        line: LineSummary,
    ): LineProgressSummary {
        val prefix = progressKey(opening, line)
        return LineProgressSummary(
            correctStreak = preferences.getInt("$prefix.correctStreak", 0),
            isLearned = preferences.getBoolean("$prefix.isLearned", false),
            timesAttempted = preferences.getInt("$prefix.timesAttempted", 0),
            timesCompleted = preferences.getInt("$prefix.timesCompleted", 0),
        )
    }

    fun learnedLineCount(opening: OpeningSummary): Int =
        opening.lines.count { lineProgress(opening, it).isLearned }

    fun lineMistakes(
        opening: OpeningSummary,
        line: LineSummary,
    ): List<AndroidMistake> =
        decodeMistakes(preferences.getString("${progressKey(opening, line)}.mistakes", null))

    fun recordCompletion(
        opening: OpeningSummary,
        line: LineSummary,
        madeMistake: Boolean,
        threshold: Int = MASTERY_THRESHOLD,
    ) {
        val current = lineProgress(opening, line)
        val next = recordCompletionProgress(current, madeMistake, threshold)
        val prefix = progressKey(opening, line)
        preferences.edit()
            .putInt("$prefix.correctStreak", next.correctStreak)
            .putBoolean("$prefix.isLearned", next.isLearned)
            .putInt("$prefix.timesAttempted", next.timesAttempted)
            .putInt("$prefix.timesCompleted", next.timesCompleted)
            .apply()
    }

    fun recordMistake(
        opening: OpeningSummary,
        line: LineSummary,
        expectedPly: PlySummary,
        playedUci: String,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        val prefix = progressKey(opening, line)
        val next = appendRollingMistake(
            current = decodeMistakes(preferences.getString("$prefix.mistakes", null)),
            expectedPly = expectedPly,
            playedUci = playedUci,
            atMillis = atMillis,
        )
        preferences.edit()
            .putString("$prefix.mistakes", encodeMistakes(next))
            .apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }
}

class AndroidSettingsStore(private val preferences: SharedPreferences) {
    var drillMode: String
        get() = preferences.getString("drillMode", DRILL_MODE_STRICT)
            ?.takeIf { it == DRILL_MODE_STRICT || it == DRILL_MODE_SHOW_AND_RETRY }
            ?: DRILL_MODE_STRICT
        set(value) {
            preferences.edit()
                .putString(
                    "drillMode",
                    if (value == DRILL_MODE_SHOW_AND_RETRY) DRILL_MODE_SHOW_AND_RETRY else DRILL_MODE_STRICT,
                )
                .apply()
        }

    var masteryThreshold: Int
        get() = preferences.getInt("masteryThreshold", MASTERY_THRESHOLD)
        set(value) {
            preferences.edit()
                .putInt("masteryThreshold", value.coerceIn(1, 10))
                .apply()
        }

    var soundsEnabled: Boolean
        get() = preferences.getBoolean("soundsEnabled", true)
        set(value) {
            preferences.edit()
                .putBoolean("soundsEnabled", value)
                .apply()
        }

    var engineLevel: Int
        get() = preferences.getInt("engineLevel", DEFAULT_ENGINE_LEVEL)
        set(value) {
            preferences.edit()
                .putInt("engineLevel", value.coerceIn(0, 20))
                .apply()
        }

    var moveAnalysisDepth: Int
        get() = preferences.getInt("moveAnalysisDepth", DEFAULT_MOVE_ANALYSIS_DEPTH)
        set(value) {
            preferences.edit()
                .putInt("moveAnalysisDepth", value.coerceIn(6, 20))
                .apply()
        }

    var moveQualityBadgeMs: Int
        get() = preferences.getInt("moveQualityBadgeMs", DEFAULT_MOVE_QUALITY_BADGE_MS)
        set(value) {
            preferences.edit()
                .putInt("moveQualityBadgeMs", value.coerceIn(500, 5000))
                .apply()
        }
}

class AndroidDrillSnapshotStore(private val preferences: SharedPreferences) {
    fun latest(): PersistedDrillSnapshot? {
        val lineKey = preferences.getString("active.lineKey", null)?.takeIf { it.isNotBlank() }
            ?: return null
        return PersistedDrillSnapshot(
            lineKey = lineKey,
            plyIndex = preferences.getInt("active.plyIndex", 0),
            madeMistake = preferences.getBoolean("active.madeMistake", false),
            phase = preferences.getString("active.phase", SNAPSHOT_PHASE_DRILL)
                ?.takeIf { it == SNAPSHOT_PHASE_DRILL || it == SNAPSHOT_PHASE_PLAYOUT }
                ?: SNAPSHOT_PHASE_DRILL,
            playoutFen = preferences.getString("active.playoutFen", null)?.takeIf { it.isNotBlank() },
            playoutStartFen = preferences.getString("active.playoutStartFen", null)?.takeIf { it.isNotBlank() },
            playoutMovesJson = preferences.getString("active.playoutMovesJson", null)?.takeIf { it.isNotBlank() },
            engineLevel = preferences.getInt("active.engineLevel", DEFAULT_ENGINE_LEVEL),
        )
    }

    fun save(
        opening: OpeningSummary,
        line: LineSummary,
        plyIndex: Int,
        madeMistake: Boolean,
    ) {
        if (plyIndex <= initialDrillPlyCount(opening, line) || plyIndex >= line.plies.size) {
            clear()
            return
        }
        preferences.edit()
            .putString("active.lineKey", progressKey(opening, line))
            .putString("active.phase", SNAPSHOT_PHASE_DRILL)
            .putInt("active.plyIndex", plyIndex)
            .putBoolean("active.madeMistake", madeMistake)
            .remove("active.playoutFen")
            .remove("active.playoutStartFen")
            .remove("active.playoutMovesJson")
            .remove("active.engineLevel")
            .apply()
    }

    fun savePlayout(
        opening: OpeningSummary,
        line: LineSummary,
        positionFen: String,
        startingFen: String,
        movesJson: String,
        madeMistake: Boolean,
        engineLevel: Int,
    ) {
        preferences.edit()
            .putString("active.lineKey", progressKey(opening, line))
            .putString("active.phase", SNAPSHOT_PHASE_PLAYOUT)
            .putInt("active.plyIndex", line.plies.size)
            .putBoolean("active.madeMistake", madeMistake)
            .putString("active.playoutFen", positionFen)
            .putString("active.playoutStartFen", startingFen)
            .putString("active.playoutMovesJson", movesJson)
            .putInt("active.engineLevel", engineLevel.coerceIn(0, 20))
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove("active.lineKey")
            .remove("active.phase")
            .remove("active.plyIndex")
            .remove("active.madeMistake")
            .remove("active.playoutFen")
            .remove("active.playoutStartFen")
            .remove("active.playoutMovesJson")
            .remove("active.engineLevel")
            .apply()
    }
}

fun recordCompletionProgress(
    current: LineProgressSummary,
    madeMistake: Boolean,
    threshold: Int,
): LineProgressSummary {
    val nextStreak = if (madeMistake) 0 else current.correctStreak + 1
    return current.copy(
        correctStreak = nextStreak,
        isLearned = current.isLearned || nextStreak >= threshold,
        timesAttempted = current.timesAttempted + 1,
        timesCompleted = current.timesCompleted + 1,
    )
}

fun appendRollingMistake(
    current: List<AndroidMistake>,
    expectedPly: PlySummary,
    playedUci: String,
    atMillis: Long,
    limit: Int = MISTAKE_LOG_LIMIT,
): List<AndroidMistake> =
    (current + AndroidMistake(expectedPly.san, expectedPly.uci, playedUci, atMillis))
        .takeLast(limit.coerceAtLeast(1))

fun encodeMistakes(mistakes: List<AndroidMistake>): String {
    val array = JSONArray()
    mistakes.forEach { mistake ->
        array.put(
            JSONObject()
                .put("expectedSan", mistake.expectedSan)
                .put("expectedUci", mistake.expectedUci)
                .put("playedUci", mistake.playedUci)
                .put("atMillis", mistake.atMillis),
        )
    }
    return array.toString()
}

fun decodeMistakes(json: String?): List<AndroidMistake> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            AndroidMistake(
                expectedSan = item.optString("expectedSan"),
                expectedUci = item.optString("expectedUci"),
                playedUci = item.optString("playedUci"),
                atMillis = item.optLong("atMillis", 0L),
            )
        }.filter { it.expectedSan.isNotBlank() && it.expectedUci.isNotBlank() && it.playedUci.isNotBlank() }
    }.getOrElse { emptyList() }
}

fun mistakeSummaryText(mistakes: List<AndroidMistake>): String? {
    val latest = mistakes.lastOrNull() ?: return null
    val countPrefix = if (mistakes.size == 1) "1 mistake" else "${mistakes.size} mistakes"
    return "$countPrefix · played ${latest.playedUci} · book ${latest.expectedSan}"
}

fun progressKey(
    opening: OpeningSummary,
    line: LineSummary,
): String {
    val identity = listOf(
        opening.name,
        opening.eco,
        opening.side,
        line.source,
        line.name,
        line.plies.joinToString(separator = " ") { "${it.san}:${it.uci}" },
    ).joinToString(separator = "\u001F")
    return "line.${sha256Hex(identity)}"
}

fun drillSelectionForSnapshot(
    openings: List<OpeningSummary>,
    snapshot: PersistedDrillSnapshot,
): DrillSelection? {
    openings.forEach { opening ->
        opening.lines.forEach { line ->
            if (progressKey(opening, line) == snapshot.lineKey) {
                return DrillSelection(opening, line, snapshot)
            }
        }
    }
    return null
}

private fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

enum class AppTab(
    val title: String,
    val subtitle: String,
    val glyph: String,
) {
    Train(
        title = "train",
        subtitle = "choose a repertoire line to drill",
        glyph = "T",
    ),
    Library(
        title = "library",
        subtitle = "seed openings and saved repertoires",
        glyph = "L",
    ),
    Settings(
        title = "settings",
        subtitle = "training preferences",
        glyph = "S",
    ),
}

fun parseOpeningSummaries(jsonText: String): List<OpeningSummary> {
    val openings = JSONObject(jsonText).getJSONArray("openings")
    return List(openings.length()) { openingIndex ->
        val opening = openings.getJSONObject(openingIndex)
        val lines = opening.getJSONArray("lines")
        OpeningSummary(
            name = opening.getString("name"),
            eco = opening.getString("eco").uppercase(),
            side = opening.getString("side"),
            description = opening.optStringOrNull("description"),
            isSeed = opening.optBoolean("isSeed", true),
            lines = List(lines.length()) { lineIndex ->
                val line = lines.getJSONObject(lineIndex)
                val plies = line.getJSONArray("plies")
                LineSummary(
                    name = line.getString("name"),
                    source = line.optString("source", ""),
                    tags = line.optStringList("tags"),
                    plies = List(plies.length()) { plyIndex ->
                        val ply = plies.getJSONObject(plyIndex)
                        PlySummary(
                            san = ply.getString("san"),
                            uci = ply.optString("uci", ""),
                            annotation = ply.optStringOrNull("annotation"),
                            alternativeSans = ply.optStringList("alternativeSans"),
                        )
                    },
                )
            },
        )
    }
}

@Composable
fun ChessOpeningsApp() {
    val context = LocalContext.current
    val progressStore = remember {
        AndroidProgressStore(
            context.getSharedPreferences("line-progress", Context.MODE_PRIVATE),
        )
    }
    val drillSnapshotStore = remember {
        AndroidDrillSnapshotStore(
            context.getSharedPreferences("drill-snapshot", Context.MODE_PRIVATE),
        )
    }
    val settingsStore = remember {
        AndroidSettingsStore(
            context.getSharedPreferences("settings", Context.MODE_PRIVATE),
        )
    }
    val soundPlayer = remember { AndroidSoundPlayer() }
    DisposableEffect(soundPlayer) {
        onDispose { soundPlayer.close() }
    }
    var progressRevision by remember { mutableIntStateOf(0) }
    var settingsRevision by remember { mutableIntStateOf(0) }
    remember {
        check(SharedCoreBridge.isChessKitAvailable()) {
            "Shared ChessOpeningsCore bridge is unavailable"
        }
        check(SharedCoreBridge.isSharedDrillSessionAvailable()) {
            "Shared DrillSession bridge is unavailable"
        }
    }
    val openings = remember {
        context.assets.open("openings.json")
            .bufferedReader()
            .use { parseOpeningSummaries(it.readText()) }
    }
    remember(openings) {
        openings.firstOrNull()?.lines?.firstOrNull()?.let { line ->
            check(SharedCoreBridge.canRunSharedDrillSession(line)) {
                "Shared DrillSession cannot run bundled line data"
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF245F73),
            secondary = Color(0xFF87643A),
            background = Color(0xFFF6F2EA),
            surface = Color(0xFFFFFCF6),
            surfaceVariant = Color(0xFFEAE0D2),
            onSurface = Color(0xFF241F1B),
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            ChessOpeningsHome(
                openings = openings,
                progressStore = progressStore,
                drillSnapshotStore = drillSnapshotStore,
                settingsStore = settingsStore,
                soundPlayer = soundPlayer,
                progressRevision = progressRevision,
                settingsRevision = settingsRevision,
                onProgressChanged = { progressRevision += 1 },
                onSettingsChanged = { settingsRevision += 1 },
            )
        }
    }
}

@Composable
fun ChessOpeningsHome(
    openings: List<OpeningSummary>,
    progressStore: AndroidProgressStore,
    drillSnapshotStore: AndroidDrillSnapshotStore,
    settingsStore: AndroidSettingsStore,
    soundPlayer: AndroidSoundPlayer,
    progressRevision: Int,
    settingsRevision: Int,
    onProgressChanged: () -> Unit,
    onSettingsChanged: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(AppTab.Train) }
    var drillSelection by remember { mutableStateOf<DrillSelection?>(null) }
    var detailOpening by remember { mutableStateOf<OpeningSummary?>(null) }
    var didAutoResume by remember { mutableStateOf(false) }

    LaunchedEffect(openings) {
        if (didAutoResume) return@LaunchedEffect
        didAutoResume = true
        val snapshot = drillSnapshotStore.latest() ?: return@LaunchedEffect
        val restored = drillSelectionForSnapshot(openings, snapshot) ?: run {
            drillSnapshotStore.clear()
            return@LaunchedEffect
        }
        detailOpening = restored.opening
        drillSelection = restored
    }

    drillSelection?.let { selection ->
        DrillScreen(
            opening = selection.opening,
            line = selection.line,
            restoredSnapshot = selection.restoredSnapshot,
            progressStore = progressStore,
            drillSnapshotStore = drillSnapshotStore,
            settingsStore = settingsStore,
            settingsRevision = settingsRevision,
            soundPlayer = soundPlayer,
            onProgressChanged = onProgressChanged,
            onBack = {
                drillSnapshotStore.clear()
                drillSelection = null
            },
        )
        return
    }

    detailOpening?.let {
        BackHandler { detailOpening = null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            val opening = detailOpening
            if (selectedTab == AppTab.Settings) {
                SettingsScreen(
                    settingsStore = settingsStore,
                    progressStore = progressStore,
                    settingsRevision = settingsRevision,
                    onSettingsChanged = onSettingsChanged,
                    onProgressReset = onProgressChanged,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (opening == null) {
                OpeningCatalogue(
                    openings = openings,
                    tab = selectedTab,
                    progressStore = progressStore,
                    progressRevision = progressRevision,
                    onOpeningSelected = { detailOpening = it },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                OpeningDetailScreen(
                    opening = opening,
                    progressStore = progressStore,
                    progressRevision = progressRevision,
                    settingsStore = settingsStore,
                    settingsRevision = settingsRevision,
                    onBack = { detailOpening = null },
                    onStartDrill = { line ->
                        drillSelection = DrillSelection(opening, line)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AppTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selectedTab,
                    onClick = {
                        selectedTab = tab
                        detailOpening = null
                    },
                    icon = {
                        Text(
                            text = tab.glyph,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    label = { Text(tab.title) },
                )
            }
        }
    }
}

@Composable
fun OpeningCatalogue(
    openings: List<OpeningSummary>,
    tab: AppTab,
    progressStore: AndroidProgressStore,
    progressRevision: Int,
    onOpeningSelected: (OpeningSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val indexedOpenings = openings.mapIndexed { index, opening -> index to opening }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        CatalogueHeader(tab = tab, openingCount = openings.size)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (tab) {
                AppTab.Train -> {
                    openingSection(
                        title = "as white",
                        openings = indexedOpenings.filter { it.second.side == "white" },
                        showProgress = true,
                        progressStore = progressStore,
                        progressRevision = progressRevision,
                        onOpeningSelected = onOpeningSelected,
                    )

                    openingSection(
                        title = "as black",
                        openings = indexedOpenings.filter { it.second.side == "black" },
                        showProgress = true,
                        progressStore = progressStore,
                        progressRevision = progressRevision,
                        onOpeningSelected = onOpeningSelected,
                    )
                }

                AppTab.Library -> {
                    openingSection(
                        title = "seed openings",
                        openings = indexedOpenings.filter { it.second.isSeed },
                        showProgress = false,
                        progressStore = progressStore,
                        progressRevision = progressRevision,
                        onOpeningSelected = onOpeningSelected,
                    )

                    val customOpenings = indexedOpenings.filter { !it.second.isSeed }
                    item {
                        SectionHeader("yours")
                    }
                    if (customOpenings.isEmpty()) {
                        item {
                            EmptyRow("No custom openings yet")
                        }
                    } else {
                        itemsIndexed(customOpenings) { _, indexedOpening ->
                            val (_, opening) = indexedOpening
                            val learned = remember(progressRevision, opening) {
                                progressStore.learnedLineCount(opening)
                            }
                            OpeningRow(
                                opening = opening,
                                showProgress = false,
                                learned = learned,
                                onClick = { onOpeningSelected(opening) },
                            )
                        }
                    }
                }

                AppTab.Settings -> Unit
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settingsStore: AndroidSettingsStore,
    progressStore: AndroidProgressStore,
    settingsRevision: Int,
    onSettingsChanged: () -> Unit,
    onProgressReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drillMode = remember(settingsRevision) { settingsStore.drillMode }
    val masteryThreshold = remember(settingsRevision) { settingsStore.masteryThreshold }
    val soundsEnabled = remember(settingsRevision) { settingsStore.soundsEnabled }
    val engineLevel = remember(settingsRevision) { settingsStore.engineLevel }
    val moveAnalysisDepth = remember(settingsRevision) { settingsStore.moveAnalysisDepth }
    val moveQualityBadgeMs = remember(settingsRevision) { settingsStore.moveQualityBadgeMs }
    var showResetConfirmation by remember { mutableStateOf(false) }
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("reset line progress?") },
            text = { Text("This clears mastery streaks and completion counts for every line.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        progressStore.clearAll()
                        showResetConfirmation = false
                        onProgressReset()
                    },
                ) {
                    Text("reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("cancel")
                }
            },
        )
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            CatalogueHeader(tab = AppTab.Settings, openingCount = null)
        }
        item {
            SectionHeader("drill mode")
        }
        item {
            SettingOptionRow(
                label = "mistakes",
                value = if (drillMode == DRILL_MODE_SHOW_AND_RETRY) "show-and-retry" else "strict",
                actions = listOf(
                    SettingAction(
                        label = "strict",
                        enabled = drillMode != DRILL_MODE_STRICT,
                        onClick = {
                            settingsStore.drillMode = DRILL_MODE_STRICT
                            onSettingsChanged()
                        },
                    ),
                    SettingAction(
                        label = "retry",
                        enabled = drillMode != DRILL_MODE_SHOW_AND_RETRY,
                        onClick = {
                            settingsStore.drillMode = DRILL_MODE_SHOW_AND_RETRY
                            onSettingsChanged()
                        },
                    ),
                ),
            )
        }
        item {
            SectionHeader("mastery")
        }
        item {
            SettingStepperRow(
                label = "correct streak",
                value = masteryThreshold,
                canDecrement = masteryThreshold > 1,
                canIncrement = masteryThreshold < 10,
                onDecrement = {
                    settingsStore.masteryThreshold = masteryThreshold - 1
                    onSettingsChanged()
                },
                onIncrement = {
                    settingsStore.masteryThreshold = masteryThreshold + 1
                    onSettingsChanged()
                },
            )
        }
        item {
            SectionHeader("sound")
        }
        item {
            SettingOptionRow(
                label = "move + feedback sounds",
                value = if (soundsEnabled) "on" else "off",
                actions = listOf(
                    SettingAction(
                        label = if (soundsEnabled) "turn off" else "turn on",
                        enabled = true,
                        onClick = {
                            settingsStore.soundsEnabled = !soundsEnabled
                            onSettingsChanged()
                        },
                    ),
                ),
            )
        }
        item {
            SectionHeader("engine")
        }
        item {
            SettingStepperRow(
                label = "difficulty",
                value = engineLevel,
                canDecrement = engineLevel > 0,
                canIncrement = engineLevel < 20,
                onDecrement = {
                    settingsStore.engineLevel = engineLevel - 1
                    onSettingsChanged()
                },
                onIncrement = {
                    settingsStore.engineLevel = engineLevel + 1
                    onSettingsChanged()
                },
            )
        }
        item {
            SectionHeader("move quality")
        }
        item {
            SettingStepperRow(
                label = "analysis depth",
                value = moveAnalysisDepth,
                canDecrement = moveAnalysisDepth > 6,
                canIncrement = moveAnalysisDepth < 20,
                onDecrement = {
                    settingsStore.moveAnalysisDepth = moveAnalysisDepth - 1
                    onSettingsChanged()
                },
                onIncrement = {
                    settingsStore.moveAnalysisDepth = moveAnalysisDepth + 1
                    onSettingsChanged()
                },
            )
        }
        item {
            SettingStepperRow(
                label = "badge duration",
                value = moveQualityBadgeMs,
                valueSuffix = "ms",
                canDecrement = moveQualityBadgeMs > 500,
                canIncrement = moveQualityBadgeMs < 5000,
                onDecrement = {
                    settingsStore.moveQualityBadgeMs = moveQualityBadgeMs - 100
                    onSettingsChanged()
                },
                onIncrement = {
                    settingsStore.moveQualityBadgeMs = moveQualityBadgeMs + 100
                    onSettingsChanged()
                },
            )
        }
        item {
            SectionHeader("data")
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "line progress",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        onClick = { showResetConfirmation = true },
                    ) {
                        Text("reset")
                    }
                }
            }
        }
    }
}

data class SettingAction(
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun SettingOptionRow(
    label: String,
    value: String,
    actions: List<SettingAction>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    TextButton(onClick = action.onClick, enabled = action.enabled) {
                        Text(action.label)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingStepperRow(
    label: String,
    value: Int,
    valueSuffix: String = "",
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (valueSuffix.isBlank()) value.toString() else "$value $valueSuffix",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDecrement, enabled = canDecrement) {
                    Text("-")
                }
                TextButton(onClick = onIncrement, enabled = canIncrement) {
                    Text("+")
                }
            }
        }
    }
}

@Composable
fun CatalogueHeader(
    tab: AppTab,
    openingCount: Int?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tab.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (openingCount == null) {
                    tab.subtitle
                } else {
                    "$openingCount openings · ${tab.subtitle}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
fun OpeningDetailScreen(
    opening: OpeningSummary,
    progressStore: AndroidProgressStore,
    progressRevision: Int,
    settingsStore: AndroidSettingsStore,
    settingsRevision: Int,
    onBack: () -> Unit,
    onStartDrill: (LineSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val masteryThreshold = remember(settingsRevision) { settingsStore.masteryThreshold }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("back")
                }
                Button(
                    onClick = { opening.lines.firstOrNull()?.let(onStartDrill) },
                    enabled = opening.lines.isNotEmpty(),
                ) {
                    Text("drill all")
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = opening.name.toDisplayName(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${opening.eco} · ${opening.side.toDisplayName()} repertoire · ${opening.lines.size} lines",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
            }
        }

        opening.description?.takeIf { it.isNotBlank() }?.let { description ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        detailLineSection(
            title = "master games",
            opening = opening,
            lines = opening.lines.filter { it.source == "masters" },
            progressStore = progressStore,
            progressRevision = progressRevision,
            masteryThreshold = masteryThreshold,
            onStartDrill = onStartDrill,
        )

        detailLineSection(
            title = "online play (2200+)",
            opening = opening,
            lines = opening.lines.filter { it.source == "open" },
            progressStore = progressStore,
            progressRevision = progressRevision,
            masteryThreshold = masteryThreshold,
            onStartDrill = onStartDrill,
        )
    }
}

@Composable
fun DrillScreen(
    opening: OpeningSummary,
    line: LineSummary,
    restoredSnapshot: PersistedDrillSnapshot?,
    progressStore: AndroidProgressStore,
    drillSnapshotStore: AndroidDrillSnapshotStore,
    settingsStore: AndroidSettingsStore,
    settingsRevision: Int,
    soundPlayer: AndroidSoundPlayer,
    onProgressChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val initialPlyCount = remember(opening, line) { initialDrillPlyCount(opening, line) }
    val drillMode = remember(settingsRevision) { settingsStore.drillMode }
    val masteryThreshold = remember(settingsRevision) { settingsStore.masteryThreshold }
    var currentPlyCount by remember(line) { mutableIntStateOf(0) }
    var currentPositionFen by remember(line) { mutableStateOf(STARTING_POSITION_FEN) }
    var sharedDrillHandle by remember(line) { mutableStateOf(0L) }
    var selectedSquare by remember(line) { mutableStateOf<String?>(null) }
    var feedback by remember(line) { mutableStateOf<String?>(null) }
    var hintShown by remember(line) { mutableStateOf(false) }
    var solutionShown by remember(line) { mutableStateOf(false) }
    var showLineIsPlaying by remember(line) { mutableStateOf(false) }
    var madeMistake by remember(line) { mutableStateOf(false) }
    var completedViaShowLine by remember(line) { mutableStateOf(false) }
    var completionRecorded by remember(line) { mutableStateOf(false) }
    var playoutHandle by remember(line) { mutableStateOf(0L) }
    var playoutStartFen by remember(line) { mutableStateOf<String?>(null) }
    var playoutPositionFen by remember(line) { mutableStateOf<String?>(null) }
    var playoutSelectedSquare by remember(line) { mutableStateOf<String?>(null) }
    var playoutFeedback by remember(line) { mutableStateOf<String?>(null) }
    var playoutMoves by remember(line) { mutableStateOf(emptyList<SharedPlayoutMoveSummary>()) }
    val inPlayout = playoutHandle != 0L
    val visiblePlies = line.plies.take(currentPlyCount)
    val visibleMoveList = visiblePlies + playoutMoves.map { it.toPlySummary() }
    val displayedPositionFen = playoutPositionFen ?: currentPositionFen
    val board = remember(displayedPositionFen, visiblePlies, inPlayout) {
        boardSquaresFromFen(
            fen = displayedPositionFen,
            highlightedMove = if (inPlayout) "" else visiblePlies.lastOrNull()?.uci.orEmpty(),
        )
    }
    val nextPly = if (inPlayout) null else line.plies.getOrNull(currentPlyCount)
    val hintCoordinate = if (hintShown && !solutionShown) nextPly?.fromCoordinate() else null
    val solutionCoordinates = if (solutionShown) nextPly?.moveCoordinates().orEmpty() else emptySet()

    DisposableEffect(opening, line) {
        val handle = SharedCoreBridge.createSharedDrillSession(line)
        sharedDrillHandle = handle
        val snapshot = restoreOrResetSharedDrill(
            handle = handle,
            opening = opening,
            line = line,
            saved = restoredSnapshot?.takeIf { it.lineKey == progressKey(opening, line) },
        )
        currentPlyCount = snapshot.plyIndex
        currentPositionFen = snapshot.positionFen
        selectedSquare = null
        feedback = null
        hintShown = false
        solutionShown = false
        showLineIsPlaying = false
        madeMistake = restoredSnapshot?.madeMistake == true
        completedViaShowLine = false
        completionRecorded = restoredSnapshot?.phase == SNAPSHOT_PHASE_PLAYOUT
        playoutHandle = 0L
        playoutStartFen = null
        playoutPositionFen = null
        playoutSelectedSquare = null
        playoutFeedback = null
        playoutMoves = emptyList()
        if (restoredSnapshot?.phase == SNAPSHOT_PHASE_PLAYOUT) {
            val restoredStartFen = restoredSnapshot.playoutStartFen
                ?: restoredSnapshot.playoutFen
                ?: snapshot.positionFen
            val playout = SharedCoreBridge.createSharedPlayoutSession(
                restoredStartFen,
                opening.side.toSharedDrillUserSide(),
                restoredSnapshot.engineLevel,
                settingsStore.moveAnalysisDepth,
            )
            if (playout != 0L) {
                val movesJson = restoredSnapshot.playoutMovesJson
                if (movesJson.isNullOrBlank() || SharedCoreBridge.restoreSharedPlayoutMoves(playout, movesJson) != 1) {
                    SharedCoreBridge.bootstrapSharedPlayoutSession(playout)
                }
                playoutHandle = playout
                playoutStartFen = restoredStartFen
                playoutPositionFen = sharedPlayoutPositionFen(playout, restoredSnapshot.playoutFen ?: restoredStartFen)
                playoutMoves = parseSharedPlayoutMoves(SharedCoreBridge.sharedPlayoutMovesJson(playout))
                playoutFeedback = "resumed playout"
            }
        }
        onDispose {
            if (handle != 0L) {
                SharedCoreBridge.releaseSharedDrillSession(handle)
            }
            if (sharedDrillHandle == handle) {
                sharedDrillHandle = 0L
            }
            if (playoutHandle != 0L) {
                SharedCoreBridge.releaseSharedPlayoutSession(playoutHandle)
                playoutHandle = 0L
            }
        }
    }

    DisposableEffect(playoutHandle) {
        val handle = playoutHandle
        onDispose {
            if (handle != 0L) {
                SharedCoreBridge.releaseSharedPlayoutSession(handle)
            }
        }
    }

    LaunchedEffect(showLineIsPlaying, line) {
        if (!showLineIsPlaying) return@LaunchedEffect
        while (showLineIsPlaying && currentPlyCount < line.plies.size) {
            delay(1_000)
            val outcome = SharedCoreBridge.autoplaySharedDrillNext(sharedDrillHandle)
            val snapshot = sharedDrillSnapshot(sharedDrillHandle, fallbackPlyIndex = currentPlyCount)
            currentPlyCount = snapshot.plyIndex.coerceAtLeast(currentPlyCount)
            currentPositionFen = snapshot.positionFen
            selectedSquare = null
            feedback = null
            hintShown = false
            solutionShown = false
            if (outcome != SHARED_DRILL_ACCEPTED && outcome != SHARED_DRILL_LINE_COMPLETE) {
                showLineIsPlaying = false
            }
            if (outcome == SHARED_DRILL_LINE_COMPLETE || currentPlyCount >= line.plies.size) {
                completedViaShowLine = true
                playCompletionSound(settingsStore, soundPlayer)
                recordDrillCompletionIfNeeded(
                    progressStore = progressStore,
                    drillSnapshotStore = drillSnapshotStore,
                    opening = opening,
                    line = line,
                    madeMistake = madeMistake,
                    masteryThreshold = masteryThreshold,
                    alreadyRecorded = completionRecorded,
                    onRecorded = {
                        completionRecorded = true
                        onProgressChanged()
                    },
                )
            } else {
                drillSnapshotStore.save(opening, line, currentPlyCount, madeMistake)
            }
        }
        if (currentPlyCount >= line.plies.size) {
            showLineIsPlaying = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("back")
            }
            SourcePill(source = line.source)
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = opening.name.toDisplayName(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = line.name.toDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        BoardGrid(
            board = board,
            orientationSide = opening.side,
            selectedCoordinate = if (inPlayout) playoutSelectedSquare else selectedSquare,
            hintCoordinate = if (inPlayout) null else hintCoordinate,
            solutionCoordinates = if (inPlayout) emptySet() else solutionCoordinates,
            onSquareClick = { coordinate ->
                if (inPlayout) {
                    val selected = playoutSelectedSquare
                    if (selected == null) {
                        if (canStartPlayoutMove(coordinate, board, opening.side)) {
                            playoutSelectedSquare = coordinate
                            playoutFeedback = null
                        } else {
                            playoutFeedback = "Select one of your pieces"
                        }
                        return@BoardGrid
                    }

                    val playedUci = "$selected$coordinate"
                    when (SharedCoreBridge.submitSharedPlayoutMove(playoutHandle, playedUci)) {
                        SHARED_PLAYOUT_ACCEPTED, SHARED_PLAYOUT_GAME_OVER -> {
                            playoutPositionFen = sharedPlayoutPositionFen(playoutHandle, displayedPositionFen)
                            drillSnapshotStore.savePlayout(
                                opening = opening,
                                line = line,
                                positionFen = playoutPositionFen ?: displayedPositionFen,
                                startingFen = playoutStartFen ?: currentPositionFen,
                                movesJson = SharedCoreBridge.sharedPlayoutMovesJson(playoutHandle).orEmpty(),
                                madeMistake = madeMistake,
                                engineLevel = settingsStore.engineLevel,
                            )
                            playMoveSound(settingsStore, soundPlayer)
                            playoutSelectedSquare = null
                            playoutMoves = parseSharedPlayoutMoves(
                                SharedCoreBridge.sharedPlayoutMovesJson(playoutHandle),
                            )
                            playoutFeedback = playoutStatusLabel(SharedCoreBridge.sharedPlayoutStatus(playoutHandle))
                        }

                        SHARED_PLAYOUT_ILLEGAL_MOVE -> {
                            playWrongMoveSound(settingsStore, soundPlayer)
                            playoutSelectedSquare = null
                            playoutFeedback = "Illegal move"
                        }

                        else -> {
                            playoutSelectedSquare = null
                            playoutFeedback = "Move unavailable"
                        }
                    }
                    return@BoardGrid
                }

                val selected = selectedSquare
                if (selected == null) {
                    if (canStartDrillMove(coordinate, board, nextPly, opening.side)) {
                        selectedSquare = coordinate
                        feedback = null
                    } else if (nextPly != null) {
                        feedback = selectExpectedPieceFeedback(nextPly)
                    }
                } else {
                    val playedUci = "$selected$coordinate"
                    when (SharedCoreBridge.submitSharedDrillMove(sharedDrillHandle, playedUci)) {
                        SHARED_DRILL_ACCEPTED, SHARED_DRILL_LINE_COMPLETE -> {
                            val snapshot = sharedDrillSnapshot(sharedDrillHandle, fallbackPlyIndex = currentPlyCount)
                            currentPlyCount = snapshot.plyIndex.coerceAtLeast(currentPlyCount)
                            currentPositionFen = snapshot.positionFen
                            if (currentPlyCount >= line.plies.size) {
                                playCompletionSound(settingsStore, soundPlayer)
                                recordDrillCompletionIfNeeded(
                                    progressStore = progressStore,
                                    drillSnapshotStore = drillSnapshotStore,
                                    opening = opening,
                                    line = line,
                                    madeMistake = madeMistake,
                                    masteryThreshold = masteryThreshold,
                                    alreadyRecorded = completionRecorded,
                                    onRecorded = {
                                        completionRecorded = true
                                        onProgressChanged()
                                    },
                                )
                            } else {
                                playMoveSound(settingsStore, soundPlayer)
                                drillSnapshotStore.save(opening, line, currentPlyCount, madeMistake)
                            }
                            selectedSquare = null
                            feedback = null
                            hintShown = false
                            solutionShown = false
                            showLineIsPlaying = false
                        }

                        SHARED_DRILL_INCORRECT -> {
                            madeMistake = true
                            nextPly?.let {
                                progressStore.recordMistake(opening, line, it, playedUci)
                                onProgressChanged()
                            }
                            playWrongMoveSound(settingsStore, soundPlayer)
                            drillSnapshotStore.save(opening, line, currentPlyCount, madeMistake = true)
                            selectedSquare = null
                            feedback = expectedMoveFeedback(nextPly, drillMode)
                            solutionShown = drillMode == DRILL_MODE_SHOW_AND_RETRY
                            hintShown = false
                            showLineIsPlaying = false
                        }

                        else -> {
                            selectedSquare = null
                            feedback = "Illegal move"
                            hintShown = false
                            showLineIsPlaying = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = if (inPlayout) {
                playoutFeedback ?: "playout · your move"
            } else {
                feedback ?: drillProgressLabel(
                    currentPlyCount = currentPlyCount,
                    line = line,
                    madeMistake = madeMistake,
                    completedViaShowLine = completedViaShowLine,
                )
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (feedback == null && playoutFeedback == null) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            } else {
                MaterialTheme.colorScheme.secondary
            },
        )

        Text(
            text = formatSanLine(visibleMoveList, maxPlies = 24),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        if (inPlayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = {
                        SharedCoreBridge.undoSharedPlayoutSession(playoutHandle)
                        playoutPositionFen = sharedPlayoutPositionFen(playoutHandle, currentPositionFen)
                        drillSnapshotStore.savePlayout(
                            opening = opening,
                            line = line,
                            positionFen = playoutPositionFen ?: currentPositionFen,
                            startingFen = playoutStartFen ?: currentPositionFen,
                            movesJson = SharedCoreBridge.sharedPlayoutMovesJson(playoutHandle).orEmpty(),
                            madeMistake = madeMistake,
                            engineLevel = settingsStore.engineLevel,
                        )
                        playoutSelectedSquare = null
                        playoutMoves = parseSharedPlayoutMoves(SharedCoreBridge.sharedPlayoutMovesJson(playoutHandle))
                        playoutFeedback = "playout · your move"
                    },
                    enabled = SharedCoreBridge.sharedPlayoutPlyIndex(playoutHandle) > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("undo")
                }
                TextButton(
                    onClick = {
                        SharedCoreBridge.releaseSharedPlayoutSession(playoutHandle)
                        playoutHandle = 0L
                        playoutStartFen = null
                        playoutPositionFen = null
                        playoutSelectedSquare = null
                        playoutFeedback = null
                        playoutMoves = emptyList()
                        drillSnapshotStore.clear()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("exit playout")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = {
                        val status = SharedCoreBridge.offerSharedPlayoutDraw(playoutHandle)
                        playoutFeedback = if (status == SHARED_PLAYOUT_GAME_OVER_STATUS) {
                            "draw agreed"
                        } else {
                            playoutStatusLabel(status)
                        }
                        if (status == SHARED_PLAYOUT_GAME_OVER_STATUS) {
                            drillSnapshotStore.clear()
                        }
                    },
                    enabled = SharedCoreBridge.sharedPlayoutStatus(playoutHandle) != SHARED_PLAYOUT_GAME_OVER_STATUS,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("offer draw")
                }
                TextButton(
                    onClick = {
                        val status = SharedCoreBridge.resignSharedPlayout(playoutHandle)
                        playoutFeedback = if (status == SHARED_PLAYOUT_GAME_OVER_STATUS) {
                            "you resigned"
                        } else {
                            playoutStatusLabel(status)
                        }
                        if (status == SHARED_PLAYOUT_GAME_OVER_STATUS) {
                            drillSnapshotStore.clear()
                        }
                    },
                    enabled = SharedCoreBridge.sharedPlayoutStatus(playoutHandle) != SHARED_PLAYOUT_GAME_OVER_STATUS,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("resign")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = {
                        hintShown = !hintShown
                        if (hintShown) solutionShown = false
                    },
                    enabled = nextPly != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (hintShown) "hide hint" else "hint")
                }
                TextButton(
                    onClick = {
                        solutionShown = !solutionShown
                        if (solutionShown) hintShown = false
                    },
                    enabled = nextPly != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (solutionShown) "hide" else "solution")
                }
                TextButton(
                    onClick = {
                        SharedCoreBridge.undoSharedDrillSession(sharedDrillHandle)
                        val snapshot = sharedDrillSnapshot(sharedDrillHandle, fallbackPlyIndex = initialPlyCount)
                        currentPlyCount = snapshot.plyIndex.coerceAtLeast(initialPlyCount)
                        currentPositionFen = snapshot.positionFen
                        madeMistake = false
                        completedViaShowLine = false
                        completionRecorded = false
                        drillSnapshotStore.save(opening, line, currentPlyCount, madeMistake)
                        selectedSquare = null
                        feedback = null
                        hintShown = false
                        solutionShown = false
                        showLineIsPlaying = false
                    },
                    enabled = currentPlyCount > initialPlyCount,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("undo")
                }
                TextButton(
                    onClick = {
                        val snapshot = resetSharedDrillForOpening(sharedDrillHandle, opening)
                        currentPlyCount = snapshot.plyIndex
                        currentPositionFen = snapshot.positionFen
                        madeMistake = false
                        completedViaShowLine = false
                        completionRecorded = false
                        drillSnapshotStore.clear()
                        selectedSquare = null
                        feedback = null
                        hintShown = false
                        solutionShown = false
                        showLineIsPlaying = false
                    },
                    enabled = currentPlyCount > initialPlyCount,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("reset")
                }
            }

            TextButton(
                onClick = {
                    showLineIsPlaying = !showLineIsPlaying
                    if (showLineIsPlaying) {
                        selectedSquare = null
                        feedback = null
                        hintShown = false
                        solutionShown = false
                    }
                },
                enabled = currentPlyCount < line.plies.size || showLineIsPlaying,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (showLineIsPlaying) "pause" else "show line")
            }
            TextButton(
                onClick = {
                    val handle = SharedCoreBridge.createSharedPlayoutSession(
                        currentPositionFen,
                        opening.side.toSharedDrillUserSide(),
                        settingsStore.engineLevel,
                        settingsStore.moveAnalysisDepth,
                    )
                    if (handle == 0L) {
                        feedback = "Playout unavailable"
                    } else {
                        playoutHandle = handle
                        playoutStartFen = currentPositionFen
                        SharedCoreBridge.bootstrapSharedPlayoutSession(handle)
                        playoutPositionFen = sharedPlayoutPositionFen(handle, currentPositionFen)
                        drillSnapshotStore.savePlayout(
                            opening = opening,
                            line = line,
                            positionFen = playoutPositionFen ?: currentPositionFen,
                            startingFen = currentPositionFen,
                            movesJson = SharedCoreBridge.sharedPlayoutMovesJson(handle).orEmpty(),
                            madeMistake = madeMistake,
                            engineLevel = settingsStore.engineLevel,
                        )
                        playoutSelectedSquare = null
                        playoutMoves = parseSharedPlayoutMoves(SharedCoreBridge.sharedPlayoutMovesJson(handle))
                        playoutFeedback = playoutStatusLabel(SharedCoreBridge.sharedPlayoutStatus(handle))
                    }
                },
                enabled = currentPlyCount >= line.plies.size,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("continue with engine")
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun BoardPreview(
    line: LineSummary,
    orientationSide: String,
    modifier: Modifier = Modifier,
) {
    val highlightedMove = line.plies.firstOrNull()?.uci.orEmpty()
    val board = remember(highlightedMove) { startingBoardSquares(highlightedMove) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoardGrid(
            board = board,
            orientationSide = orientationSide,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Start position · previewing ${firstMoveLabel(line)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
    }
}

@Composable
fun BoardGrid(
    board: List<BoardSquare>,
    modifier: Modifier = Modifier,
    orientationSide: String = "white",
    selectedCoordinate: String? = null,
    hintCoordinate: String? = null,
    solutionCoordinates: Set<String> = emptySet(),
    onSquareClick: ((String) -> Unit)? = null,
) {
    val displayedSquares = remember(board, orientationSide) {
        displayedBoardSquares(board, orientationSide)
    }
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF6F655A), RoundedCornerShape(6.dp)),
        color = Color(0xFFEEE2D2),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            displayedSquares.chunked(8).forEach { rankSquares ->
                Row(modifier = Modifier.weight(1f)) {
                    rankSquares.forEachIndexed { index, square ->
                        BoardSquareCell(
                            square = square,
                            dark = isDarkBoardSquare(square.file, square.rank),
                            orientationSide = orientationSide,
                            selected = square.coordinate == selectedCoordinate,
                            hinted = square.coordinate == hintCoordinate,
                            solution = square.coordinate in solutionCoordinates,
                            onClick = onSquareClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoardSquareCell(
    square: BoardSquare,
    dark: Boolean,
    orientationSide: String = "white",
    selected: Boolean = false,
    hinted: Boolean = false,
    solution: Boolean = false,
    onClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val baseColor = if (dark) Color(0xFF9D7A55) else Color(0xFFE9D7B9)
    val background = when {
        selected -> Color(0xFF6EA4B8)
        solution -> Color(0xFF88B6D8)
        hinted -> Color(0xFF92BE74)
        square.highlighted -> Color(0xFFB9C86B)
        else -> baseColor
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .then(
                if (onClick == null) Modifier else Modifier.clickable { onClick(square.coordinate) }
            )
            .padding(3.dp),
    ) {
        if (square.file == rankLabelFile(orientationSide)) {
            Text(
                text = square.rank.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF3D332C).copy(alpha = 0.72f),
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        if (square.rank == fileLabelRank(orientationSide)) {
            Text(
                text = square.file.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF3D332C).copy(alpha = 0.72f),
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        pieceResourceId(square.pieceCode)?.let { resourceId ->
            Image(
                painter = painterResource(resourceId),
                contentDescription = pieceDescription(square.pieceCode),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .padding(2.dp),
            )
        }
    }
}

@Composable
fun OpeningRow(
    opening: OpeningSummary,
    showProgress: Boolean,
    learned: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = opening.name.toDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (showProgress) {
                        "$learned/${opening.lines.size} lines learned"
                    } else {
                        "${opening.lines.size} lines · ${opening.side.toDisplayName()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 16.dp),
            ) {
                Text(
                    text = opening.eco,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                if (showProgress) {
                    ProgressBar(
                        current = learned,
                        total = opening.lines.size,
                        modifier = Modifier.width(60.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun LineRow(
    line: LineSummary,
    progress: LineProgressSummary,
    mistakes: List<AndroidMistake>,
    masteryThreshold: Int,
    onClick: () -> Unit,
) {
    val streak = progress.correctStreak
    val threshold = masteryThreshold
    val mistakeSummary = mistakeSummaryText(mistakes)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = line.name.toDisplayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = linePreviewSans(line, maxPlies = 6),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            mistakeSummary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress.isLearned) {
                Text(
                    text = "learned · streak $streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProgressBar(
                        current = streak,
                        total = threshold,
                        modifier = Modifier.width(60.dp),
                    )
                    Text(
                        text = "$streak/$threshold",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressBar(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (total <= 0) 0f else current.coerceIn(0, total).toFloat() / total.toFloat()
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun LazyListScope.detailLineSection(
    title: String,
    opening: OpeningSummary,
    lines: List<LineSummary>,
    progressStore: AndroidProgressStore,
    progressRevision: Int,
    masteryThreshold: Int,
    onStartDrill: (LineSummary) -> Unit,
) {
    if (lines.isEmpty()) return

    item {
        SectionHeader(title)
    }

    itemsIndexed(lines) { _, line ->
        val progress = remember(progressRevision, opening, line) {
            progressStore.lineProgress(opening, line)
        }
        val mistakes = remember(progressRevision, opening, line) {
            progressStore.lineMistakes(opening, line)
        }
        LineRow(
            line = line,
            progress = progress,
            mistakes = mistakes,
            masteryThreshold = masteryThreshold,
            onClick = { onStartDrill(line) },
        )
    }
}

private fun LazyListScope.openingSection(
    title: String,
    openings: List<Pair<Int, OpeningSummary>>,
    showProgress: Boolean,
    progressStore: AndroidProgressStore,
    progressRevision: Int,
    onOpeningSelected: (OpeningSummary) -> Unit,
) {
    if (openings.isEmpty()) return

    item {
        SectionHeader(title)
    }

    itemsIndexed(openings) { _, indexedOpening ->
        val (_, opening) = indexedOpening
        val learned = remember(progressRevision, opening) {
            progressStore.learnedLineCount(opening)
        }
        OpeningRow(
            opening = opening,
            showProgress = showProgress,
            learned = learned,
            onClick = { onOpeningSelected(opening) },
        )
    }
}

@Composable
fun EmptyRow(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
fun SourcePill(source: String) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    ) {
        Text(
            text = sourceLabel(source),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}

fun startingBoardSquares(highlightedMove: String): List<BoardSquare> {
    val highlightedSquares = highlightedSquaresForUci(highlightedMove)
    val pieces = startingPieceMap().toMutableMap()
    applyUciMove(pieces, highlightedMove)
    return boardSquaresFromPieces(pieces, highlightedSquares)
}

fun boardSquaresAfterPlies(plies: List<PlySummary>): List<BoardSquare> {
    val pieces = startingPieceMap().toMutableMap()
    plies.forEach { ply -> applyUciMove(pieces, ply.uci) }
    val highlightedSquares = highlightedSquaresForUci(plies.lastOrNull()?.uci.orEmpty())
    return boardSquaresFromPieces(pieces, highlightedSquares)
}

fun boardSquaresFromFen(
    fen: String,
    highlightedMove: String = "",
): List<BoardSquare> {
    val placement = fen.substringBefore(' ')
    val pieces = mutableMapOf<String, String>()
    var rank = 8
    var file = 'a'

    for (symbol in placement) {
        when {
            symbol == '/' -> {
                rank -= 1
                file = 'a'
            }

            symbol.isDigit() -> {
                file += symbol.digitToInt()
            }

            symbol.isLetter() && rank in 1..8 && file in 'a'..'h' -> {
                pieces["$file$rank"] = fenPieceCode(symbol)
                file += 1
            }

            else -> return boardSquaresAfterPlies(emptyList())
        }
    }

    return boardSquaresFromPieces(
        pieces = pieces,
        highlightedSquares = highlightedSquaresForUci(highlightedMove),
    )
}

private fun fenPieceCode(symbol: Char): String {
    val color = if (symbol.isUpperCase()) "w" else "b"
    return "$color${symbol.lowercaseChar()}"
}

fun displayedBoardSquares(
    board: List<BoardSquare>,
    orientationSide: String,
): List<BoardSquare> {
    val squaresByCoordinate = board.associateBy { it.coordinate }
    val ranks = if (orientationSide.isBlackSide()) (1..8) else (8 downTo 1)
    val files = if (orientationSide.isBlackSide()) ('h' downTo 'a') else ('a'..'h')

    return ranks.flatMap { rank ->
        files.mapNotNull { file ->
            squaresByCoordinate["$file$rank"]
        }
    }
}

fun isDarkBoardSquare(file: Char, rank: Int): Boolean {
    val fileNumber = file - 'a' + 1
    return (fileNumber + rank) % 2 == 0
}

private fun boardSquaresFromPieces(
    pieces: Map<String, String>,
    highlightedSquares: Set<String>,
): List<BoardSquare> =
    (8 downTo 1).flatMap { rank ->
        ('a'..'h').map { file ->
            val coordinate = "$file$rank"
            BoardSquare(
                file = file,
                rank = rank,
                pieceCode = pieces[coordinate].orEmpty(),
                highlighted = coordinate in highlightedSquares,
            )
        }
    }

fun startingPieceMap(): Map<String, String> =
    (1..8).flatMap { rank ->
        ('a'..'h').mapNotNull { file ->
            val piece = startingPieceAt(file, rank)
            if (piece.isBlank()) null else "$file$rank" to piece
        }
    }.toMap()

fun applyUciMove(
    pieces: MutableMap<String, String>,
    uci: String,
) {
    if (uci.length < 4) return
    val from = uci.substring(0, 2)
    val to = uci.substring(2, 4)
    if (!from.isBoardCoordinate() || !to.isBoardCoordinate()) return

    val movedPiece = pieces[from] ?: return
    castlingSquares(movedPiece, from, to)?.let { castle ->
        pieces.remove(from)
        val rookPiece = pieces.remove(castle.rookFrom)
        pieces[castle.kingTo] = movedPiece
        if (rookPiece != null) {
            pieces[castle.rookTo] = rookPiece
        }
        return
    }

    pieces.remove(from)
    pieces[to] = promotedPieceCode(movedPiece, uci.getOrNull(4)) ?: movedPiece
}

data class CastlingSquares(
    val kingTo: String,
    val rookFrom: String,
    val rookTo: String,
)

fun castlingSquares(
    movedPiece: String,
    from: String,
    to: String,
): CastlingSquares? {
    if (movedPiece.length < 2 || movedPiece[1] != 'k') return null
    return when ("$from$to") {
        "e1g1", "e1h1" -> CastlingSquares(kingTo = "g1", rookFrom = "h1", rookTo = "f1")
        "e1c1", "e1a1" -> CastlingSquares(kingTo = "c1", rookFrom = "a1", rookTo = "d1")
        "e8g8", "e8h8" -> CastlingSquares(kingTo = "g8", rookFrom = "h8", rookTo = "f8")
        "e8c8", "e8a8" -> CastlingSquares(kingTo = "c8", rookFrom = "a8", rookTo = "d8")
        else -> null
    }
}

fun startingPieceAt(file: Char, rank: Int): String =
    when (rank) {
        8 -> "b${"rnbqkbnr"[file - 'a']}"
        7 -> "bp"
        2 -> "wp"
        1 -> "w${"rnbqkbnr"[file - 'a']}"
        else -> ""
    }

fun previewPieceAt(file: Char, rank: Int, uci: String): String {
    val coordinate = "$file$rank"
    if (uci.length < 4) return startingPieceAt(file, rank)

    val from = uci.substring(0, 2)
    val to = uci.substring(2, 4)
    if (!from.isBoardCoordinate() || !to.isBoardCoordinate()) {
        return startingPieceAt(file, rank)
    }

    val movedPiece = startingPieceAt(from[0], from[1].digitToInt())
    return when (coordinate) {
        from -> ""
        to -> promotedPieceCode(movedPiece, uci.getOrNull(4)) ?: movedPiece
        else -> startingPieceAt(file, rank)
    }
}

fun drillProgressLabel(
    currentPlyCount: Int,
    line: LineSummary,
    madeMistake: Boolean = false,
    completedViaShowLine: Boolean = false,
): String {
    if (line.plies.isEmpty()) return "No moves"
    return if (currentPlyCount >= line.plies.size) {
        if (!madeMistake && !completedViaShowLine) "perfect" else "line complete"
    } else {
        val next = line.plies[currentPlyCount]
        "Move ${currentPlyCount + 1} of ${line.plies.size} · next ${next.san}"
    }
}

fun sameMoveSquares(playedUci: String, expectedUci: String): Boolean =
    playedUci.length >= 4 &&
        expectedUci.length >= 4 &&
        playedUci.take(4) == expectedUci.take(4)

fun expectedMoveFeedback(
    nextPly: PlySummary?,
    drillMode: String = DRILL_MODE_STRICT,
): String =
    nextPly?.let {
        if (drillMode == DRILL_MODE_SHOW_AND_RETRY) {
            "Book says ${it.san} · try again"
        } else {
            "Try again"
        }
    } ?: "Line complete"

fun selectExpectedPieceFeedback(nextPly: PlySummary): String =
    "Select the piece for ${nextPly.san}"

fun initialDrillPlyCount(
    opening: OpeningSummary,
    line: LineSummary,
): Int =
    if (opening.side.isBlackSide() && line.plies.isNotEmpty()) 1 else 0

fun advancedDrillPlyCountAfterUserMove(
    currentPlyCount: Int,
    line: LineSummary,
): Int =
    (currentPlyCount + 2).coerceAtMost(line.plies.size)

fun showLineNextPlyCount(
    currentPlyCount: Int,
    line: LineSummary,
): Int =
    (currentPlyCount + 1).coerceAtMost(line.plies.size)

fun recordDrillCompletionIfNeeded(
    progressStore: AndroidProgressStore,
    drillSnapshotStore: AndroidDrillSnapshotStore,
    opening: OpeningSummary,
    line: LineSummary,
    madeMistake: Boolean,
    masteryThreshold: Int,
    alreadyRecorded: Boolean,
    onRecorded: () -> Unit,
) {
    if (alreadyRecorded) return
    progressStore.recordCompletion(
        opening = opening,
        line = line,
        madeMistake = madeMistake,
        threshold = masteryThreshold,
    )
    drillSnapshotStore.clear()
    onRecorded()
}

fun playMoveSound(
    settingsStore: AndroidSettingsStore,
    soundPlayer: AndroidSoundPlayer,
) {
    if (settingsStore.soundsEnabled) soundPlayer.playMove()
}

fun playWrongMoveSound(
    settingsStore: AndroidSettingsStore,
    soundPlayer: AndroidSoundPlayer,
) {
    if (settingsStore.soundsEnabled) soundPlayer.playWrongMove()
}

fun playCompletionSound(
    settingsStore: AndroidSettingsStore,
    soundPlayer: AndroidSoundPlayer,
) {
    if (settingsStore.soundsEnabled) soundPlayer.playCompletion()
}

data class SharedDrillSnapshot(
    val plyIndex: Int,
    val positionFen: String,
)

fun sharedDrillSnapshot(
    handle: Long,
    fallbackPlyIndex: Int = 0,
): SharedDrillSnapshot {
    if (handle == 0L) {
        return SharedDrillSnapshot(fallbackPlyIndex.coerceAtLeast(0), STARTING_POSITION_FEN)
    }
    return SharedDrillSnapshot(
        plyIndex = SharedCoreBridge.sharedDrillPlyIndex(handle).coerceAtLeast(0),
        positionFen = SharedCoreBridge.sharedDrillPositionFen(handle).orEmpty()
            .ifBlank { STARTING_POSITION_FEN },
    )
}

fun resetSharedDrillForOpening(
    handle: Long,
    opening: OpeningSummary,
): SharedDrillSnapshot {
    if (handle == 0L) return SharedDrillSnapshot(0, STARTING_POSITION_FEN)
    SharedCoreBridge.resetSharedDrillSession(handle)
    if (opening.side.isBlackSide()) {
        SharedCoreBridge.autoplaySharedDrillNext(handle)
    }
    return sharedDrillSnapshot(handle)
}

fun restoreOrResetSharedDrill(
    handle: Long,
    opening: OpeningSummary,
    line: LineSummary,
    saved: PersistedDrillSnapshot?,
): SharedDrillSnapshot {
    if (handle == 0L) return SharedDrillSnapshot(0, STARTING_POSITION_FEN)
    if (saved != null && saved.plyIndex > initialDrillPlyCount(opening, line)) {
        SharedCoreBridge.restoreSharedDrillSession(
            handle,
            saved.plyIndex,
            opening.side.toSharedDrillUserSide(),
        )
        return sharedDrillSnapshot(handle, fallbackPlyIndex = saved.plyIndex)
    }
    return resetSharedDrillForOpening(handle, opening)
}

fun undoDrillPlyCount(
    currentPlyCount: Int,
    initialPlyCount: Int,
    opening: OpeningSummary,
): Int {
    val playableParity = if (opening.side.isBlackSide()) 1 else 0
    var next = (currentPlyCount - 1).coerceAtLeast(initialPlyCount)
    while (next > initialPlyCount && next % 2 != playableParity) {
        next--
    }
    return next.coerceAtLeast(initialPlyCount)
}

fun canStartDrillMove(
    coordinate: String,
    board: List<BoardSquare>,
    nextPly: PlySummary?,
    openingSide: String,
): Boolean {
    val next = nextPly ?: return false
    if (next.uci.length < 4 || coordinate != next.uci.substring(0, 2)) return false
    val pieceCode = board.firstOrNull { it.coordinate == coordinate }?.pieceCode ?: return false
    return pieceCode.isNotBlank() && pieceCode.first() == pieceColorCode(openingSide)
}

fun canStartPlayoutMove(
    coordinate: String,
    board: List<BoardSquare>,
    openingSide: String,
): Boolean {
    val pieceCode = board.firstOrNull { it.coordinate == coordinate }?.pieceCode ?: return false
    return pieceCode.isNotBlank() && pieceCode.first() == pieceColorCode(openingSide)
}

fun sharedPlayoutPositionFen(
    handle: Long,
    fallbackFen: String,
): String =
    SharedCoreBridge.sharedPlayoutPositionFen(handle)?.takeIf { it.isNotBlank() } ?: fallbackFen

fun playoutStatusLabel(status: Int): String =
    when (status) {
        SHARED_PLAYOUT_GAME_OVER_STATUS -> "game over"
        SHARED_PLAYOUT_ENGINE_THINKING_STATUS -> "engine thinking"
        else -> "playout · your move"
    }

fun SharedPlayoutMoveSummary.toPlySummary(): PlySummary =
    PlySummary(
        san = san,
        uci = uci,
        annotation = quality?.let(::moveQualityMark),
        alternativeSans = emptyList(),
    )

fun moveQualityMark(quality: String): String =
    when (quality) {
        "brilliant" -> "!!"
        "best" -> "*"
        "excellent" -> "!"
        "good" -> "+"
        "inaccuracy" -> "?!"
        "mistake" -> "?"
        "blunder" -> "??"
        "miss" -> "x"
        else -> ""
    }

fun pieceColorCode(openingSide: String): Char =
    if (openingSide.isBlackSide()) 'b' else 'w'

fun String.toSharedDrillUserSide(): Int =
    if (isBlackSide()) SHARED_DRILL_USER_SIDE_BLACK else SHARED_DRILL_USER_SIDE_WHITE

fun PlySummary.fromCoordinate(): String? =
    uci.takeIf { it.length >= 4 }
        ?.substring(0, 2)
        ?.takeIf { it.isBoardCoordinate() }

fun PlySummary.moveCoordinates(): Set<String> {
    if (uci.length < 4) return emptySet()
    val from = uci.substring(0, 2)
    val to = uci.substring(2, 4)
    return listOf(from, to)
        .filter { it.isBoardCoordinate() }
        .toSet()
}

private fun promotedPieceCode(pieceCode: String, promotion: Char?): String? {
    if (pieceCode.isBlank() || promotion == null) return null
    val side = pieceCode.first()
    val promotedKind = when (promotion.lowercaseChar()) {
        'q' -> 'q'
        'r' -> 'r'
        'b' -> 'b'
        'n' -> 'n'
        else -> return null
    }
    return "$side$promotedKind"
}

fun pieceResourceId(pieceCode: String): Int? =
    when (pieceCode) {
        "bp" -> R.drawable.bp
        "bn" -> R.drawable.bn
        "bb" -> R.drawable.bb
        "br" -> R.drawable.br
        "bq" -> R.drawable.bq
        "bk" -> R.drawable.bk
        "wp" -> R.drawable.wp
        "wn" -> R.drawable.wn
        "wb" -> R.drawable.wb
        "wr" -> R.drawable.wr
        "wq" -> R.drawable.wq
        "wk" -> R.drawable.wk
        else -> null
    }

fun pieceDescription(pieceCode: String): String? =
    when (pieceCode) {
        "bp" -> "black pawn"
        "bn" -> "black knight"
        "bb" -> "black bishop"
        "br" -> "black rook"
        "bq" -> "black queen"
        "bk" -> "black king"
        "wp" -> "white pawn"
        "wn" -> "white knight"
        "wb" -> "white bishop"
        "wr" -> "white rook"
        "wq" -> "white queen"
        "wk" -> "white king"
        else -> null
    }

fun highlightedSquaresForUci(uci: String): Set<String> {
    if (uci.length < 4) return emptySet()
    val from = uci.substring(0, 2)
    val to = uci.substring(2, 4)
    return listOf(from, to)
        .filter { it.isBoardCoordinate() }
        .toSet()
}

fun formatSanLine(plies: List<PlySummary>, maxPlies: Int): String {
    if (plies.isEmpty()) return "No moves"
    val visible = plies.take(maxPlies)
    val moves = visible.chunked(2).mapIndexed { index, pair ->
        buildString {
            append(index + 1)
            append(". ")
            append(pair[0].displaySan())
            if (pair.size > 1) {
                append(' ')
                append(pair[1].displaySan())
            }
        }
    }.joinToString(" ")
    return if (plies.size > maxPlies) "$moves ..." else moves
}

fun PlySummary.displaySan(): String =
    if (annotation.isNullOrBlank()) san else "$san $annotation"

fun linePreviewSans(line: LineSummary, maxPlies: Int): String {
    val visible = line.sans.take(maxPlies)
    return if (visible.isEmpty()) {
        "No moves"
    } else {
        visible.joinToString(" ")
    }
}

fun lineDepthLabel(line: LineSummary): String {
    val fullMoves = (line.plies.size + 1) / 2
    val moveWord = if (fullMoves == 1) "move" else "moves"
    return "$fullMoves $moveWord"
}

fun firstMoveLabel(line: LineSummary): String {
    val first = line.plies.firstOrNull() ?: return "starting setup"
    val uci = first.uci.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    return "${first.san}$uci"
}

fun sourceLabel(source: String): String =
    source.ifBlank { "seed" }.toDisplayName()

fun String.toDisplayName(): String =
    split(" ", "-", "_")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { first -> first.uppercase() }
        }

private fun String.isBoardCoordinate(): Boolean =
    length == 2 && this[0] in 'a'..'h' && this[1] in '1'..'8'

private fun String.isBlackSide(): Boolean =
    equals("black", ignoreCase = true)

private fun rankLabelFile(orientationSide: String): Char =
    if (orientationSide.isBlackSide()) 'h' else 'a'

private fun fileLabelRank(orientationSide: String): Int =
    if (orientationSide.isBlackSide()) 8 else 1

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.optStringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return List(array.length()) { index -> array.optString(index) }
        .filter { it.isNotBlank() }
}

private const val SHARED_DRILL_ACCEPTED = 1
private const val SHARED_DRILL_INCORRECT = 2
private const val SHARED_DRILL_LINE_COMPLETE = 5
private const val SHARED_DRILL_USER_SIDE_WHITE = 0
private const val SHARED_DRILL_USER_SIDE_BLACK = 1
private const val SHARED_PLAYOUT_ACCEPTED = 1
private const val SHARED_PLAYOUT_ILLEGAL_MOVE = 3
private const val SHARED_PLAYOUT_GAME_OVER = 5
private const val SHARED_PLAYOUT_ENGINE_THINKING_STATUS = 1
private const val SHARED_PLAYOUT_GAME_OVER_STATUS = 3
private const val SNAPSHOT_PHASE_DRILL = "drill"
private const val SNAPSHOT_PHASE_PLAYOUT = "playout"
private const val DRILL_MODE_STRICT = "strict"
private const val DRILL_MODE_SHOW_AND_RETRY = "showAndRetry"
private const val MASTERY_THRESHOLD = 3
private const val MISTAKE_LOG_LIMIT = 20
private const val DEFAULT_ENGINE_LEVEL = 10
private const val DEFAULT_MOVE_ANALYSIS_DEPTH = 10
private const val DEFAULT_MOVE_QUALITY_BADGE_MS = 1750
private const val STARTING_POSITION_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
