package com.chessopenings.app

import android.content.SharedPreferences
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val stockfishPath = File(
            context.applicationInfo.nativeLibraryDir,
            "libstockfish.so",
        ).takeIf { it.isFile && it.canExecute() }?.absolutePath

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
    val id: String = "",
)

data class LineSummary(
    val name: String,
    val source: String,
    val tags: List<String>,
    val plies: List<PlySummary>,
    val id: String = "",
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

data class AnimatedBoardPiece(
    val id: Int,
    val coordinate: String,
    val pieceCode: String,
)

data class ReconciledAnimatedBoardPieces(
    val pieces: List<AnimatedBoardPiece>,
    val nextId: Int,
)

data class MoveQualityAnnotation(
    val square: String,
    val quality: String,
    val id: Long = System.currentTimeMillis(),
)

data class PendingPromotionMove(
    val from: String,
    val to: String,
    val inPlayout: Boolean,
)

enum class PlayoutConfirmationAction {
    OfferDraw,
    Resign,
    Exit,
}

data class BoardArrow(
    val from: String,
    val to: String,
)

data class ConfettiParticle(
    val originX: Float,
    val originY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val sizeFraction: Float,
    val color: Color,
)

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
        val prefix = storedProgressPrefix(opening, line)
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
        decodeMistakes(preferences.getString("${storedProgressPrefix(opening, line)}.mistakes", null))

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
            current = lineMistakes(opening, line),
            expectedPly = expectedPly,
            playedUci = playedUci,
            atMillis = atMillis,
        )
        preferences.edit()
            .putString("$prefix.mistakes", encodeMistakes(next))
            .apply()
    }

    fun migrateCustomOpening(opening: OpeningSummary) {
        if (opening.isSeed) return
        opening.lines.forEach { line ->
            val target = progressKey(opening, line)
            val legacy = legacyProgressKey(opening, line)
            if (target == legacy || hasStoredProgress(target) || !hasStoredProgress(legacy)) {
                return@forEach
            }
            val editor = preferences.edit()
                .putInt("$target.correctStreak", preferences.getInt("$legacy.correctStreak", 0))
                .putBoolean("$target.isLearned", preferences.getBoolean("$legacy.isLearned", false))
                .putInt("$target.timesAttempted", preferences.getInt("$legacy.timesAttempted", 0))
                .putInt("$target.timesCompleted", preferences.getInt("$legacy.timesCompleted", 0))
            preferences.getString("$legacy.mistakes", null)?.let {
                editor.putString("$target.mistakes", it)
            }
            editor.apply()
        }
    }

    fun deleteOpening(opening: OpeningSummary) {
        val prefixes = opening.lines.flatMap { line ->
            listOf(progressKey(opening, line), legacyProgressKey(opening, line))
        }.toSet()
        val editor = preferences.edit()
        preferences.all.keys
            .filter { key -> prefixes.any { prefix -> key == prefix || key.startsWith("$prefix.") } }
            .forEach(editor::remove)
        editor.apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun storedProgressPrefix(opening: OpeningSummary, line: LineSummary): String {
        val primary = progressKey(opening, line)
        return if (hasStoredProgress(primary)) primary else legacyProgressKey(opening, line)
    }

    private fun hasStoredProgress(prefix: String): Boolean =
        preferences.contains("$prefix.correctStreak") ||
            preferences.contains("$prefix.isLearned") ||
            preferences.contains("$prefix.timesAttempted") ||
            preferences.contains("$prefix.timesCompleted") ||
            preferences.contains("$prefix.mistakes")
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

    fun migrateCustomOpening(opening: OpeningSummary) {
        val activeKey = preferences.getString("active.lineKey", null) ?: return
        opening.lines.firstOrNull { legacyProgressKey(opening, it) == activeKey }?.let { line ->
            preferences.edit()
                .putString("active.lineKey", progressKey(opening, line))
                .apply()
        }
    }

    fun clearForOpening(opening: OpeningSummary) {
        val activeKey = preferences.getString("active.lineKey", null) ?: return
        val belongsToOpening = opening.lines.any { line ->
            activeKey == progressKey(opening, line) ||
                activeKey == legacyProgressKey(opening, line)
        }
        if (belongsToOpening) clear()
    }
}

class AndroidCustomOpeningStore(private val preferences: SharedPreferences) {
    fun openings(): List<OpeningSummary> =
        decodeCustomOpenings(preferences.getString(CUSTOM_OPENINGS_KEY, null))

    fun create(name: String, eco: String, side: String): OpeningSummary {
        val opening = OpeningSummary(
            name = name.trim(),
            eco = eco.trim().uppercase(),
            side = side,
            description = null,
            isSeed = false,
            lines = emptyList(),
            id = UUID.randomUUID().toString(),
        )
        save(openings() + opening)
        return opening
    }

    fun delete(openingId: String) {
        save(openings().filterNot { it.id == openingId })
    }

    fun addLine(openingId: String, line: LineSummary): OpeningSummary? {
        var updated: OpeningSummary? = null
        val persistedLine = if (line.id.isBlank()) {
            line.copy(id = UUID.randomUUID().toString())
        } else {
            line
        }
        val openings = openings().map { opening ->
            if (opening.id == openingId) {
                opening.copy(lines = opening.lines + persistedLine).also { updated = it }
            } else {
                opening
            }
        }
        save(openings)
        return updated
    }

    private fun save(openings: List<OpeningSummary>) {
        preferences.edit()
            .putString(CUSTOM_OPENINGS_KEY, encodeCustomOpenings(openings))
            .apply()
    }
}

data class SANLineValidationResult(
    val plies: List<PlySummary>?,
    val errorMessage: String?,
)

fun parseSANLineValidation(jsonText: String?): SANLineValidationResult {
    if (jsonText.isNullOrBlank()) {
        return SANLineValidationResult(null, "could not validate moves")
    }
    return runCatching {
        val result = JSONObject(jsonText)
        if (!result.optBoolean("ok")) {
            val message = when (result.optString("error")) {
                "empty" -> "no moves entered"
                "illegal" -> {
                    val ply = result.optInt("ply", -1)
                    val san = result.optString("san")
                    "illegal move at ply ${ply + 1}: $san"
                }
                else -> "could not validate moves"
            }
            SANLineValidationResult(null, message)
        } else {
            val pliesJson = result.getJSONArray("plies")
            val plies = List(pliesJson.length()) { index ->
                val ply = pliesJson.getJSONObject(index)
                PlySummary(
                    san = ply.getString("san"),
                    uci = ply.getString("uci"),
                    annotation = null,
                    alternativeSans = emptyList(),
                )
            }
            SANLineValidationResult(plies, null)
        }
    }.getOrElse {
        SANLineValidationResult(null, "could not validate moves")
    }
}

fun encodeCustomOpenings(openings: List<OpeningSummary>): String =
    JSONArray(
        openings.map { opening ->
            JSONObject()
                .put("id", opening.id)
                .put("name", opening.name)
                .put("eco", opening.eco)
                .put("side", opening.side)
                .put(
                    "lines",
                    JSONArray(
                        opening.lines.map { line ->
                            JSONObject()
                                .put("id", line.id)
                                .put("name", line.name)
                                .put("source", line.source)
                                .put("tags", JSONArray(line.tags))
                                .put(
                                    "plies",
                                    JSONArray(
                                        line.plies.map { ply ->
                                            JSONObject()
                                                .put("san", ply.san)
                                                .put("uci", ply.uci)
                                        },
                                    ),
                                )
                        },
                    ),
                )
        },
    ).toString()

fun decodeCustomOpenings(jsonText: String?): List<OpeningSummary> {
    if (jsonText.isNullOrBlank()) return emptyList()
    return runCatching {
        val openings = JSONArray(jsonText)
        List(openings.length()) { openingIndex ->
            val opening = openings.getJSONObject(openingIndex)
            val lines = opening.optJSONArray("lines") ?: JSONArray()
            OpeningSummary(
                name = opening.getString("name"),
                eco = opening.optString("eco").uppercase(),
                side = opening.optString("side").takeIf { it == "black" } ?: "white",
                description = null,
                isSeed = false,
                lines = List(lines.length()) { lineIndex ->
                    val line = lines.getJSONObject(lineIndex)
                    val plies = line.getJSONArray("plies")
                    LineSummary(
                        name = line.getString("name"),
                        source = line.optString("source", "masters"),
                        tags = line.optStringList("tags"),
                        plies = List(plies.length()) { plyIndex ->
                            val ply = plies.getJSONObject(plyIndex)
                            PlySummary(
                                san = ply.getString("san"),
                                uci = ply.getString("uci"),
                                annotation = null,
                                alternativeSans = emptyList(),
                            )
                        },
                        id = line.optString("id").ifBlank {
                            "legacy-${opening.getString("id")}-$lineIndex"
                        },
                    )
                },
                id = opening.getString("id"),
            )
        }
    }.getOrElse { emptyList() }
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
    if (!opening.isSeed && opening.id.isNotBlank() && line.id.isNotBlank()) {
        return "line.${sha256Hex("custom\u001F${opening.id}\u001F${line.id}")}"
    }
    return legacyProgressKey(opening, line)
}

fun legacyProgressKey(
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
            id = "seed-$openingIndex",
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
    val customOpeningStore = remember {
        AndroidCustomOpeningStore(
            context.getSharedPreferences("custom-openings", Context.MODE_PRIVATE),
        )
    }
    val soundPlayer = remember { AndroidSoundPlayer() }
    DisposableEffect(soundPlayer) {
        onDispose { soundPlayer.close() }
    }
    var progressRevision by remember { mutableIntStateOf(0) }
    var settingsRevision by remember { mutableIntStateOf(0) }
    var customOpeningRevision by remember { mutableIntStateOf(0) }
    remember {
        check(SharedCoreBridge.isChessKitAvailable()) {
            "Shared ChessOpeningsCore bridge is unavailable"
        }
        check(SharedCoreBridge.isSharedDrillSessionAvailable()) {
            "Shared DrillSession bridge is unavailable"
        }
    }
    val seedOpenings = remember {
        context.assets.open("openings.json")
            .bufferedReader()
            .use { parseOpeningSummaries(it.readText()) }
    }
    val openings = remember(seedOpenings, customOpeningRevision) {
        val customOpenings = customOpeningStore.openings()
        customOpenings.forEach { opening ->
            progressStore.migrateCustomOpening(opening)
            drillSnapshotStore.migrateCustomOpening(opening)
        }
        seedOpenings + customOpenings
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
                customOpeningStore = customOpeningStore,
                soundPlayer = soundPlayer,
                progressRevision = progressRevision,
                settingsRevision = settingsRevision,
                onProgressChanged = { progressRevision += 1 },
                onSettingsChanged = { settingsRevision += 1 },
                onCustomOpeningsChanged = { customOpeningRevision += 1 },
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
    customOpeningStore: AndroidCustomOpeningStore,
    soundPlayer: AndroidSoundPlayer,
    progressRevision: Int,
    settingsRevision: Int,
    onProgressChanged: () -> Unit,
    onSettingsChanged: () -> Unit,
    onCustomOpeningsChanged: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(AppTab.Train) }
    var drillSelection by remember { mutableStateOf<DrillSelection?>(null) }
    var detailOpening by remember { mutableStateOf<OpeningSummary?>(null) }
    var didAutoResume by remember { mutableStateOf(false) }
    var showNewOpening by remember { mutableStateOf(false) }

    if (showNewOpening) {
        NewOpeningDialog(
            onDismiss = { showNewOpening = false },
            onCreate = { name, eco, side ->
                customOpeningStore.create(name, eco, side)
                onCustomOpeningsChanged()
                showNewOpening = false
            },
        )
    }

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
            onOpenSettings = {
                drillSelection = null
                detailOpening = null
                selectedTab = AppTab.Settings
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
                    onCreateOpening = { showNewOpening = true },
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
                    onDeleteOpening = {
                        progressStore.deleteOpening(opening)
                        drillSnapshotStore.clearForOpening(opening)
                        customOpeningStore.delete(opening.id)
                        onCustomOpeningsChanged()
                        detailOpening = null
                    },
                    onAddLine = { lineName, sanText ->
                        val validation = parseSANLineValidation(
                            SharedCoreBridge.validateSanLineJson(sanText),
                        )
                        val plies = validation.plies
                            ?: return@OpeningDetailScreen validation.errorMessage
                        val updated = customOpeningStore.addLine(
                            openingId = opening.id,
                            line = LineSummary(
                                name = lineName.trim(),
                                source = "masters",
                                tags = emptyList(),
                                plies = plies,
                            ),
                        ) ?: return@OpeningDetailScreen "opening no longer exists"
                        detailOpening = updated
                        onCustomOpeningsChanged()
                        null
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
fun NewOpeningDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, eco: String, side: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var eco by remember { mutableStateOf("") }
    var side by remember { mutableStateOf("white") }
    val canCreate = name.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("new opening") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("opening name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = eco,
                    onValueChange = { eco = it },
                    label = { Text("ECO (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { side = "white" },
                        enabled = side != "white",
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("white")
                    }
                    TextButton(
                        onClick = { side = "black" },
                        enabled = side != "black",
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("black")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), eco.trim(), side) },
                enabled = canCreate,
            ) {
                Text("create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("cancel")
            }
        },
    )
}

@Composable
fun OpeningCatalogue(
    openings: List<OpeningSummary>,
    tab: AppTab,
    progressStore: AndroidProgressStore,
    progressRevision: Int,
    onOpeningSelected: (OpeningSummary) -> Unit,
    onCreateOpening: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val indexedOpenings = openings.mapIndexed { index, opening -> index to opening }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CatalogueHeader(
                tab = tab,
                openingCount = openings.size,
            )
            if (tab == AppTab.Library) {
                Button(
                    onClick = onCreateOpening,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("new opening")
                }
            }
        }
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
    onDeleteOpening: () -> Unit,
    onAddLine: (lineName: String, sanText: String) -> String?,
    modifier: Modifier = Modifier,
) {
    val masteryThreshold = remember(settingsRevision) { settingsStore.masteryThreshold }
    var showDeleteConfirmation by remember(opening.id) { mutableStateOf(false) }
    var showNewLine by remember(opening.id) { mutableStateOf(false) }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("delete opening?") },
            text = { Text("This deletes ${opening.name.toDisplayName()} and all of its lines.") },
            confirmButton = {
                TextButton(onClick = onDeleteOpening) {
                    Text("delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("cancel")
                }
            },
        )
    }
    if (showNewLine) {
        NewLineDialog(
            onDismiss = { showNewLine = false },
            onSave = { lineName, sanText ->
                onAddLine(lineName, sanText).also { error ->
                    if (error == null) showNewLine = false
                }
            },
        )
    }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!opening.isSeed) {
                        TextButton(onClick = { showDeleteConfirmation = true }) {
                            Text("delete")
                        }
                        TextButton(onClick = { showNewLine = true }) {
                            Text("add line")
                        }
                    }
                    Button(
                        onClick = { opening.lines.firstOrNull()?.let(onStartDrill) },
                        enabled = opening.lines.isNotEmpty(),
                    ) {
                        Text("drill all")
                    }
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
fun NewLineDialog(
    onDismiss: () -> Unit,
    onSave: (lineName: String, sanText: String) -> String?,
) {
    var lineName by remember { mutableStateOf("") }
    var sanText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val canSave = lineName.trim().isNotEmpty() && sanText.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("new line") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = lineName,
                    onValueChange = {
                        lineName = it
                        errorMessage = null
                    },
                    label = { Text("line name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sanText,
                    onValueChange = {
                        sanText = it
                        errorMessage = null
                    },
                    label = { Text("moves (SAN)") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    errorMessage = onSave(lineName.trim(), sanText.trim())
                },
                enabled = canSave,
            ) {
                Text("save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("cancel")
            }
        },
    )
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
    onOpenSettings: () -> Unit,
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
    var expectedMoveArrow by remember(line) { mutableStateOf<BoardArrow?>(null) }
    var madeMistake by remember(line) { mutableStateOf(false) }
    var completedViaShowLine by remember(line) { mutableStateOf(false) }
    var completionRecorded by remember(line) { mutableStateOf(false) }
    var userThinkingMillis by remember(line) { mutableLongStateOf(0L) }
    var lastPromptAtMillis by remember(line) { mutableStateOf<Long?>(null) }
    var timingEligible by remember(line) { mutableStateOf(false) }
    var completionWasSpeedy by remember(line) { mutableStateOf(false) }
    var wasLearnedAtSessionStart by remember(line) { mutableStateOf(false) }
    var confettiTrigger by remember(line) { mutableIntStateOf(0) }
    var playoutHandle by remember(line) { mutableStateOf(0L) }
    var playoutStartFen by remember(line) { mutableStateOf<String?>(null) }
    var playoutPositionFen by remember(line) { mutableStateOf<String?>(null) }
    var playoutSelectedSquare by remember(line) { mutableStateOf<String?>(null) }
    var playoutFeedback by remember(line) { mutableStateOf<String?>(null) }
    var pendingPromotion by remember(line) { mutableStateOf<PendingPromotionMove?>(null) }
    var pendingPlayoutConfirmation by remember(line) { mutableStateOf<PlayoutConfirmationAction?>(null) }
    var playoutMoves by remember(line) { mutableStateOf(emptyList<SharedPlayoutMoveSummary>()) }
    var moveQualityAnnotation by remember(line) { mutableStateOf<MoveQualityAnnotation?>(null) }
    var playoutMovePending by remember(line) { mutableStateOf(false) }
    var playoutHintPending by remember(line) { mutableStateOf(false) }
    var playoutHintUci by remember(line) { mutableStateOf<String?>(null) }
    var engineResignationState by remember(line) {
        mutableIntStateOf(SHARED_PLAYOUT_ENGINE_RESIGNATION_NONE)
    }
    val coroutineScope = rememberCoroutineScope()
    val inPlayout = playoutHandle != 0L
    val visiblePlies = line.plies.take(currentPlyCount)
    val visibleMoveList = visiblePlies + playoutMoves.map { it.toPlySummary() }
    val displayedPositionFen = playoutPositionFen ?: currentPositionFen
    val activeSelectedCoordinate = if (inPlayout) playoutSelectedSquare else selectedSquare
    val legalTargets = remember(displayedPositionFen, activeSelectedCoordinate) {
        activeSelectedCoordinate?.let { source ->
            parseSharedLegalTargets(
                SharedCoreBridge.legalTargetsJson(displayedPositionFen, source),
            )
        }.orEmpty()
    }
    val legalTargetCoordinates = legalTargets
        .filterNot { it.isCapture }
        .mapTo(mutableSetOf()) { it.square }
    val captureTargetCoordinates = legalTargets
        .filter { it.isCapture }
        .mapTo(mutableSetOf()) { it.square }
    val highlightedMoveUci = if (inPlayout) {
        playoutMoves.lastOrNull()?.uci ?: visiblePlies.lastOrNull()?.uci.orEmpty()
    } else {
        visiblePlies.lastOrNull()?.uci.orEmpty()
    }
    val board = remember(displayedPositionFen, highlightedMoveUci) {
        boardSquaresFromFen(
            fen = displayedPositionFen,
            highlightedMove = highlightedMoveUci,
        )
    }
    val nextPly = if (inPlayout) null else line.plies.getOrNull(currentPlyCount)
    val hintCoordinate = if (inPlayout) {
        playoutHintUci
            ?.takeIf { hintShown && !solutionShown }
            ?.take(2)
            ?.takeIf { it.isBoardCoordinate() }
    } else if (hintShown && !solutionShown) {
        nextPly?.fromCoordinate()
    } else {
        null
    }
    val solutionCoordinates = if (inPlayout) {
        if (solutionShown) highlightedSquaresForUci(playoutHintUci.orEmpty()) else emptySet()
    } else if (solutionShown) {
        nextPly?.moveCoordinates().orEmpty()
    } else {
        emptySet()
    }
    val boardArrow = if (inPlayout) {
        if (solutionShown) boardArrowFromUci(playoutHintUci.orEmpty()) else null
    } else if (solutionShown) {
        nextPly?.boardArrow()
    } else {
        expectedMoveArrow
    }

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
        expectedMoveArrow = null
        madeMistake = restoredSnapshot?.madeMistake == true
        completedViaShowLine = false
        completionRecorded = restoredSnapshot?.phase == SNAPSHOT_PHASE_PLAYOUT
        userThinkingMillis = 0L
        timingEligible = restoredSnapshot == null
        completionWasSpeedy = false
        wasLearnedAtSessionStart = progressStore.lineProgress(opening, line).isLearned
        confettiTrigger = 0
        lastPromptAtMillis = if (timingEligible && snapshot.plyIndex < line.plies.size) {
            SystemClock.elapsedRealtime()
        } else {
            null
        }
        playoutHandle = 0L
        playoutStartFen = null
        playoutPositionFen = null
        playoutSelectedSquare = null
        playoutFeedback = null
        pendingPromotion = null
        pendingPlayoutConfirmation = null
        playoutMoves = emptyList()
        moveQualityAnnotation = null
        playoutMovePending = false
        playoutHintPending = false
        playoutHintUci = null
        engineResignationState = SHARED_PLAYOUT_ENGINE_RESIGNATION_NONE
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
                playoutHandle = playout
                playoutStartFen = restoredStartFen
                moveQualityAnnotation = null
                val restoredMoves = !movesJson.isNullOrBlank() &&
                    SharedCoreBridge.restoreSharedPlayoutMoves(playout, movesJson) == 1
                if (restoredMoves) {
                    playoutPositionFen = sharedPlayoutPositionFen(
                        playout,
                        restoredSnapshot.playoutFen ?: restoredStartFen,
                    )
                    playoutMoves = parseSharedPlayoutMoves(SharedCoreBridge.sharedPlayoutMovesJson(playout))
                    playoutFeedback = "resumed playout"
                    engineResignationState = SharedCoreBridge.sharedPlayoutEngineResignation(playout)
                } else {
                    playoutMovePending = true
                    playoutFeedback = "engine thinking"
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            SharedCoreBridge.bootstrapSharedPlayoutSession(playout)
                        }
                        if (playoutHandle != playout) return@launch
                        playoutMovePending = false
                        playoutPositionFen = sharedPlayoutPositionFen(
                            playout,
                            restoredSnapshot.playoutFen ?: restoredStartFen,
                        )
                        playoutMoves = parseSharedPlayoutMoves(
                            SharedCoreBridge.sharedPlayoutMovesJson(playout),
                        )
                        playoutFeedback = playoutStatusLabel(
                            SharedCoreBridge.sharedPlayoutStatus(playout),
                            SharedCoreBridge.sharedPlayoutGameOverReason(playout),
                            opening.side,
                        )
                        engineResignationState =
                            SharedCoreBridge.sharedPlayoutEngineResignation(playout)
                    }
                }
            }
        }
        onDispose {
            if (handle != 0L) {
                SharedCoreBridge.releaseSharedDrillSession(handle)
            }
            if (sharedDrillHandle == handle) {
                sharedDrillHandle = 0L
            }
        }
    }

    val effectPlayoutHandle = playoutHandle
    DisposableEffect(effectPlayoutHandle) {
        val handle = effectPlayoutHandle
        onDispose {
            if (handle != 0L) {
                SharedCoreBridge.releaseSharedPlayoutSession(handle)
            }
        }
    }

    fun submitPlayoutMove(playedUci: String) {
        if (playoutMovePending || playoutHintPending || playoutHandle == 0L) return
        pendingPromotion = null
        hintShown = false
        solutionShown = false
        playoutHintUci = null
        playoutMovePending = true
        playoutSelectedSquare = null
        playoutFeedback = "engine thinking"
        val submittedHandle = playoutHandle
        val fallbackFen = displayedPositionFen
        coroutineScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                SharedCoreBridge.submitSharedPlayoutMove(submittedHandle, playedUci)
            }
            if (playoutHandle != submittedHandle) return@launch
            playoutMovePending = false
            when (outcome) {
                SHARED_PLAYOUT_ACCEPTED, SHARED_PLAYOUT_GAME_OVER -> {
                    playoutPositionFen = sharedPlayoutPositionFen(submittedHandle, fallbackFen)
                    drillSnapshotStore.savePlayout(
                        opening = opening,
                        line = line,
                        positionFen = playoutPositionFen ?: fallbackFen,
                        startingFen = playoutStartFen ?: currentPositionFen,
                        movesJson = SharedCoreBridge.sharedPlayoutMovesJson(submittedHandle).orEmpty(),
                        madeMistake = madeMistake,
                        engineLevel = settingsStore.engineLevel,
                    )
                    playMoveSound(settingsStore, soundPlayer)
                    playoutMoves = parseSharedPlayoutMoves(
                        SharedCoreBridge.sharedPlayoutMovesJson(submittedHandle),
                    )
                    moveQualityAnnotation = latestMoveQualityAnnotation(playoutMoves)
                    engineResignationState = SharedCoreBridge.sharedPlayoutEngineResignation(submittedHandle)
                    playoutFeedback = playoutStatusLabel(
                        SharedCoreBridge.sharedPlayoutStatus(submittedHandle),
                        SharedCoreBridge.sharedPlayoutGameOverReason(submittedHandle),
                        opening.side,
                    )
                }

                SHARED_PLAYOUT_ILLEGAL_MOVE -> {
                    playWrongMoveSound(settingsStore, soundPlayer)
                    playoutFeedback = "Illegal move"
                }

                else -> {
                    playoutFeedback = "Move unavailable"
                }
            }
        }
    }

    fun requestPlayoutHint() {
        if (playoutHintPending || playoutMovePending || playoutHandle == 0L) return
        playoutHintPending = true
        playoutFeedback = "finding hint"
        val requestedHandle = playoutHandle
        val requestedFen = displayedPositionFen
        coroutineScope.launch {
            val hint = withContext(Dispatchers.IO) {
                SharedCoreBridge.sharedPlayoutBestMove(requestedHandle)
            }
            if (playoutHandle != requestedHandle || displayedPositionFen != requestedFen) return@launch
            playoutHintPending = false
            playoutHintUci = hint?.takeIf { boardArrowFromUci(it) != null }
            playoutFeedback = if (playoutHintUci == null) {
                "Hint unavailable"
            } else {
                "playout · your move"
            }
        }
    }

    fun stopThinkingClock() {
        val promptAt = lastPromptAtMillis ?: return
        userThinkingMillis += (SystemClock.elapsedRealtime() - promptAt).coerceAtLeast(0L)
        lastPromptAtMillis = null
    }

    fun restartThinkingClock() {
        lastPromptAtMillis = if (timingEligible && currentPlyCount < line.plies.size) {
            SystemClock.elapsedRealtime()
        } else {
            null
        }
    }

    fun submitDrillMove(playedUci: String) {
        pendingPromotion = null
        stopThinkingClock()
        when (SharedCoreBridge.submitSharedDrillMove(sharedDrillHandle, playedUci)) {
            SHARED_DRILL_ACCEPTED, SHARED_DRILL_LINE_COMPLETE -> {
                val snapshot = sharedDrillSnapshot(sharedDrillHandle, fallbackPlyIndex = currentPlyCount)
                currentPlyCount = snapshot.plyIndex.coerceAtLeast(currentPlyCount)
                currentPositionFen = snapshot.positionFen
                if (currentPlyCount >= line.plies.size) {
                    completionWasSpeedy = isSpeedyDrillCompletion(
                        userThinkingMillis = userThinkingMillis,
                        linePlyCount = line.plies.size,
                        timingEligible = timingEligible,
                        completedViaShowLine = completedViaShowLine,
                    )
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
                            val isLearned = progressStore.lineProgress(opening, line).isLearned
                            if (shouldTriggerLearningConfetti(
                                    wasLearned = wasLearnedAtSessionStart,
                                    isLearned = isLearned,
                                    completedViaShowLine = completedViaShowLine,
                                )
                            ) {
                                confettiTrigger += 1
                                wasLearnedAtSessionStart = true
                            }
                            onProgressChanged()
                        },
                    )
                } else {
                    playMoveSound(settingsStore, soundPlayer)
                    drillSnapshotStore.save(opening, line, currentPlyCount, madeMistake)
                    restartThinkingClock()
                }
                selectedSquare = null
                feedback = null
                hintShown = false
                solutionShown = false
                expectedMoveArrow = null
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
                expectedMoveArrow = nextPly?.boardArrow()
                showLineIsPlaying = false
                restartThinkingClock()
            }

            else -> {
                selectedSquare = null
                feedback = "Illegal move"
                hintShown = false
                expectedMoveArrow = null
                showLineIsPlaying = false
                restartThinkingClock()
            }
        }
    }

    fun dismissPromotionDialog() {
        pendingPromotion = null
        if (inPlayout) {
            playoutSelectedSquare = null
        } else {
            selectedSquare = null
        }
    }

    fun completePromotion(promotion: Char) {
        val pending = pendingPromotion ?: return
        val playedUci = "${pending.from}${pending.to}$promotion"
        if (pending.inPlayout) {
            submitPlayoutMove(playedUci)
        } else {
            submitDrillMove(playedUci)
        }
    }

    fun exitPlayout() {
        playoutHandle = 0L
        playoutStartFen = null
        playoutPositionFen = null
        playoutSelectedSquare = null
        playoutFeedback = null
        pendingPromotion = null
        pendingPlayoutConfirmation = null
        playoutMoves = emptyList()
        moveQualityAnnotation = null
        playoutMovePending = false
        playoutHintPending = false
        playoutHintUci = null
        hintShown = false
        solutionShown = false
        engineResignationState = SHARED_PLAYOUT_ENGINE_RESIGNATION_NONE
        drillSnapshotStore.clear()
    }

    fun offerPlayoutDraw() {
        if (playoutMovePending || playoutHintPending || playoutHandle == 0L) return
        pendingPlayoutConfirmation = null
        playoutMovePending = true
        playoutFeedback = "draw offered · awaiting engine reply"
        val offeredHandle = playoutHandle
        coroutineScope.launch {
            val status = withContext(Dispatchers.IO) {
                SharedCoreBridge.offerSharedPlayoutDraw(offeredHandle)
            }
            if (playoutHandle != offeredHandle) return@launch
            playoutMovePending = false
            playoutFeedback = if (status == SHARED_PLAYOUT_GAME_OVER_STATUS) {
                "draw agreed"
            } else {
                playoutStatusLabel(status)
            }
            if (status == SHARED_PLAYOUT_GAME_OVER_STATUS) {
                drillSnapshotStore.clear()
            }
            engineResignationState =
                SharedCoreBridge.sharedPlayoutEngineResignation(offeredHandle)
        }
    }

    fun resignPlayout() {
        pendingPlayoutConfirmation = null
        val status = SharedCoreBridge.resignSharedPlayout(playoutHandle)
        playoutFeedback = if (status == SHARED_PLAYOUT_GAME_OVER_STATUS) {
            "you resigned"
        } else {
            playoutStatusLabel(status)
        }
        if (status == SHARED_PLAYOUT_GAME_OVER_STATUS) {
            drillSnapshotStore.clear()
        }
        engineResignationState = SharedCoreBridge.sharedPlayoutEngineResignation(playoutHandle)
    }

    fun confirmPlayoutAction(action: PlayoutConfirmationAction) {
        when (action) {
            PlayoutConfirmationAction.OfferDraw -> offerPlayoutDraw()
            PlayoutConfirmationAction.Resign -> resignPlayout()
            PlayoutConfirmationAction.Exit -> exitPlayout()
        }
    }

    fun startPlayout() {
        if (playoutMovePending) return
        val handle = SharedCoreBridge.createSharedPlayoutSession(
            currentPositionFen,
            opening.side.toSharedDrillUserSide(),
            settingsStore.engineLevel,
            settingsStore.moveAnalysisDepth,
        )
        if (handle == 0L) {
            feedback = "Playout unavailable"
            return
        }

        playoutHandle = handle
        playoutStartFen = currentPositionFen
        playoutSelectedSquare = null
        pendingPromotion = null
        playoutMoves = emptyList()
        moveQualityAnnotation = null
        playoutMovePending = true
        playoutHintPending = false
        playoutHintUci = null
        hintShown = false
        solutionShown = false
        engineResignationState = SHARED_PLAYOUT_ENGINE_RESIGNATION_NONE
        playoutFeedback = "engine thinking"
        val startingFen = currentPositionFen
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                SharedCoreBridge.bootstrapSharedPlayoutSession(handle)
            }
            if (playoutHandle != handle) return@launch
            playoutMovePending = false
            playoutPositionFen = sharedPlayoutPositionFen(handle, startingFen)
            playoutMoves = parseSharedPlayoutMoves(
                SharedCoreBridge.sharedPlayoutMovesJson(handle),
            )
            drillSnapshotStore.savePlayout(
                opening = opening,
                line = line,
                positionFen = playoutPositionFen ?: startingFen,
                startingFen = startingFen,
                movesJson = SharedCoreBridge.sharedPlayoutMovesJson(handle).orEmpty(),
                madeMistake = madeMistake,
                engineLevel = settingsStore.engineLevel,
            )
            engineResignationState = SharedCoreBridge.sharedPlayoutEngineResignation(handle)
            playoutFeedback = playoutStatusLabel(
                SharedCoreBridge.sharedPlayoutStatus(handle),
                SharedCoreBridge.sharedPlayoutGameOverReason(handle),
                opening.side,
            )
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
            expectedMoveArrow = null
            pendingPromotion = null
            if (outcome != SHARED_DRILL_ACCEPTED && outcome != SHARED_DRILL_LINE_COMPLETE) {
                showLineIsPlaying = false
            }
            if (outcome == SHARED_DRILL_LINE_COMPLETE || currentPlyCount >= line.plies.size) {
                completedViaShowLine = true
                completionWasSpeedy = false
                drillSnapshotStore.clear()
            } else {
                drillSnapshotStore.save(opening, line, currentPlyCount, madeMistake)
            }
        }
        if (currentPlyCount >= line.plies.size) {
            showLineIsPlaying = false
        }
    }

    LaunchedEffect(moveQualityAnnotation?.id, settingsRevision) {
        val annotation = moveQualityAnnotation ?: return@LaunchedEffect
        delay(settingsStore.moveQualityBadgeMs.toLong())
        if (moveQualityAnnotation?.id == annotation.id) {
            moveQualityAnnotation = null
        }
    }

    pendingPromotion?.let {
        AlertDialog(
            onDismissRequest = { dismissPromotionDialog() },
            title = { Text("promote pawn") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf('q', 'r', 'b', 'n').forEach { promotion ->
                        TextButton(
                            onClick = { completePromotion(promotion) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(promotionPieceName(promotion))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dismissPromotionDialog() }) {
                    Text("cancel")
                }
            },
        )
    }

    pendingPlayoutConfirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingPlayoutConfirmation = null },
            title = { Text(playoutConfirmationTitle(action)) },
            text = { Text(playoutConfirmationMessage(action)) },
            confirmButton = {
                TextButton(onClick = { confirmPlayoutAction(action) }) {
                    Text(playoutConfirmationConfirmLabel(action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlayoutConfirmation = null }) {
                    Text("cancel")
                }
            },
        )
    }

    if (engineResignationState == SHARED_PLAYOUT_ENGINE_RESIGNATION_PENDING) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("engine resigns") },
            text = { Text("Stockfish thinks the position is lost. Accept the resignation or keep playing.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val status = SharedCoreBridge.acceptSharedPlayoutEngineResignation(playoutHandle)
                        engineResignationState = SharedCoreBridge.sharedPlayoutEngineResignation(playoutHandle)
                        playoutFeedback = if (status == SHARED_PLAYOUT_GAME_OVER_STATUS) {
                            "engine resigned"
                        } else {
                            playoutStatusLabel(status)
                        }
                        drillSnapshotStore.clear()
                    },
                ) {
                    Text("accept")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val status = SharedCoreBridge.declineSharedPlayoutEngineResignation(playoutHandle)
                        engineResignationState = SharedCoreBridge.sharedPlayoutEngineResignation(playoutHandle)
                        playoutFeedback = playoutStatusLabel(status)
                        drillSnapshotStore.savePlayout(
                            opening = opening,
                            line = line,
                            positionFen = playoutPositionFen ?: displayedPositionFen,
                            startingFen = playoutStartFen ?: currentPositionFen,
                            movesJson = SharedCoreBridge.sharedPlayoutMovesJson(playoutHandle).orEmpty(),
                            madeMistake = madeMistake,
                            engineLevel = settingsStore.engineLevel,
                        )
                    },
                ) {
                    Text("keep playing")
                }
            },
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourcePill(source = line.source)
                    TextButton(onClick = onOpenSettings) {
                        Text("settings")
                    }
                }
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
                hintCoordinate = hintCoordinate,
                solutionCoordinates = solutionCoordinates,
                legalTargetCoordinates = legalTargetCoordinates,
                captureTargetCoordinates = captureTargetCoordinates,
                boardArrow = boardArrow,
                moveQualityAnnotation = if (inPlayout) moveQualityAnnotation else null,
                canDragCoordinate = { coordinate ->
                    if (playoutMovePending || playoutHintPending) {
                        false
                    } else if (inPlayout) {
                        canStartPlayoutMove(coordinate, board, opening.side)
                    } else {
                        canStartDrillMove(coordinate, board, nextPly, opening.side)
                    }
                },
                onSquareDrag = { from, to ->
                    if (isPromotionMove(from, to, board)) {
                        pendingPromotion = PendingPromotionMove(from, to, inPlayout = inPlayout)
                    } else if (inPlayout) {
                        submitPlayoutMove("$from$to")
                    } else {
                        submitDrillMove("$from$to")
                    }
                },
                onSquareClick = { coordinate ->
                    if (inPlayout) {
                        if (playoutMovePending || playoutHintPending) return@BoardGrid
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

                        if (isPromotionMove(selected, coordinate, board)) {
                            pendingPromotion = PendingPromotionMove(selected, coordinate, inPlayout = true)
                        } else {
                            submitPlayoutMove("$selected$coordinate")
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
                        if (isPromotionMove(selected, coordinate, board)) {
                            pendingPromotion = PendingPromotionMove(selected, coordinate, inPlayout = false)
                        } else {
                            submitDrillMove("$selected$coordinate")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!inPlayout && currentPlyCount >= line.plies.size) {
                DrillCompletionBanner(
                    perfect = !madeMistake && !completedViaShowLine,
                    speedy = completionWasSpeedy,
                    onPlayOut = { startPlayout() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
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
            }

            MoveListFlow(
                plies = visibleMoveList,
                drillPlyCount = if (inPlayout) visiblePlies.size else null,
                currentMoveLabel = if (inPlayout) {
                    "move ${visibleMoveList.size / 2 + 1}"
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (inPlayout) {
                TextButton(
                    onClick = {
                        when {
                            !hintShown && !solutionShown -> {
                                hintShown = true
                                requestPlayoutHint()
                            }

                            hintShown -> {
                                hintShown = false
                                solutionShown = true
                                if (playoutHintUci == null) requestPlayoutHint()
                            }

                            else -> {
                                solutionShown = false
                                playoutHintUci = null
                            }
                        }
                    },
                    enabled = !playoutMovePending &&
                        !playoutHintPending &&
                        SharedCoreBridge.sharedPlayoutStatus(playoutHandle) == SHARED_PLAYOUT_WAITING_STATUS,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            playoutHintPending -> "finding hint..."
                            solutionShown -> "hide solution"
                            hintShown -> "show solution"
                            else -> "show hint"
                        },
                    )
                }
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
                            pendingPromotion = null
                            playoutMoves = parseSharedPlayoutMoves(SharedCoreBridge.sharedPlayoutMovesJson(playoutHandle))
                            moveQualityAnnotation = null
                            playoutHintUci = null
                            hintShown = false
                            solutionShown = false
                            engineResignationState = SharedCoreBridge.sharedPlayoutEngineResignation(playoutHandle)
                            playoutFeedback = "playout · your move"
                        },
                        enabled = !playoutMovePending &&
                            !playoutHintPending &&
                            SharedCoreBridge.sharedPlayoutPlyIndex(playoutHandle) > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("undo")
                    }
                    TextButton(
                        onClick = { pendingPlayoutConfirmation = PlayoutConfirmationAction.Exit },
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
                        onClick = { pendingPlayoutConfirmation = PlayoutConfirmationAction.OfferDraw },
                        enabled = !playoutMovePending &&
                            !playoutHintPending &&
                            SharedCoreBridge.sharedPlayoutStatus(playoutHandle) != SHARED_PLAYOUT_GAME_OVER_STATUS,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("offer draw")
                    }
                    TextButton(
                        onClick = { pendingPlayoutConfirmation = PlayoutConfirmationAction.Resign },
                        enabled = !playoutMovePending &&
                            !playoutHintPending &&
                            SharedCoreBridge.sharedPlayoutStatus(playoutHandle) != SHARED_PLAYOUT_GAME_OVER_STATUS,
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
                            when {
                                !hintShown && !solutionShown -> {
                                    hintShown = true
                                }

                                hintShown -> {
                                    hintShown = false
                                    solutionShown = true
                                }

                                else -> {
                                    solutionShown = false
                                }
                            }
                        },
                        enabled = nextPly != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            when {
                                solutionShown -> "hide solution"
                                hintShown -> "show solution"
                                else -> "show hint"
                            },
                        )
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
                            completionWasSpeedy = false
                            restartThinkingClock()
                            drillSnapshotStore.save(opening, line, currentPlyCount, madeMistake)
                            selectedSquare = null
                            feedback = null
                            hintShown = false
                            solutionShown = false
                            expectedMoveArrow = null
                            pendingPromotion = null
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
                            userThinkingMillis = 0L
                            timingEligible = true
                            completionWasSpeedy = false
                            restartThinkingClock()
                            drillSnapshotStore.clear()
                            selectedSquare = null
                            feedback = null
                            hintShown = false
                            solutionShown = false
                            expectedMoveArrow = null
                            pendingPromotion = null
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
                            timingEligible = false
                            lastPromptAtMillis = null
                            completionWasSpeedy = false
                            selectedSquare = null
                            feedback = null
                            hintShown = false
                            solutionShown = false
                            expectedMoveArrow = null
                            pendingPromotion = null
                        }
                    },
                    enabled = currentPlyCount < line.plies.size || showLineIsPlaying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (showLineIsPlaying) "pause" else "show line")
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        ConfettiBurst(
            trigger = confettiTrigger,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun ConfettiBurst(
    trigger: Int,
    modifier: Modifier = Modifier,
) {
    val animation = remember { Animatable(1f) }
    val particles = remember(trigger) {
        if (trigger > 0) generateConfettiParticles(trigger) else emptyList()
    }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        animation.snapTo(0f)
        animation.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2_400),
        )
    }

    Canvas(modifier = modifier) {
        if (trigger <= 0 || animation.value >= 1f) return@Canvas
        val elapsedSeconds = animation.value * 2.4f
        val alpha = (1f - animation.value).coerceIn(0f, 1f)
        particles.forEach { particle ->
            val x = particle.originX * size.width +
                particle.velocityX * size.width * elapsedSeconds
            val y = particle.originY * size.height +
                particle.velocityY * size.height * elapsedSeconds +
                0.5f * 0.38f * size.height * elapsedSeconds * elapsedSeconds
            val particleSize = particle.sizeFraction * size.width
            drawRect(
                color = particle.color.copy(alpha = alpha),
                topLeft = Offset(x - particleSize / 2f, y - particleSize / 2f),
                size = Size(particleSize, particleSize),
            )
        }
    }
}

fun generateConfettiParticles(
    seed: Int,
    count: Int = 90,
): List<ConfettiParticle> {
    val random = Random(seed)
    val colors = listOf(
        Color(0xFFE84D4D),
        Color(0xFFF29D38),
        Color(0xFFF2D14B),
        Color(0xFF4AAE73),
        Color(0xFF3D82D7),
        Color(0xFF8B62C7),
        Color(0xFFD75C9A),
        Color(0xFF45B8C4),
    )
    return List(count.coerceAtLeast(0)) {
        val angle = random.nextDouble() * (2.0 * PI)
        val speed = 0.16f + random.nextFloat() * 0.28f
        ConfettiParticle(
            originX = 0.46f + random.nextFloat() * 0.08f,
            originY = 0.34f + random.nextFloat() * 0.08f,
            velocityX = cos(angle).toFloat() * speed,
            velocityY = sin(angle).toFloat() * speed,
            sizeFraction = 0.006f + random.nextFloat() * 0.006f,
            color = colors[random.nextInt(colors.size)],
        )
    }
}

@Composable
fun DrillCompletionBanner(
    perfect: Boolean,
    speedy: Boolean,
    onPlayOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFE8F4EC),
        border = BorderStroke(1.dp, Color(0xFF87B697)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (perfect) "perfect" else "line complete",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF28613A),
                )
                if (speedy) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0xFFFFE6A3),
                    ) {
                        Text(
                            text = "speedy",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6D5100),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Button(onClick = onPlayOut) {
                Text("play it out")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveListFlow(
    plies: List<PlySummary>,
    drillPlyCount: Int?,
    currentMoveLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    if (plies.isEmpty()) {
        Text(
            text = "No moves",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            modifier = modifier,
        )
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(max = 92.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                plies.forEachIndexed { index, ply ->
                    val isDrillPrefix = drillPlyCount != null && index < drillPlyCount
                    Text(
                        text = moveListToken(index, ply),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (isDrillPrefix) 0.52f else 0.82f,
                        ),
                    )
                }
            }
        }
        currentMoveLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }
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
    val previewArrow = remember(highlightedMove) { boardArrowFromUci(highlightedMove) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoardGrid(
            board = board,
            orientationSide = orientationSide,
            boardArrow = previewArrow,
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
    legalTargetCoordinates: Set<String> = emptySet(),
    captureTargetCoordinates: Set<String> = emptySet(),
    boardArrow: BoardArrow? = null,
    moveQualityAnnotation: MoveQualityAnnotation? = null,
    canDragCoordinate: ((String) -> Boolean)? = null,
    onSquareDrag: ((String, String) -> Unit)? = null,
    onSquareClick: ((String) -> Unit)? = null,
) {
    val displayedSquares = remember(board, orientationSide) {
        displayedBoardSquares(board, orientationSide)
    }
    val initialAnimatedPieces = remember { boardAnimationPieces(board, nextId = 1) }
    var nextPieceId by remember { mutableIntStateOf(initialAnimatedPieces.size + 1) }
    var animatedPieces by remember { mutableStateOf(initialAnimatedPieces) }
    var boardPixelSize by remember { mutableStateOf(IntSize.Zero) }
    var draggedCoordinate by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(board) {
        val reconciled = reconcileAnimatedBoardPieces(
            previous = animatedPieces,
            board = board,
            nextId = nextPieceId,
        )
        animatedPieces = reconciled.pieces
        nextPieceId = reconciled.nextId
    }

    val boardDragModifier = if (onSquareDrag == null) {
        Modifier
    } else {
        Modifier
            .onSizeChanged { boardPixelSize = it }
            .pointerInput(board, orientationSide, boardPixelSize, canDragCoordinate) {
                detectDragGestures(
                    onDragStart = { position ->
                        val coordinate = boardCoordinateAt(
                            position = position,
                            boardSide = boardPixelSize.width.toFloat(),
                            orientationSide = orientationSide,
                        )
                        if (coordinate != null && canDragCoordinate?.invoke(coordinate) != false) {
                            draggedCoordinate = coordinate
                            dragPosition = position
                        }
                    },
                    onDrag = { change, _ ->
                        if (draggedCoordinate != null) {
                            change.consume()
                            dragPosition = change.position
                        }
                    },
                    onDragCancel = {
                        draggedCoordinate = null
                        dragPosition = null
                    },
                    onDragEnd = {
                        val from = draggedCoordinate
                        val to = dragPosition?.let { position ->
                            boardCoordinateAt(
                                position = position,
                                boardSide = boardPixelSize.width.toFloat(),
                                orientationSide = orientationSide,
                            )
                        }
                        draggedCoordinate = null
                        dragPosition = null
                        if (from != null && to != null && from != to) {
                            onSquareDrag(from, to)
                        }
                    },
                )
            }
    }

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF6F655A), RoundedCornerShape(6.dp)),
        color = Color(0xFFEEE2D2),
    ) {
        Box(modifier = Modifier.fillMaxSize().then(boardDragModifier)) {
            Column(modifier = Modifier.fillMaxSize()) {
                displayedSquares.chunked(8).forEach { rankSquares ->
                    Row(modifier = Modifier.weight(1f)) {
                        rankSquares.forEach { square ->
                            BoardSquareCell(
                                square = square,
                                dark = isDarkBoardSquare(square.file, square.rank),
                                orientationSide = orientationSide,
                                selected = square.coordinate == selectedCoordinate,
                                hinted = square.coordinate == hintCoordinate,
                                solution = square.coordinate in solutionCoordinates,
                                legalTarget = square.coordinate in legalTargetCoordinates,
                                captureTarget = square.coordinate in captureTargetCoordinates,
                                moveQuality = moveQualityAnnotation
                                    ?.takeIf { it.square == square.coordinate }
                                    ?.quality,
                                onClick = onSquareClick,
                                showPiece = false,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            AnimatedBoardPieces(
                pieces = animatedPieces,
                orientationSide = orientationSide,
                draggedCoordinate = draggedCoordinate,
                dragPosition = dragPosition,
                boardSidePixels = boardPixelSize.width.toFloat(),
            )
            boardArrow?.let { arrow ->
                BoardArrowOverlay(arrow = arrow, orientationSide = orientationSide)
            }
        }
    }
}

@Composable
private fun AnimatedBoardPieces(
    pieces: List<AnimatedBoardPiece>,
    orientationSide: String,
    draggedCoordinate: String?,
    dragPosition: Offset?,
    boardSidePixels: Float,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cellSize = maxWidth / 8
        val density = LocalDensity.current
        pieces.forEach { piece ->
            key(piece.id) {
                val column = boardDisplayColumn(piece.coordinate, orientationSide)
                val row = boardDisplayRow(piece.coordinate, orientationSide)
                val animatedX by animateDpAsState(
                    targetValue = cellSize * column,
                    animationSpec = tween(durationMillis = 90),
                    label = "piece-x-${piece.id}",
                )
                val animatedY by animateDpAsState(
                    targetValue = cellSize * row,
                    animationSpec = tween(durationMillis = 90),
                    label = "piece-y-${piece.id}",
                )
                val isDragged = piece.coordinate == draggedCoordinate && dragPosition != null
                val x = if (isDragged && boardSidePixels > 0f) {
                    with(density) { (dragPosition.x - boardSidePixels / 16f).toDp() }
                } else {
                    animatedX
                }
                val y = if (isDragged && boardSidePixels > 0f) {
                    with(density) { (dragPosition.y - boardSidePixels / 16f).toDp() }
                } else {
                    animatedY
                }
                pieceResourceId(piece.pieceCode)?.let { resourceId ->
                    Image(
                        painter = painterResource(resourceId),
                        contentDescription = pieceDescription(piece.pieceCode),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .offset(x = x, y = y)
                            .size(cellSize)
                            .padding(5.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun BoardArrowOverlay(
    arrow: BoardArrow,
    orientationSide: String,
) {
    val progress = remember(arrow) { Animatable(0.35f) }

    LaunchedEffect(arrow) {
        var fullLengthHoldMs = 650L
        while (true) {
            progress.snapTo(0.35f)
            delay(100)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 350,
                    easing = FastOutSlowInEasing,
                ),
            )
            delay(fullLengthHoldMs)
            fullLengthHoldMs = (fullLengthHoldMs * 2).coerceAtMost(41_600L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawBoardArrow(arrow, orientationSide, progress.value)
    }
}

private fun DrawScope.drawBoardArrow(
    arrow: BoardArrow,
    orientationSide: String,
    progress: Float = 1f,
) {
    val boardSide = min(size.width, size.height)
    val route = boardArrowRoute(arrow, orientationSide, boardSide) ?: return
    val clampedProgress = progress.coerceIn(0f, 1f)
    val fullLength = routeLength(route)
    if (fullLength == 0f || clampedProgress < 0.08f) return

    val cell = boardSide / 8f
    val currentLength = fullLength * clampedProgress
    val shaftStartInset = min(cell * 0.42f, currentLength * 0.25f)
    val shaftEndInset = min(cell * 0.34f, currentLength * 0.35f)
    val headScale = min(1f, currentLength / cell)
    val shaftStartDistance = shaftStartInset
    val shaftEndDistance = (currentLength - shaftEndInset)
        .coerceAtLeast(shaftStartDistance)
    val arrowColor = Color(0xCC2F74CC)
    drawRouteLine(
        route = route,
        startDistance = shaftStartDistance,
        endDistance = shaftEndDistance,
        color = arrowColor,
        strokeWidth = cell * 0.16f,
    )

    val headPoint = pointAtRouteDistance(route, currentLength) ?: return
    val angle = headPoint.angle
    val headLength = cell * 0.46f * headScale
    val headWidth = cell * 0.30f * headScale
    val leftAngle = angle + (PI * 0.82).toFloat()
    val rightAngle = angle - (PI * 0.82).toFloat()
    val head = Path().apply {
        moveTo(headPoint.offset.x, headPoint.offset.y)
        lineTo(
            headPoint.offset.x + cos(leftAngle) * headLength + cos(angle + PI.toFloat() / 2f) * headWidth * 0.22f,
            headPoint.offset.y + sin(leftAngle) * headLength + sin(angle + PI.toFloat() / 2f) * headWidth * 0.22f,
        )
        lineTo(
            headPoint.offset.x + cos(rightAngle) * headLength + cos(angle - PI.toFloat() / 2f) * headWidth * 0.22f,
            headPoint.offset.y + sin(rightAngle) * headLength + sin(angle - PI.toFloat() / 2f) * headWidth * 0.22f,
        )
        close()
    }
    drawPath(path = head, color = arrowColor)
}

private fun DrawScope.drawRouteLine(
    route: List<Offset>,
    startDistance: Float,
    endDistance: Float,
    color: Color,
    strokeWidth: Float,
) {
    var consumed = 0f
    route.zipWithNext().forEach { (segmentStart, segmentEnd) ->
        val segmentLength = distanceBetween(segmentStart, segmentEnd)
        if (segmentLength == 0f) return@forEach
        val segmentFrom = consumed
        val segmentTo = consumed + segmentLength
        val overlapFrom = startDistance.coerceIn(segmentFrom, segmentTo)
        val overlapTo = endDistance.coerceIn(segmentFrom, segmentTo)
        if (overlapTo > overlapFrom) {
            drawLine(
                color = color,
                start = interpolateSegment(
                    segmentStart,
                    segmentEnd,
                    (overlapFrom - segmentFrom) / segmentLength,
                ),
                end = interpolateSegment(
                    segmentStart,
                    segmentEnd,
                    (overlapTo - segmentFrom) / segmentLength,
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
        consumed = segmentTo
    }
}

private data class RoutePosition(
    val offset: Offset,
    val angle: Float,
)

private fun pointAtRouteDistance(
    route: List<Offset>,
    distance: Float,
): RoutePosition? {
    var consumed = 0f
    route.zipWithNext().forEach { (segmentStart, segmentEnd) ->
        val segmentLength = distanceBetween(segmentStart, segmentEnd)
        if (segmentLength == 0f) return@forEach
        val segmentTo = consumed + segmentLength
        if (distance <= segmentTo) {
            val t = ((distance - consumed) / segmentLength).coerceIn(0f, 1f)
            return RoutePosition(
                offset = interpolateSegment(segmentStart, segmentEnd, t),
                angle = atan2(segmentEnd.y - segmentStart.y, segmentEnd.x - segmentStart.x),
            )
        }
        consumed = segmentTo
    }

    val lastStart = route.dropLast(1).lastOrNull() ?: return null
    val lastEnd = route.lastOrNull() ?: return null
    return RoutePosition(
        offset = lastEnd,
        angle = atan2(lastEnd.y - lastStart.y, lastEnd.x - lastStart.x),
    )
}

private fun boardArrowRoute(
    arrow: BoardArrow,
    orientationSide: String,
    boardSide: Float,
): List<Offset>? {
    val start = boardArrowCenter(arrow.from, orientationSide, boardSide) ?: return null
    val end = boardArrowCenter(arrow.to, orientationSide, boardSide) ?: return null
    val corner = knightMoveCorner(arrow)
        ?.let { boardArrowCenter(it, orientationSide, boardSide) }
    return if (corner == null) {
        listOf(start, end)
    } else {
        listOf(start, corner, end)
    }
}

private fun routeLength(route: List<Offset>): Float =
    route.zipWithNext().sumOf { (start, end) ->
        distanceBetween(start, end).toDouble()
    }.toFloat()

private fun distanceBetween(start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    return sqrt(dx * dx + dy * dy)
}

private fun interpolateSegment(start: Offset, end: Offset, t: Float): Offset =
    Offset(
        x = start.x + (end.x - start.x) * t,
        y = start.y + (end.y - start.y) * t,
    )

private fun knightMoveCorner(arrow: BoardArrow): String? {
    if (!arrow.from.isBoardCoordinate() || !arrow.to.isBoardCoordinate()) return null
    val fromFile = arrow.from[0]
    val fromRank = arrow.from[1].digitToInt()
    val toFile = arrow.to[0]
    val toRank = arrow.to[1].digitToInt()
    val fileDelta = abs(toFile - fromFile)
    val rankDelta = abs(toRank - fromRank)
    if (!((fileDelta == 1 && rankDelta == 2) || (fileDelta == 2 && rankDelta == 1))) {
        return null
    }

    val cornerFile = fromFile + if (toFile > fromFile) 1 else -1
    val cornerRank = fromRank + if (toRank > fromRank) 1 else -1
    return "$cornerFile$cornerRank"
}

private fun boardArrowCenter(
    coordinate: String,
    orientationSide: String,
    boardSide: Float,
): Offset? {
    if (!coordinate.isBoardCoordinate()) return null
    val fileIndex = coordinate[0] - 'a'
    val rankIndex = coordinate[1].digitToInt() - 1
    val column = if (orientationSide.isBlackSide()) 7 - fileIndex else fileIndex
    val row = if (orientationSide.isBlackSide()) rankIndex else 7 - rankIndex
    val cell = boardSide / 8f
    return Offset(
        x = (column + 0.5f) * cell,
        y = (row + 0.5f) * cell,
    )
}

fun boardAnimationPieces(
    board: List<BoardSquare>,
    nextId: Int,
): List<AnimatedBoardPiece> =
    board
        .filter { it.pieceCode.isNotBlank() }
        .mapIndexed { index, square ->
            AnimatedBoardPiece(
                id = nextId + index,
                coordinate = square.coordinate,
                pieceCode = square.pieceCode,
            )
        }

fun reconcileAnimatedBoardPieces(
    previous: List<AnimatedBoardPiece>,
    board: List<BoardSquare>,
    nextId: Int,
): ReconciledAnimatedBoardPieces {
    val targets = board.filter { it.pieceCode.isNotBlank() }
    val unmatched = previous.toMutableList()
    val result = mutableListOf<AnimatedBoardPiece>()
    val remaining = mutableListOf<BoardSquare>()

    targets.forEach { target ->
        val index = unmatched.indexOfFirst {
            it.coordinate == target.coordinate && it.pieceCode == target.pieceCode
        }
        if (index >= 0) {
            result += unmatched.removeAt(index)
        } else {
            remaining += target
        }
    }

    val unmatchedAfterExact = mutableListOf<BoardSquare>()
    remaining.forEach { target ->
        val index = unmatched.indices
            .filter { unmatched[it].pieceCode == target.pieceCode }
            .minByOrNull { boardSquareDistance(unmatched[it].coordinate, target.coordinate) }
        if (index != null) {
            val piece = unmatched.removeAt(index)
            result += piece.copy(coordinate = target.coordinate)
        } else {
            unmatchedAfterExact += target
        }
    }

    var availableId = nextId
    unmatchedAfterExact.forEach { target ->
        val side = target.pieceCode.firstOrNull()
        val index = unmatched.indices
            .filter { unmatched[it].pieceCode.firstOrNull() == side }
            .minByOrNull { boardSquareDistance(unmatched[it].coordinate, target.coordinate) }
        if (index != null) {
            val piece = unmatched.removeAt(index)
            result += piece.copy(
                coordinate = target.coordinate,
                pieceCode = target.pieceCode,
            )
        } else {
            result += AnimatedBoardPiece(
                id = availableId++,
                coordinate = target.coordinate,
                pieceCode = target.pieceCode,
            )
        }
    }

    return ReconciledAnimatedBoardPieces(
        pieces = result,
        nextId = availableId,
    )
}

private fun boardSquareDistance(from: String, to: String): Int {
    if (!from.isBoardCoordinate() || !to.isBoardCoordinate()) return Int.MAX_VALUE
    val fileDelta = from[0] - to[0]
    val rankDelta = from[1] - to[1]
    return fileDelta * fileDelta + rankDelta * rankDelta
}

private fun boardDisplayColumn(coordinate: String, orientationSide: String): Int {
    val fileIndex = coordinate[0] - 'a'
    return if (orientationSide.isBlackSide()) 7 - fileIndex else fileIndex
}

private fun boardDisplayRow(coordinate: String, orientationSide: String): Int {
    val rankIndex = coordinate[1].digitToInt() - 1
    return if (orientationSide.isBlackSide()) rankIndex else 7 - rankIndex
}

fun boardCoordinateAt(
    position: Offset,
    boardSide: Float,
    orientationSide: String,
): String? {
    if (boardSide <= 0f ||
        position.x < 0f ||
        position.y < 0f ||
        position.x >= boardSide ||
        position.y >= boardSide
    ) {
        return null
    }

    val cellSize = boardSide / 8f
    val displayedColumn = (position.x / cellSize).toInt()
    val displayedRow = (position.y / cellSize).toInt()
    val fileIndex = if (orientationSide.isBlackSide()) 7 - displayedColumn else displayedColumn
    val rank = if (orientationSide.isBlackSide()) displayedRow + 1 else 8 - displayedRow
    return "${'a' + fileIndex}$rank"
}

@Composable
fun BoardSquareCell(
    square: BoardSquare,
    dark: Boolean,
    orientationSide: String = "white",
    selected: Boolean = false,
    hinted: Boolean = false,
    solution: Boolean = false,
    legalTarget: Boolean = false,
    captureTarget: Boolean = false,
    moveQuality: String? = null,
    onClick: ((String) -> Unit)? = null,
    showPiece: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val baseColor = if (dark) Color(0xFF9D7A55) else Color(0xFFE9D7B9)
    val hintTransition = rememberInfiniteTransition(label = "hint square")
    val hintPulse by hintTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hint pulse",
    )
    val background = when {
        selected -> Color(0xFF6EA4B8)
        solution -> Color(0xFF88B6D8)
        hinted -> lerp(baseColor, Color(0xFF88B6D8), hintPulse)
        legalTarget -> Color(0xFFC4D8E8)
        square.highlighted -> Color(0xFFF2E29B)
        else -> baseColor
    }
    val hasHighlight = selected || solution || hinted || legalTarget || square.highlighted

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .then(
                if (hasHighlight) {
                    Modifier.border(0.75.dp, Color(0x663D332C))
                } else {
                    Modifier
                }
            )
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
        if (captureTarget) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0x99C74747),
                    radius = size.minDimension * 0.40f,
                    style = Stroke(width = size.minDimension * 0.09f),
                )
            }
        }
        if (showPiece) {
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
        moveQuality?.let { quality ->
            MoveQualityBadge(
                quality = quality,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(1.dp),
            )
        }
    }
}

@Composable
fun MoveQualityBadge(
    quality: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(22.dp),
        shape = CircleShape,
        color = moveQualityColor(quality),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = moveQualityMark(quality),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
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
        "Move ${currentPlyCount + 1} of ${line.plies.size} · select a move"
    }
}

fun isSpeedyDrillCompletion(
    userThinkingMillis: Long,
    linePlyCount: Int,
    timingEligible: Boolean,
    completedViaShowLine: Boolean,
): Boolean =
    timingEligible &&
        !completedViaShowLine &&
        linePlyCount > 0 &&
        userThinkingMillis.toDouble() / linePlyCount < 1_000.0

fun shouldTriggerLearningConfetti(
    wasLearned: Boolean,
    isLearned: Boolean,
    completedViaShowLine: Boolean,
): Boolean =
    !wasLearned && isLearned && !completedViaShowLine

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

fun isPromotionMove(
    from: String,
    to: String,
    board: List<BoardSquare>,
): Boolean {
    if (!from.isBoardCoordinate() || !to.isBoardCoordinate()) return false
    val pieceCode = board.firstOrNull { it.coordinate == from }?.pieceCode ?: return false
    if (pieceCode.length < 2 || pieceCode[1] != 'p') return false
    return when (pieceCode[0]) {
        'w' -> to[1] == '8'
        'b' -> to[1] == '1'
        else -> false
    }
}

fun promotionPieceName(promotion: Char): String =
    when (promotion.lowercaseChar()) {
        'q' -> "queen"
        'r' -> "rook"
        'b' -> "bishop"
        'n' -> "knight"
        else -> "queen"
    }

fun playoutConfirmationTitle(action: PlayoutConfirmationAction): String =
    when (action) {
        PlayoutConfirmationAction.OfferDraw -> "offer a draw?"
        PlayoutConfirmationAction.Resign -> "resign the game?"
        PlayoutConfirmationAction.Exit -> "exit playout?"
    }

fun playoutConfirmationMessage(action: PlayoutConfirmationAction): String =
    when (action) {
        PlayoutConfirmationAction.OfferDraw ->
            "Stockfish will accept if the position is roughly equal. Otherwise the game continues."

        PlayoutConfirmationAction.Resign ->
            "Stockfish takes the win immediately and this game ends."

        PlayoutConfirmationAction.Exit ->
            "Your current game will end."
    }

fun playoutConfirmationConfirmLabel(action: PlayoutConfirmationAction): String =
    when (action) {
        PlayoutConfirmationAction.OfferDraw -> "offer"
        PlayoutConfirmationAction.Resign -> "resign"
        PlayoutConfirmationAction.Exit -> "exit"
    }

fun sharedPlayoutPositionFen(
    handle: Long,
    fallbackFen: String,
): String =
    SharedCoreBridge.sharedPlayoutPositionFen(handle)?.takeIf { it.isNotBlank() } ?: fallbackFen

fun playoutStatusLabel(
    status: Int,
    gameOverReason: Int = SHARED_PLAYOUT_GAME_OVER_REASON_NONE,
    userSide: String = "white",
): String =
    when (status) {
        SHARED_PLAYOUT_GAME_OVER_STATUS -> when (gameOverReason) {
            SHARED_PLAYOUT_GAME_OVER_REASON_CHECKMATE_WHITE ->
                if (userSide.isBlackSide()) "checkmate · you lose" else "checkmate · you win"
            SHARED_PLAYOUT_GAME_OVER_REASON_CHECKMATE_BLACK ->
                if (userSide.isBlackSide()) "checkmate · you win" else "checkmate · you lose"
            SHARED_PLAYOUT_GAME_OVER_REASON_STALEMATE -> "draw by stalemate"
            SHARED_PLAYOUT_GAME_OVER_REASON_FIFTY_MOVE -> "draw by 50-move rule"
            SHARED_PLAYOUT_GAME_OVER_REASON_REPETITION -> "draw by repetition"
            SHARED_PLAYOUT_GAME_OVER_REASON_INSUFFICIENT_MATERIAL ->
                "draw by insufficient material"
            SHARED_PLAYOUT_GAME_OVER_REASON_DRAW_AGREED -> "draw agreed"
            SHARED_PLAYOUT_GAME_OVER_REASON_USER_RESIGNED -> "you resigned"
            SHARED_PLAYOUT_GAME_OVER_REASON_ENGINE_RESIGNATION_PENDING ->
                "engine offers to resign"
            SHARED_PLAYOUT_GAME_OVER_REASON_ENGINE_RESIGNATION_ACCEPTED ->
                "engine resigned · you win"
            else -> "game over"
        }
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

fun latestMoveQualityAnnotation(moves: List<SharedPlayoutMoveSummary>): MoveQualityAnnotation? =
    moves.lastOrNull { it.byUser && !it.quality.isNullOrBlank() }
        ?.let { move ->
            val square = move.uci.takeIf { it.length >= 4 }?.substring(2, 4)
            val quality = move.quality
            if (square != null && square.isBoardCoordinate() && quality != null) {
                MoveQualityAnnotation(square = square, quality = quality)
            } else {
                null
            }
        }

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

fun moveQualityColor(quality: String): Color =
    when (quality) {
        "brilliant" -> Color(0xFF1BA1A1)
        "best" -> Color(0xFF95BB4A)
        "excellent" -> Color(0xFF95BB4A)
        "good" -> Color(0xFF95AF80)
        "inaccuracy" -> Color(0xFFF7C245)
        "mistake" -> Color(0xFFFFA459)
        "blunder" -> Color(0xFFFA412D)
        "miss" -> Color(0xFFFF7769)
        else -> Color(0xFF6F655A)
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

fun PlySummary.boardArrow(): BoardArrow? = boardArrowFromUci(uci)

fun boardArrowFromUci(uci: String): BoardArrow? {
    if (uci.length < 4) return null
    val from = uci.substring(0, 2)
    val to = uci.substring(2, 4)
    return if (from.isBoardCoordinate() && to.isBoardCoordinate()) {
        BoardArrow(from = from, to = to)
    } else {
        null
    }
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

fun moveListToken(index: Int, ply: PlySummary): String =
    if (index % 2 == 0) "${index / 2 + 1}. ${ply.displaySan()}" else ply.displaySan()

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
private const val SHARED_PLAYOUT_WAITING_STATUS = 0
private const val SHARED_PLAYOUT_ENGINE_THINKING_STATUS = 1
private const val SHARED_PLAYOUT_GAME_OVER_STATUS = 3
private const val SHARED_PLAYOUT_GAME_OVER_REASON_NONE = 0
private const val SHARED_PLAYOUT_GAME_OVER_REASON_CHECKMATE_WHITE = 1
private const val SHARED_PLAYOUT_GAME_OVER_REASON_CHECKMATE_BLACK = 2
private const val SHARED_PLAYOUT_GAME_OVER_REASON_STALEMATE = 3
private const val SHARED_PLAYOUT_GAME_OVER_REASON_FIFTY_MOVE = 4
private const val SHARED_PLAYOUT_GAME_OVER_REASON_REPETITION = 5
private const val SHARED_PLAYOUT_GAME_OVER_REASON_INSUFFICIENT_MATERIAL = 6
private const val SHARED_PLAYOUT_GAME_OVER_REASON_DRAW_AGREED = 7
private const val SHARED_PLAYOUT_GAME_OVER_REASON_USER_RESIGNED = 8
private const val SHARED_PLAYOUT_GAME_OVER_REASON_ENGINE_RESIGNATION_PENDING = 9
private const val SHARED_PLAYOUT_GAME_OVER_REASON_ENGINE_RESIGNATION_ACCEPTED = 10
private const val SHARED_PLAYOUT_ENGINE_RESIGNATION_NONE = 0
private const val SHARED_PLAYOUT_ENGINE_RESIGNATION_PENDING = 1
private const val SNAPSHOT_PHASE_DRILL = "drill"
private const val SNAPSHOT_PHASE_PLAYOUT = "playout"
private const val DRILL_MODE_STRICT = "strict"
private const val DRILL_MODE_SHOW_AND_RETRY = "showAndRetry"
private const val MASTERY_THRESHOLD = 3
private const val MISTAKE_LOG_LIMIT = 20
private const val CUSTOM_OPENINGS_KEY = "custom.openings.json"
private const val DEFAULT_ENGINE_LEVEL = 10
private const val DEFAULT_MOVE_ANALYSIS_DEPTH = 10
private const val DEFAULT_MOVE_QUALITY_BADGE_MS = 1750
private const val STARTING_POSITION_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
