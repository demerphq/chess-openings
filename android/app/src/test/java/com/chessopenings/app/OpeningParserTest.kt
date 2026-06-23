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
    fun formatsMoveListTokensForWrappedLayout() {
        val e4 = PlySummary(san = "e4", uci = "e2e4", annotation = null, alternativeSans = emptyList())
        val e5 = PlySummary(san = "e5", uci = "e7e5", annotation = null, alternativeSans = emptyList())
        val nf3 = PlySummary(san = "Nf3", uci = "g1f3", annotation = "!", alternativeSans = emptyList())

        assertEquals("1. e4", moveListToken(0, e4))
        assertEquals("e5", moveListToken(1, e5))
        assertEquals("2. Nf3 !", moveListToken(2, nf3))
    }

    @Test
    fun awardsSpeedyOnlyForEligibleSubSecondCompletions() {
        assertEquals(true, isSpeedyDrillCompletion(7_999, 8, timingEligible = true, completedViaShowLine = false))
        assertEquals(false, isSpeedyDrillCompletion(8_000, 8, timingEligible = true, completedViaShowLine = false))
        assertEquals(false, isSpeedyDrillCompletion(1_000, 8, timingEligible = false, completedViaShowLine = false))
        assertEquals(false, isSpeedyDrillCompletion(1_000, 8, timingEligible = true, completedViaShowLine = true))
        assertEquals(false, isSpeedyDrillCompletion(0, 0, timingEligible = true, completedViaShowLine = false))
    }

    @Test
    fun triggersConfettiOnlyOnUserDrivenLearnedTransition() {
        assertEquals(true, shouldTriggerLearningConfetti(false, true, completedViaShowLine = false))
        assertEquals(false, shouldTriggerLearningConfetti(true, true, completedViaShowLine = false))
        assertEquals(false, shouldTriggerLearningConfetti(false, false, completedViaShowLine = false))
        assertEquals(false, shouldTriggerLearningConfetti(false, true, completedViaShowLine = true))
        assertEquals(90, generateConfettiParticles(seed = 7).size)
        assertEquals(generateConfettiParticles(seed = 7), generateConfettiParticles(seed = 7))
    }

    @Test
    fun parsesSharedLegalTargets() {
        val targets = parseSharedLegalTargets(
            """[{"square":"e4","isCapture":false},{"square":"d5","isCapture":true}]""",
        )

        assertEquals(
            listOf(
                SharedLegalTargetSummary("e4", isCapture = false),
                SharedLegalTargetSummary("d5", isCapture = true),
            ),
            targets,
        )
        assertEquals(emptyList<SharedLegalTargetSummary>(), parseSharedLegalTargets("invalid"))
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
    fun preservesAnimatedPieceIdentityAcrossMovesAndCaptures() {
        val initialBoard = boardSquaresAfterPlies(emptyList())
        val initialPieces = boardAnimationPieces(initialBoard, nextId = 1)
        val pawnId = initialPieces.first { it.coordinate == "e2" }.id

        val afterE4 = reconcileAnimatedBoardPieces(
            previous = initialPieces,
            board = boardSquaresAfterPlies(
                listOf(PlySummary("e4", "e2e4", null, emptyList())),
            ),
            nextId = 33,
        )

        assertEquals(pawnId, afterE4.pieces.first { it.coordinate == "e4" }.id)
        assertEquals(33, afterE4.nextId)

        val captureBoard = listOf(
            BoardSquare('d', 5, "wp", highlighted = true),
            BoardSquare('e', 1, "wk", highlighted = false),
            BoardSquare('e', 8, "bk", highlighted = false),
        )
        val beforeCapture = listOf(
            AnimatedBoardPiece(1, "e4", "wp"),
            AnimatedBoardPiece(2, "d5", "bp"),
            AnimatedBoardPiece(3, "e1", "wk"),
            AnimatedBoardPiece(4, "e8", "bk"),
        )
        val afterCapture = reconcileAnimatedBoardPieces(beforeCapture, captureBoard, nextId = 5)

        assertEquals(1, afterCapture.pieces.first { it.coordinate == "d5" }.id)
        assertEquals(false, afterCapture.pieces.any { it.id == 2 })
    }

    @Test
    fun preservesBothAnimatedPieceIdentitiesDuringCastling() {
        val before = listOf(
            AnimatedBoardPiece(1, "e1", "wk"),
            AnimatedBoardPiece(2, "h1", "wr"),
            AnimatedBoardPiece(3, "e8", "bk"),
        )
        val board = listOf(
            BoardSquare('g', 1, "wk", highlighted = true),
            BoardSquare('f', 1, "wr", highlighted = true),
            BoardSquare('e', 8, "bk", highlighted = false),
        )

        val after = reconcileAnimatedBoardPieces(before, board, nextId = 4)

        assertEquals(1, after.pieces.first { it.coordinate == "g1" }.id)
        assertEquals(2, after.pieces.first { it.coordinate == "f1" }.id)
        assertEquals(4, after.nextId)
    }

    @Test
    fun buildsBoardFromFenForSharedDrillPosition() {
        val fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2"
        val board = boardSquaresFromFen(fen, highlightedMove = "e7e5")
            .associate { it.coordinate to it }

        assertEquals("wp", board["e4"]?.pieceCode)
        assertEquals("bp", board["e5"]?.pieceCode)
        assertEquals("wn", board["f3"]?.pieceCode)
        assertEquals("", board["e2"]?.pieceCode)
        assertEquals("", board["g1"]?.pieceCode)
        assertEquals(true, board["e7"]?.highlighted)
        assertEquals(true, board["e5"]?.highlighted)
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
        assertEquals("Try again", expectedMoveFeedback(next))
        assertEquals("Book says e4 · try again", expectedMoveFeedback(next, "showAndRetry"))
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
    fun labelsCleanAndAssistedLineCompletions() {
        val line = sampleDrillLine()

        assertEquals("perfect", drillProgressLabel(3, line))
        assertEquals("line complete", drillProgressLabel(3, line, madeMistake = true))
        assertEquals("line complete", drillProgressLabel(3, line, completedViaShowLine = true))
        assertEquals("Move 2 of 3 · select a move", drillProgressLabel(1, line))
    }

    @Test
    fun recordsCompletionProgressWithStickyLearnedState() {
        val first = recordCompletionProgress(LineProgressSummary(), madeMistake = false, threshold = 3)
        val second = recordCompletionProgress(first, madeMistake = false, threshold = 3)
        val learned = recordCompletionProgress(second, madeMistake = false, threshold = 3)
        val afterMistake = recordCompletionProgress(learned, madeMistake = true, threshold = 3)

        assertEquals(1, first.correctStreak)
        assertEquals(false, first.isLearned)
        assertEquals(3, learned.correctStreak)
        assertEquals(true, learned.isLearned)
        assertEquals(3, learned.timesAttempted)
        assertEquals(3, learned.timesCompleted)
        assertEquals(0, afterMistake.correctStreak)
        assertEquals(true, afterMistake.isLearned)
        assertEquals(4, afterMistake.timesAttempted)
        assertEquals(4, afterMistake.timesCompleted)
    }

    @Test
    fun recordsRollingMistakesForLineReview() {
        val expected = PlySummary(san = "e4", uci = "e2e4", annotation = null, alternativeSans = emptyList())
        val first = appendRollingMistake(emptyList(), expected, playedUci = "d2d4", atMillis = 1L)
        val second = appendRollingMistake(first, expected, playedUci = "c2c4", atMillis = 2L)

        assertEquals(1, first.size)
        assertEquals("d2d4", first[0].playedUci)
        assertEquals("2 mistakes · played c2c4 · book e4", mistakeSummaryText(second))

        val roundTrip = decodeMistakes(encodeMistakes(second))
        assertEquals(second, roundTrip)
        assertEquals(emptyList<AndroidMistake>(), decodeMistakes("not json"))
    }

    @Test
    fun capsMistakeLogAtTwentyRecentEntries() {
        val expected = PlySummary(san = "e4", uci = "e2e4", annotation = null, alternativeSans = emptyList())
        val mistakes = (1..25).fold(emptyList<AndroidMistake>()) { current, index ->
            appendRollingMistake(current, expected, playedUci = "a${index}a${index + 1}", atMillis = index.toLong())
        }

        assertEquals(20, mistakes.size)
        assertEquals("a6a7", mistakes.first().playedUci)
        assertEquals("a25a26", mistakes.last().playedUci)
    }

    @Test
    fun buildsStableProgressKeysFromOpeningAndLineIdentity() {
        val line = sampleDrillLine()
        val opening = sampleOpening(side = "white", line = line)
        val same = sampleOpening(side = "white", line = line)
        val other = sampleOpening(side = "black", line = line)

        assertEquals(progressKey(opening, line), progressKey(same, line))
        assertEquals(false, progressKey(opening, line) == progressKey(other, line))
    }

    @Test
    fun findsDrillSelectionForPersistedSnapshot() {
        val line = sampleDrillLine()
        val opening = sampleOpening(side = "white", line = line)
        val snapshot = PersistedDrillSnapshot(
            lineKey = progressKey(opening, line),
            plyIndex = 2,
            madeMistake = true,
        )

        val selection = drillSelectionForSnapshot(listOf(opening), snapshot)

        assertEquals(opening, selection?.opening)
        assertEquals(line, selection?.line)
        assertEquals(snapshot, selection?.restoredSnapshot)
        assertEquals("drill", snapshot.phase)
        assertEquals(null, snapshot.playoutFen)
        assertEquals(null, snapshot.playoutStartFen)
        assertEquals(null, snapshot.playoutMovesJson)
        assertEquals(10, snapshot.engineLevel)
        assertEquals(null, drillSelectionForSnapshot(emptyList(), snapshot))
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
        assertEquals(true, canStartPlayoutMove("e2", startingBoard, "white"))
        assertEquals(false, canStartPlayoutMove("e7", startingBoard, "white"))
        assertEquals(true, canStartPlayoutMove("e7", startingBoard, "black"))
        assertEquals(false, canStartPlayoutMove("e2", startingBoard, "black"))
        assertEquals('w', pieceColorCode("white"))
        assertEquals('b', pieceColorCode("black"))
        assertEquals(0, "white".toSharedDrillUserSide())
        assertEquals(1, "black".toSharedDrillUserSide())
        assertEquals("Select the piece for e4", selectExpectedPieceFeedback(line.plies[0]))
    }

    @Test
    fun detectsPromotionMovesFromBoardState() {
        val board = listOf(
            BoardSquare(file = 'a', rank = 7, pieceCode = "wp", highlighted = false),
            BoardSquare(file = 'h', rank = 2, pieceCode = "bp", highlighted = false),
            BoardSquare(file = 'b', rank = 7, pieceCode = "wn", highlighted = false),
        )

        assertEquals(true, isPromotionMove("a7", "a8", board))
        assertEquals(true, isPromotionMove("h2", "h1", board))
        assertEquals(false, isPromotionMove("a7", "a6", board))
        assertEquals(false, isPromotionMove("b7", "b8", board))
        assertEquals(false, isPromotionMove("z9", "a8", board))
        assertEquals("queen", promotionPieceName('q'))
        assertEquals("knight", promotionPieceName('n'))
    }

    @Test
    fun labelsPlayoutConfirmationActions() {
        assertEquals("offer a draw?", playoutConfirmationTitle(PlayoutConfirmationAction.OfferDraw))
        assertEquals("resign", playoutConfirmationConfirmLabel(PlayoutConfirmationAction.Resign))
        assertEquals("Your current game will end.", playoutConfirmationMessage(PlayoutConfirmationAction.Exit))
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

    @Test
    fun parsesSharedPlayoutMoveHistory() {
        val moves = parseSharedPlayoutMoves(
            """
            [
              {"uci":"e2e4","san":"e4","byUser":true,"fenAfterMove":"after e4"},
              {"uci":"e7e5","san":"e5","byUser":false,"fenAfterMove":"after e5"}
            ]
            """.trimIndent(),
        )

        assertEquals(2, moves.size)
        assertEquals("e4", moves[0].san)
        assertEquals(true, moves[0].byUser)
        assertEquals("e5", moves[1].toPlySummary().san)
        assertEquals(emptyList<SharedPlayoutMoveSummary>(), parseSharedPlayoutMoves("not json"))
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
