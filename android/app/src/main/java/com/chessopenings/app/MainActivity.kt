package com.chessopenings.app

import android.os.Bundle
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChessOpeningsApp()
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
    val openings = remember {
        context.assets.open("openings.json")
            .bufferedReader()
            .use { parseOpeningSummaries(it.readText()) }
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
            ChessOpeningsHome(openings)
        }
    }
}

@Composable
fun ChessOpeningsHome(openings: List<OpeningSummary>) {
    var selectedTab by remember { mutableStateOf(AppTab.Train) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            OpeningCatalogue(
                openings = openings,
                tab = selectedTab,
                modifier = Modifier.fillMaxSize(),
            )
        }
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AppTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
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
    modifier: Modifier = Modifier,
) {
    var selectedOpeningIndex by remember { mutableIntStateOf(0) }
    var selectedLineIndex by remember { mutableIntStateOf(0) }
    val selectedOpening = openings.getOrNull(selectedOpeningIndex)
    val selectedLine = selectedOpening?.lines?.getOrNull(selectedLineIndex)
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
            if (selectedOpening != null && selectedLine != null) {
                item {
                    SelectedLineDetail(
                        opening = selectedOpening,
                        line = selectedLine,
                    )
                }

                lineSection(
                    title = "master games",
                    lines = selectedOpening.lines.withIndex().filter { it.value.source == "masters" },
                    selectedLineIndex = selectedLineIndex,
                    onLineSelected = { selectedLineIndex = it },
                )

                lineSection(
                    title = "online play (2200+)",
                    lines = selectedOpening.lines.withIndex().filter { it.value.source == "open" },
                    selectedLineIndex = selectedLineIndex,
                    onLineSelected = { selectedLineIndex = it },
                )
            }

            when (tab) {
                AppTab.Train -> {
                    openingSection(
                        title = "as white",
                        openings = indexedOpenings.filter { it.second.side == "white" },
                        selectedOpeningIndex = selectedOpeningIndex,
                        onOpeningSelected = { index ->
                            selectedOpeningIndex = index
                            selectedLineIndex = 0
                        },
                    )

                    openingSection(
                        title = "as black",
                        openings = indexedOpenings.filter { it.second.side == "black" },
                        selectedOpeningIndex = selectedOpeningIndex,
                        onOpeningSelected = { index ->
                            selectedOpeningIndex = index
                            selectedLineIndex = 0
                        },
                    )
                }

                AppTab.Library -> {
                    openingSection(
                        title = "seed openings",
                        openings = indexedOpenings.filter { it.second.isSeed },
                        selectedOpeningIndex = selectedOpeningIndex,
                        onOpeningSelected = { index ->
                            selectedOpeningIndex = index
                            selectedLineIndex = 0
                        },
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
                            val (index, opening) = indexedOpening
                            OpeningRow(
                                opening = opening,
                                selected = index == selectedOpeningIndex,
                                onClick = {
                                    selectedOpeningIndex = index
                                    selectedLineIndex = 0
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CatalogueHeader(
    tab: AppTab,
    openingCount: Int,
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
                text = "$openingCount openings · ${tab.subtitle}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
fun SelectedLineDetail(
    opening: OpeningSummary,
    line: LineSummary,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = opening.name.toDisplayName(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${opening.eco} · ${opening.side.toDisplayName()} repertoire · ${lineDepthLabel(line)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
            }

            BoardPreview(
                line = line,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = line.name.toDisplayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SourcePill(source = line.source)
                }
                Text(
                    text = formatSanLine(line.plies, maxPlies = 18),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                line.tags.takeIf { it.isNotEmpty() }?.let { tags ->
                    Text(
                        text = tags.joinToString(" · ") { it.toDisplayName() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun BoardPreview(
    line: LineSummary,
    modifier: Modifier = Modifier,
) {
    val highlightedMove = line.plies.firstOrNull()?.uci.orEmpty()
    val board = remember(highlightedMove) { startingBoardSquares(highlightedMove) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF6F655A), RoundedCornerShape(6.dp)),
            color = Color(0xFFEEE2D2),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                board.chunked(8).forEach { rankSquares ->
                    Row(modifier = Modifier.weight(1f)) {
                        rankSquares.forEachIndexed { index, square ->
                            BoardSquareCell(
                                square = square,
                                dark = ((square.rank + index) % 2 == 0),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "Start position · previewing ${firstMoveLabel(line)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
    }
}

@Composable
fun BoardSquareCell(
    square: BoardSquare,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseColor = if (dark) Color(0xFF9D7A55) else Color(0xFFE9D7B9)
    val background = if (square.highlighted) Color(0xFFB9C86B) else baseColor

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .padding(3.dp),
    ) {
        if (square.file == 'a') {
            Text(
                text = square.rank.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF3D332C).copy(alpha = 0.72f),
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        if (square.rank == 1) {
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
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFFE7F0EE) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        ),
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
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${opening.eco} · ${opening.side.toDisplayName()} · ${opening.lines.size} lines",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
            Text(
                text = opening.lines.size.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@Composable
fun LineRow(
    line: LineSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFFFFF6E3) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = line.name.toDisplayName(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                SourcePill(source = line.source)
            }
            Text(
                text = formatSanLine(line.plies, maxPlies = 10),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LazyListScope.lineSection(
    title: String,
    lines: List<IndexedValue<LineSummary>>,
    selectedLineIndex: Int,
    onLineSelected: (Int) -> Unit,
) {
    if (lines.isEmpty()) return

    item {
        SectionHeader(title)
    }

    itemsIndexed(lines) { _, indexedLine ->
        LineRow(
            line = indexedLine.value,
            selected = indexedLine.index == selectedLineIndex,
            onClick = { onLineSelected(indexedLine.index) },
        )
    }
}

private fun LazyListScope.openingSection(
    title: String,
    openings: List<Pair<Int, OpeningSummary>>,
    selectedOpeningIndex: Int,
    onOpeningSelected: (Int) -> Unit,
) {
    if (openings.isEmpty()) return

    item {
        SectionHeader(title)
    }

    itemsIndexed(openings) { _, indexedOpening ->
        val (index, opening) = indexedOpening
        OpeningRow(
            opening = opening,
            selected = index == selectedOpeningIndex,
            onClick = { onOpeningSelected(index) },
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
    return (8 downTo 1).flatMap { rank ->
        ('a'..'h').map { file ->
            val coordinate = "$file$rank"
            BoardSquare(
                file = file,
                rank = rank,
                pieceCode = previewPieceAt(file, rank, highlightedMove),
                highlighted = coordinate in highlightedSquares,
            )
        }
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
            append(pair[0].san)
            if (pair.size > 1) {
                append(' ')
                append(pair[1].san)
            }
        }
    }.joinToString(" ")
    return if (plies.size > maxPlies) "$moves ..." else moves
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

private fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.optStringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return List(array.length()) { index -> array.optString(index) }
        .filter { it.isNotBlank() }
}
