package com.chessopenings.app

import org.junit.Assert.assertEquals
import org.junit.Test

class OpeningParserTest {
    @Test
    fun parsesOpeningSummariesForCatalogueDisplay() {
        val json = """
            {
              "openings": [
                {
                  "name": "italian game",
                  "eco": "c50",
                  "side": "white",
                  "lines": [
                    {
                      "name": "Bc5",
                      "source": "masters",
                      "plies": [
                        {"san": "e4"},
                        {"san": "e5"}
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
        assertEquals(1, openings[0].lines.size)
        assertEquals("Bc5", openings[0].lines[0].name)
        assertEquals("masters", openings[0].lines[0].source)
        assertEquals(listOf("e4", "e5"), openings[0].lines[0].sans)
    }
}
