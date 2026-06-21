import Testing
@testable import ChessOpeningsCore

@Test func validatesChessKitMoveGenerationAndSanParsing() {
    #expect(CoreValidation.canPlayKingsPawnOpening())
    #expect(CoreValidation.fenAfterKingsPawnOpening() == "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
}
