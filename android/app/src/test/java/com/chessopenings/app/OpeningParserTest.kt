package com.chessopenings.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject

class OpeningParserTest {
    @Test
    fun parsesOpeningSummariesForCatalogueAndLineDisplay() {
        val json = """
            {
              "openings": [
                {
                  "name": "italian game",
                  "eco": "c50",
                  "side": "white",
                  "description": "Classical e-pawn development",
                  "isSeed": true,
                  "lines": [
                    {
                      "name": "Bc5",
                      "source": "masters",
                      "tags": ["quiet_setup", "mainline"],
                      "plies": [
                        {
                          "san": "e4",
                          "uci": "e2e4",
                          "annotation": "claim the centre",
                          "alternativeSans": ["d4"]
                        },
                        {
                          "san": "e5",
                          "uci": "e7e5",
                          "annotation": null,
                          "alternativeSans": []
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val openings = parseOpeningSummaries(json)

        assertEquals(1, openings.size)
        assertEquals("italian game", openings[0].name)
        assertEquals("C50", openings[0].eco)
        assertEquals("white", openings[0].side)
        assertEquals("Classical e-pawn development", openings[0].description)
        assertEquals(true, openings[0].isSeed)
        assertEquals(1, openings[0].lines.size)
        assertEquals("Bc5", openings[0].lines[0].name)
        assertEquals("masters", openings[0].lines[0].source)
        assertEquals(listOf("quiet_setup", "mainline"), openings[0].lines[0].tags)
        assertEquals(listOf("e4", "e5"), openings[0].lines[0].sans)
        assertEquals("e2e4", openings[0].lines[0].plies[0].uci)
        assertEquals("claim the centre", openings[0].lines[0].plies[0].annotation)
        assertEquals(listOf("d4"), openings[0].lines[0].plies[0].alternativeSans)
        assertEquals(null, openings[0].lines[0].plies[1].annotation)
    }

    @Test
    fun formatsSanLineWithMoveNumbersAndEllipsis() {
        val plies = listOf("e4", "e5", "Nf3", "Nc6", "Bb5")
            .map { PlySummary(san = it, uci = "", annotation = null, alternativeSans = emptyList()) }

        assertEquals("1. e4 e5 2. Nf3 Nc6 3. Bb5", formatSanLine(plies, maxPlies = 8))
        assertEquals("1. e4 e5 2. Nf3 ...", formatSanLine(plies, maxPlies = 3))
    }

    @Test
    fun formatsDisplayLabels() {
        val line = LineSummary(
            name = "Bc5",
            source = "",
            tags = emptyList(),
            plies = listOf(
                PlySummary(san = "e4", uci = "e2e4", annotation = null, alternativeSans = emptyList()),
                PlySummary(san = "e5", uci = "e7e5", annotation = null, alternativeSans = emptyList()),
                PlySummary(san = "Nf3", uci = "g1f3", annotation = null, alternativeSans = emptyList()),
            ),
        )

        assertEquals("Italian Game", "italian-game".toDisplayName())
        assertEquals("Seed", sourceLabel(""))
        assertEquals("Masters", sourceLabel("masters"))
        assertEquals("2 moves", lineDepthLabel(line))
        assertEquals("e4 · e2e4", firstMoveLabel(line))
    }

    @Test
    fun encodesLineSummaryForSharedCoreBridge() {
        val json = JSONObject(sampleDrillLine().toCoreJson())
        val plies = json.getJSONArray("plies")

        assertEquals("Bc5", json.getString("name"))
        assertEquals("masters", json.getString("source"))
        assertEquals(0, json.getJSONArray("tags").length())
        assertEquals(3, plies.length())
        assertEquals("e4", plies.getJSONObject(0).getString("san"))
        assertEquals("e2e4", plies.getJSONObject(0).getString("uci"))
        assertEquals("Nf3", plies.getJSONObject(2).getString("san"))
        assertEquals(0, plies.getJSONObject(2).getJSONArray("alternativeSans").length())
    }

    @Test
    fun buildsStartingBoardPreviewWithAppliedMove() {
        val highlighted = startingBoardSquares("e2e4")
            .filter { it.highlighted }
            .map { it.coordinate }

        assertEquals(listOf("e4", "e2"), highlighted)
        assertEquals("wr", startingPieceAt('a', 1))
        assertEquals("bk", startingPieceAt('e', 8))
        assertEquals("wp", startingPieceAt('e', 2))
        assertEquals("", startingPieceAt('e', 4))
        assertEquals("", previewPieceAt('e', 2, "e2e4"))
        assertEquals("wp", previewPieceAt('e', 4, "e2e4"))
        assertEquals("bq", previewPieceAt('a', 8, "a7a8q"))
        assertEquals(emptySet<String>(), highlightedSquaresForUci("castle"))
    }

    @Test
    fun buildsBoardAfterLinePlies() {
        val plies = listOf(
            PlySummary(san = "e4", uci = "e2e4", annotation = null, alternativeSans = emptyList()),
            PlySummary(san = "e5", uci = "e7e5", annotation = null, alternativeSans = emptyList()),
            PlySummary(san = "Nf3", uci = "g1f3", annotation = null, alternativeSans = emptyList()),
        )
        val board = boardSquaresAfterPlies(plies).associate { it.coordinate to it.pieceCode }

        assertEquals("wp", board["e4"])
        assertEquals("bp", board["e5"])
        assertEquals("wn", board["f3"])
        assertEquals("", board["e2"])
        assertEquals("", board["g1"])
    }

    @Test
    fun orientsDisplayedBoardByRepertoireSide() {
        val board = boardSquaresAfterPlies(emptyList())

        assertEquals("a8", displayedBoardSquares(board, "white").first().coordinate)
        assertEquals("h1", displayedBoardSquares(board, "white").last().coordinate)
        assertEquals("h1", displayedBoardSquares(board, "black").first().coordinate)
        assertEquals("a8", displayedBoardSquares(board, "black").last().coordinate)
        assertEquals("h1", displayedBoardSquares(board, "BLACK").first().coordinate)
    }

    @Test
    fun identifiesDarkBoardSquares() {
        assertEquals(true, isDarkBoardSquare('a', 1))
        assertEquals(false, isDarkBoardSquare('h', 1))
        assertEquals(false, isDarkBoardSquare('a', 8))
        assertEquals(true, isDarkBoardSquare('h', 8))
    }

    @Test
    fun appliesStandardCastlingMoveForBoardPlayback() {
        val pieces = startingPieceMap().toMutableMap()

        applyUciMove(pieces, "e1g1")

        assertEquals("wk", pieces["g1"])
        assertEquals("wr", pieces["f1"])
        assertEquals(null, pieces["e1"])
        assertEquals(null, pieces["h1"])
    }

    @Test
    fun appliesChessKitCastlingMoveForBoardPlayback() {
        val pieces = startingPieceMap().toMutableMap()

        applyUciMove(pieces, "e1h1")

        assertEquals("wk", pieces["g1"])
        assertEquals("wr", pieces["f1"])
        assertEquals(null, pieces["e1"])
        assertEquals(null, pieces["h1"])
        assertEquals(CastlingSquares(kingTo = "c8", rookFrom = "a8", rookTo = "d8"), castlingSquares("bk", "e8", "a8"))
        assertEquals(null, castlingSquares("wq", "e1", "h1"))
    }

    @Test
    fun mapsPieceCodesToDrawableResourcesAndDescriptions() {
        assertEquals(R.drawable.wp, pieceResourceId("wp"))
        assertEquals(R.drawable.bk, pieceResourceId("bk"))
        assertEquals(null, pieceResourceId(""))
        assertEquals("white pawn", pieceDescription("wp"))
        assertEquals("black king", pieceDescription("bk"))
    }

    @Test
    fun matchesTappedSquaresAgainstExpectedMove() {
        assertEquals(true, sameMoveSquares("e2e4", "e2e4"))
        assertEquals(true, sameMoveSquares("a7a8", "a7a8q"))
        assertEquals(false, sameMoveSquares("d2d4", "e2e4"))
        assertEquals(false, sameMoveSquares("e2", "e2e4"))

        val next = PlySummary(san = "e4", uci = "e2e4", annotation = null, alternativeSans = emptyList())
        assertEquals("Try again · expected e4", expectedMoveFeedback(next))
        assertEquals("Line complete", expectedMoveFeedback(null))
    }

    @Test
    fun startsBlackDrillsAfterScriptedWhiteMove() {
        val line = sampleDrillLine()
        val whiteOpening = sampleOpening(side = "white", line = line)
        val blackOpening = sampleOpening(side = "black", line = line)

        assertEquals(0, initialDrillPlyCount(whiteOpening, line))
        assertEquals(1, initialDrillPlyCount(blackOpening, line))
        assertEquals(2, advancedDrillPlyCountAfterUserMove(0, line))
        assertEquals(3, advancedDrillPlyCountAfterUserMove(1, line))
        assertEquals(1, showLineNextPlyCount(0, line))
        assertEquals(3, showLineNextPlyCount(2, line))
        assertEquals(3, showLineNextPlyCount(3, line))
    }

    @Test
    fun restrictsDrillStartSquareToExpectedUserPiece() {
        val line = sampleDrillLine()
        val startingBoard = boardSquaresAfterPlies(emptyList())
        val blackReplyBoard = boardSquaresAfterPlies(line.plies.take(1))

        assertEquals(true, canStartDrillMove("e2", startingBoard, line.plies[0], "white"))
        assertEquals(false, canStartDrillMove("d2", startingBoard, line.plies[0], "white"))
        assertEquals(false, canStartDrillMove("e4", startingBoard, line.plies[0], "white"))
        assertEquals(false, canStartDrillMove("e7", startingBoard, line.plies[1], "white"))
        assertEquals(true, canStartDrillMove("e7", blackReplyBoard, line.plies[1], "black"))
        assertEquals('w', pieceColorCode("white"))
        assertEquals('b', pieceColorCode("black"))
        assertEquals("Select the piece for e4", selectExpectedPieceFeedback(line.plies[0]))
    }

    @Test
    fun extractsHintAndSolutionCoordinatesFromBookMove() {
        val move = PlySummary(san = "e4", uci = "e2e4", annotation = null, alternativeSans = emptyList())
        val promotion = PlySummary(san = "a8=Q", uci = "a7a8q", annotation = null, alternativeSans = emptyList())
        val malformed = PlySummary(san = "bad", uci = "castle", annotation = null, alternativeSans = emptyList())

        assertEquals("e2", move.fromCoordinate())
        assertEquals(setOf("e2", "e4"), move.moveCoordinates())
        assertEquals("a7", promotion.fromCoordinate())
        assertEquals(setOf("a7", "a8"), promotion.moveCoordinates())
        assertEquals(null, malformed.fromCoordinate())
        assertEquals(emptySet<String>(), malformed.moveCoordinates())
    }

    private fun sampleOpening(side: String, line: LineSummary): OpeningSummary =
        OpeningSummary(
            name = "italian game",
            eco = "C50",
            side = side,
            description = null,
            isSeed = true,
            lines = listOf(line),
        )

    private fun sampleDrillLine(): LineSummary =
        LineSummary(
            name = "Bc5",
            source = "masters",
            tags = emptyList(),
            plies = listOf(
                PlySummary(san = "e4", uci = "e2e4", annotation = null, alternativeSans = emptyList()),
                PlySummary(san = "e5", uci = "e7e5", annotation = null, alternativeSans = emptyList()),
                PlySummary(san = "Nf3", uci = "g1f3", annotation = null, alternativeSans = emptyList()),
            ),
        )
}
