package com.chessopenings.app

import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun mapsPieceCodesToDrawableResourcesAndDescriptions() {
        assertEquals(R.drawable.wp, pieceResourceId("wp"))
        assertEquals(R.drawable.bk, pieceResourceId("bk"))
        assertEquals(null, pieceResourceId(""))
        assertEquals("white pawn", pieceDescription("wp"))
        assertEquals("black king", pieceDescription("bk"))
    }
}
