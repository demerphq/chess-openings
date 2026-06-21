import ChessKit
import Foundation

public enum SharedDrillStatus: String, Codable, Equatable, Sendable {
    case waitingForUser
    case mistake
    case lineComplete
}

public enum SharedDrillSubmitOutcome: Equatable, Sendable {
    case accepted(SharedDrillTurn)
    case incorrect(SharedDrillMistake)
    case invalidInput
    case invalidBookMove(BookPly)
    case lineComplete
}

public struct SharedDrillTurn: Equatable, Sendable {
    public let userMove: SharedDrillMove
    public let scriptedReply: SharedDrillMove?
    public let status: SharedDrillStatus
}

public struct SharedDrillMistake: Equatable, Sendable {
    public let expected: BookPly
    public let playedUCI: String
    public let playedSAN: String
}

public struct SharedDrillMove: Equatable, Sendable {
    public let san: String
    public let uci: String
    public let byUser: Bool
    public let fenAfterMove: String
}

public final class SharedDrillSession {
    public let line: Line
    public private(set) var status: SharedDrillStatus
    public private(set) var completedWithoutMistake: Bool
    public private(set) var moves: [SharedDrillMove]

    private var board: Board

    public init(line: Line) {
        self.line = line
        self.status = .waitingForUser
        self.completedWithoutMistake = true
        self.moves = []
        self.board = Board(position: .standard)
    }

    public var positionFEN: String {
        board.position.fen
    }

    public var plyIndex: Int {
        moves.count
    }

    public var nextBookPly: BookPly? {
        guard plyIndex < line.plies.count else { return nil }
        return line.plies[plyIndex]
    }

    @discardableResult
    public func submit(uci: String) -> SharedDrillSubmitOutcome {
        guard let expected = nextBookPly else {
            status = .lineComplete
            return .lineComplete
        }
        guard let playedMove = EngineLANParser.parse(move: uci, for: board.position.sideToMove, in: board.position) else {
            return .invalidInput
        }
        guard let expectedMove = SANParser.parse(move: expected.san, in: board.position) else {
            return .invalidBookMove(expected)
        }

        guard Self.sameChessMove(playedMove, expectedMove) else {
            completedWithoutMistake = false
            status = .mistake
            return .incorrect(
                SharedDrillMistake(
                    expected: expected,
                    playedUCI: uci,
                    playedSAN: SANParser.convert(move: playedMove)
                )
            )
        }

        let userRecord = apply(expectedMove, san: expected.san, byUser: true)
        let replyRecord = autoplayNextBookPly()
        if plyIndex >= line.plies.count {
            status = .lineComplete
        } else {
            status = .waitingForUser
        }
        return .accepted(
            SharedDrillTurn(
                userMove: userRecord,
                scriptedReply: replyRecord,
                status: status
            )
        )
    }

    @discardableResult
    public func autoplayNextBookPly() -> SharedDrillMove? {
        guard let ply = nextBookPly else {
            status = .lineComplete
            return nil
        }
        guard let move = SANParser.parse(move: ply.san, in: board.position) else {
            return nil
        }
        let record = apply(move, san: ply.san, byUser: false)
        if plyIndex >= line.plies.count {
            status = .lineComplete
        }
        return record
    }

    public func reset() {
        status = .waitingForUser
        completedWithoutMistake = true
        moves = []
        board = Board(position: .standard)
    }

    private func apply(_ move: Move, san: String, byUser: Bool) -> SharedDrillMove {
        var committed = board.move(pieceAt: move.start, to: move.end) ?? move
        if case .promotion = board.state,
           let promo = move.promotedPiece {
            committed = board.completePromotion(of: committed, to: promo.kind)
        }

        let record = SharedDrillMove(
            san: san,
            uci: EngineLANParser.convert(move: committed),
            byUser: byUser,
            fenAfterMove: board.position.fen
        )
        moves.append(record)
        return record
    }

    private static func sameChessMove(_ lhs: Move, _ rhs: Move) -> Bool {
        lhs.start == rhs.start
            && lhs.end == rhs.end
            && lhs.promotedPiece?.kind == rhs.promotedPiece?.kind
    }
}
