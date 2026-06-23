import ChessKit
import Testing
@testable import ChessOpeningsCore

@Test func sharedLegalTargetsReturnStartingPawnMoves() {
    let targets = SharedLegalTargets.query(fen: Position.standard.fen, source: "e2")

    #expect(targets == [
        SharedLegalTarget(square: "e3", isCapture: false),
        SharedLegalTarget(square: "e4", isCapture: false),
    ])
}

@Test func sharedLegalTargetsIdentifyCaptures() {
    let fen = "8/8/8/3p4/4P3/8/8/4K2k w - - 0 1"
    let targets = SharedLegalTargets.query(fen: fen, source: "e4")

    #expect(targets.contains(SharedLegalTarget(square: "d5", isCapture: true)))
    #expect(targets.contains(SharedLegalTarget(square: "e5", isCapture: false)))
}

@Test func sharedLegalTargetsRejectInvalidOrWrongSideSources() {
    #expect(SharedLegalTargets.query(fen: "not a fen", source: "e2").isEmpty)
    #expect(SharedLegalTargets.query(fen: Position.standard.fen, source: "e7").isEmpty)
    #expect(SharedLegalTargets.query(fen: Position.standard.fen, source: "no").isEmpty)
}
