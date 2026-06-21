import Foundation
import Testing
@testable import ChessOpeningsCore

private let fixtureJSON = """
{
  "version": 2,
  "openings": [
    {
      "name": "italian game",
      "eco": "c50",
      "side": "white",
      "rootFen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      "description": null,
      "isSeed": true,
      "lines": [
        {
          "name": "Bc5",
          "source": "masters",
          "tags": ["mainline"],
          "plies": [
            {
              "san": "e4",
              "uci": "e2e4",
              "annotation": null,
              "alternativeSans": []
            },
            {
              "san": "e5",
              "uci": "e7e5",
              "annotation": "classical reply",
              "alternativeSans": ["...e5"]
            },
            {
              "san": "Nf3",
              "uci": "g1f3",
              "annotation": null,
              "alternativeSans": []
            }
          ]
        }
      ]
    }
  ]
}
"""

@Test func decodesSeedCatalogueFromString() throws {
    let catalogue = try SeedCatalogue.decode(from: fixtureJSON)

    #expect(catalogue.version == 2)
    #expect(catalogue.openings.count == 1)

    let opening = try #require(catalogue.opening(named: "italian game"))
    #expect(opening.eco == "c50")
    #expect(opening.side == .white)
    #expect(opening.isSeed)
    #expect(opening.description == nil)
    #expect(opening.lines.count == 1)

    let line = try #require(opening.lines.first)
    #expect(line.name == "Bc5")
    #expect(line.source == .masters)
    #expect(line.tags == ["mainline"])
    #expect(line.plies.map(\.san) == ["e4", "e5", "Nf3"])
    #expect(line.plies[1].annotation == "classical reply")
    #expect(line.plies[1].alternativeSans == ["...e5"])
}

@Test func decodesSeedCatalogueFromDataAndBuildsSummaries() throws {
    let catalogue = try SeedCatalogue.decode(from: Data(fixtureJSON.utf8))

    #expect(catalogue.openings(for: .white).count == 1)
    #expect(catalogue.openings(for: .black).isEmpty)

    let openingSummary = try #require(catalogue.openingSummaries.first)
    #expect(openingSummary.name == "italian game")
    #expect(openingSummary.lineCount == 1)
    #expect(openingSummary.plyCount == 3)

    let lineSummary = try #require(catalogue.openings.first?.lineSummaries.first)
    #expect(lineSummary.name == "Bc5")
    #expect(lineSummary.source == .masters)
    #expect(lineSummary.plyCount == 3)
    #expect(lineSummary.firstMoveSAN == "e4")
    #expect(lineSummary.moveText == "1. e4 e5 2. Nf3")
}

@Test func decodesGeneratedSeedResource() throws {
    let testFile = URL(fileURLWithPath: #filePath)
    let packageDirectory = testFile
        .deletingLastPathComponent()
        .deletingLastPathComponent()
        .deletingLastPathComponent()
    let repoDirectory = packageDirectory
        .deletingLastPathComponent()
        .deletingLastPathComponent()
    let seedURL = repoDirectory
        .appendingPathComponent("Chess Openings")
        .appendingPathComponent("Resources")
        .appendingPathComponent("openings.json")

    let catalogue = try SeedCatalogue.decode(from: try Data(contentsOf: seedURL))

    #expect(catalogue.version == 2)
    #expect(catalogue.openings.count >= 16)
    #expect(catalogue.openings.allSatisfy { !$0.lines.isEmpty })
    #expect(catalogue.openings.allSatisfy { !$0.rootFen.isEmpty })
    #expect(catalogue.openings.flatMap(\.lines).allSatisfy { !$0.plies.isEmpty })
}
