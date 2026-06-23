import Testing
@testable import ChessOpeningsCore

@Test func sharedSANLineStripsMoveNumbersAndBuildsCanonicalPlies() {
    let result = SharedSANLine.validate("1. e4 e5 2. Nf3 Nc6")

    #expect(
        result == .valid([
            SharedSANLinePly(san: "e4", uci: "e2e4"),
            SharedSANLinePly(san: "e5", uci: "e7e5"),
            SharedSANLinePly(san: "Nf3", uci: "g1f3"),
            SharedSANLinePly(san: "Nc6", uci: "b8c6"),
        ])
    )
}

@Test func sharedSANLineReportsFirstIllegalPly() {
    #expect(
        SharedSANLine.validate("e4 e5 Bh6") == .invalid(ply: 2, san: "Bh6")
    )
}

@Test func sharedSANLineRejectsEmptyInput() {
    #expect(SharedSANLine.validate("  1. 2... ") == .empty)
}
