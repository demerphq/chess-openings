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
        let userOutcome = submitUserMoveOnly(uci: uci)
        guard case .accepted(let userTurn) = userOutcome else {
            return userOutcome
        }
        guard status != .lineComplete else {
            return userOutcome
        }

        let replyRecord = autoplayNextBookPly()
        if plyIndex >= line.plies.count {
            status = .lineComplete
        } else {
            status = .waitingForUser
        }
        return .accepted(
            SharedDrillTurn(
                userMove: userTurn.userMove,
                scriptedReply: replyRecord,
                status: status
            )
        )
    }

    @discardableResult
    public func submitUserMoveOnly(uci: String) -> SharedDrillSubmitOutcome {
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
        if plyIndex >= line.plies.count {
            status = .lineComplete
        } else {
            status = .waitingForUser
        }
        return .accepted(
            SharedDrillTurn(
                userMove: userRecord,
                scriptedReply: nil,
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

    public func restore(plyIndex requestedPlyIndex: Int, userSide: OpeningSide) {
        reset()

        let restoredPlyCount = min(max(requestedPlyIndex, 0), line.plies.count)
        for index in 0..<restoredPlyCount {
            let ply = line.plies[index]
            guard let move = SANParser.parse(move: ply.san, in: board.position) else {
                reset()
                return
            }
            _ = apply(move, san: ply.san, byUser: Self.isUserPly(index, userSide: userSide))
        }
        if plyIndex >= line.plies.count {
            status = .lineComplete
        }
    }

    public func undo() {
        guard !moves.isEmpty else { return }

        var restoredMoves = moves
        var removedUserMove = false
        while !restoredMoves.isEmpty {
            let removed = restoredMoves.removeLast()
            if removed.byUser {
                removedUserMove = true
                break
            }
        }
        guard removedUserMove else {
            reset()
            return
        }

        rebuild(from: restoredMoves)
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

    private func rebuild(from restoredMoves: [SharedDrillMove]) {
        status = .waitingForUser
        board = Board(position: .standard)
        moves = []

        for record in restoredMoves {
            guard let move = SANParser.parse(move: record.san, in: board.position) else {
                reset()
                return
            }
            _ = apply(move, san: record.san, byUser: record.byUser)
        }
        if plyIndex >= line.plies.count {
            status = .lineComplete
        }
    }

    private static func sameChessMove(_ lhs: Move, _ rhs: Move) -> Bool {
        lhs.start == rhs.start
            && lhs.end == rhs.end
            && lhs.promotedPiece?.kind == rhs.promotedPiece?.kind
    }

    private static func isUserPly(_ index: Int, userSide: OpeningSide) -> Bool {
        switch userSide {
        case .white:
            return index.isMultiple(of: 2)
        case .black:
            return !index.isMultiple(of: 2)
        }
    }
}
