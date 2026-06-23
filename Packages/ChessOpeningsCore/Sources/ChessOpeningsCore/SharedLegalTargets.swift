import ChessKit
import Foundation

public struct SharedLegalTarget: Codable, Equatable, Sendable {
    public let square: String
    public let isCapture: Bool

    public init(square: String, isCapture: Bool) {
        self.square = square
        self.isCapture = isCapture
    }
}

public enum SharedLegalTargets {
    public static func query(fen: String, source: String) -> [SharedLegalTarget] {
        let sourceCharacters = Array(source)
        guard sourceCharacters.count == 2,
              ("a"..."h").contains(sourceCharacters[0]),
              let rank = sourceCharacters[1].wholeNumberValue,
              (1...8).contains(rank),
              let position = Position(fen: fen) else {
            return []
        }
        let sourceSquare = Square(source)
        guard position.piece(at: sourceSquare)?.color == position.sideToMove else {
            return []
        }

        let board = Board(position: position)
        return board.legalMoves(forPieceAt: sourceSquare)
            .map { target in
                SharedLegalTarget(
                    square: "\(target.file.rawValue)\(target.rank.value)",
                    isCapture: position.piece(at: target) != nil
                )
            }
            .sorted { $0.square < $1.square }
    }
}
