import ChessKit
import Foundation

public enum SharedMoveQuality: String, Codable, Equatable, Sendable {
    case brilliant
    case best
    case excellent
    case good
    case inaccuracy
    case mistake
    case blunder
    case miss

    static func classify(
        bestEvalCp: Int,
        actualEvalCp: Int,
        bestEvalIsWinning: Bool,
        isBrilliantCandidate: Bool = false
    ) -> SharedMoveQuality {
        let drop = bestEvalCp - actualEvalCp
        if isBrilliantCandidate && drop < 5 { return .brilliant }
        if bestEvalIsWinning && drop >= 300 { return .miss }
        switch drop {
        case ..<5: return .best
        case ..<20: return .excellent
        case ..<50: return .good
        case ..<100: return .inaccuracy
        case ..<300: return .mistake
        default: return .blunder
        }
    }
}

extension SharedEngineEvaluation {
    var clampedCp: Int {
        switch self {
        case .cp(let value):
            return value
        case .mate(let moves):
            if moves > 0 { return 5000 }
            if moves < 0 { return -5000 }
            return 0
        }
    }
}

enum SharedMoveQualityHeuristics {
    static func isBrilliantCandidate(
        userMove: Move,
        pre: Position,
        post: Position,
        userSide: OpeningSide,
        bestUCI: String?,
        bestEvalCp: Int,
        actualEvalCp: Int
    ) -> Bool {
        guard let bestUCI, bestUCI.count >= 4 else { return false }
        let bestFrom = Square(String(bestUCI.prefix(2)))
        let bestTo = Square(String(bestUCI.dropFirst(2).prefix(2)))
        guard userMove.start == bestFrom, userMove.end == bestTo else {
            return false
        }
        guard bestEvalCp < 300, actualEvalCp >= 0 else { return false }
        guard let movedKind = pre.piece(at: userMove.start)?.kind else {
            return false
        }

        let movedValue = materialValue(of: movedKind)
        guard movedValue >= 3 else { return false }
        let capturedValue = pre.piece(at: userMove.end)
            .map { materialValue(of: $0.kind) } ?? 0
        guard capturedValue < movedValue else { return false }

        let opponentColor: Piece.Color = userSide == .white ? .black : .white
        let attackers = attackers(of: userMove.end, by: opponentColor, in: post)
        let minAttackerValue = attackers
            .compactMap { post.piece(at: $0).map { materialValue(of: $0.kind) } }
            .min()
        guard let minAttackerValue, minAttackerValue < movedValue else {
            return false
        }
        return true
    }

    private static func attackers(
        of target: Square,
        by color: Piece.Color,
        in position: Position
    ) -> [Square] {
        let board = Board(position: position)
        return position.pieces
            .filter { $0.color == color }
            .compactMap { piece in
                board.legalMoves(forPieceAt: piece.square)
                    .contains(target) ? piece.square : nil
            }
    }

    private static func materialValue(of kind: Piece.Kind) -> Int {
        switch kind {
        case .pawn: return 1
        case .knight, .bishop: return 3
        case .rook: return 5
        case .queen: return 9
        case .king: return 0
        }
    }
}
