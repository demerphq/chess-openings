import ChessKit

public enum CoreValidation {
    public static func fenAfterKingsPawnOpening() -> String {
        var board = Board()
        _ = board.move(pieceAt: .e2, to: .e4)
        return board.position.fen
    }

    public static func canPlayKingsPawnOpening() -> Bool {
        let board = Board()
        guard board.canMove(pieceAt: .e2, to: .e4) else {
            return false
        }

        let move = Move(san: "e4", position: board.position)
        return move?.start == .e2 && move?.end == .e4
    }
}
