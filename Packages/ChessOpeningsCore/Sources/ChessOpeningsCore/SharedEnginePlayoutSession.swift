import ChessKit
import Foundation

public enum SharedSearchBudget: Equatable, Sendable {
    case depth(Int)
    case movetimeMs(Int)
}

public struct SharedEngineLevel: Equatable, Sendable {
    public let stockfishSkill: Int

    public init(rawSkill: Int) {
        stockfishSkill = max(0, min(20, rawSkill))
    }

    public static let `default` = SharedEngineLevel(rawSkill: 10)

    public var opponentSearchBudget: SharedSearchBudget {
        .depth(4 + (stockfishSkill * 11) / 20)
    }
}

public struct SharedEngineMove: Equatable, Sendable {
    public let uci: String

    public init(uci: String) {
        self.uci = uci
    }
}

public enum SharedEngineEvaluation: Equatable, Sendable {
    case cp(Int)
    case mate(in: Int)
}

public struct SharedEngineDecision: Equatable, Sendable {
    public let move: SharedEngineMove
    public let evaluation: SharedEngineEvaluation?

    public init(move: SharedEngineMove, evaluation: SharedEngineEvaluation?) {
        self.move = move
        self.evaluation = evaluation
    }
}

public protocol SharedEngineServicing: AnyObject {
    func bestMove(
        at position: Position,
        skill: Int,
        budget: SharedSearchBudget
    ) async -> SharedEngineDecision?

    func evaluate(
        at position: Position,
        budget: SharedSearchBudget
    ) async -> SharedEngineEvaluation
}

public enum SharedPlayoutStatus: Equatable, Sendable {
    case waitingForUser
    case engineThinking
    case drawOffered(byUser: Bool)
    case gameOver(SharedGameOverReason)
}

public enum SharedGameOverReason: Equatable, Sendable {
    case checkmate(winner: OpeningSide)
    case stalemate
    case fiftyMoveRule
    case threefoldRepetition
    case insufficientMaterial
    case drawAgreed
    case userResigned
    case engineResigned(accepted: Bool?)
}

public struct SharedPlayoutMove: Equatable, Sendable {
    public let uci: String
    public let san: String
    public let byUser: Bool
    public let fenAfterMove: String
}

public enum SharedPlayoutSubmitOutcome: Equatable, Sendable {
    case accepted(SharedPlayoutMove, engineReply: SharedPlayoutMove?)
    case invalidInput
    case illegalMove
    case notUserTurn
    case gameOver(SharedGameOverReason)
}

public final class SharedEnginePlayoutSession {
    public let userSide: OpeningSide
    public let level: SharedEngineLevel
    public private(set) var status: SharedPlayoutStatus
    public private(set) var moves: [SharedPlayoutMove]
    public private(set) var lastEngineEval: SharedEngineEvaluation?

    private let engine: SharedEngineServicing
    private let initialFEN: String
    private var board: Board
    private var history: [(move: Move, byUser: Bool)]

    public init(
        startingFEN: String,
        userSide: OpeningSide,
        level: SharedEngineLevel = .default,
        engine: SharedEngineServicing
    ) throws {
        guard let position = Position(fen: startingFEN) else {
            throw SharedEnginePlayoutError.invalidFEN
        }
        self.userSide = userSide
        self.level = level
        self.engine = engine
        self.initialFEN = position.fen
        self.board = Board(position: position)
        self.moves = []
        self.lastEngineEval = nil
        self.history = []
        self.status = Self.userIsOnMove(userSide: userSide, position: position)
            ? .waitingForUser
            : .engineThinking
    }

    public var positionFEN: String {
        board.position.fen
    }

    public var plyIndex: Int {
        moves.count
    }

    public func bootstrap() async {
        guard status == .engineThinking else { return }
        _ = await playEngineReply()
    }

    public func submit(uci: String) async -> SharedPlayoutSubmitOutcome {
        guard status == .waitingForUser else {
            if case .gameOver(let reason) = status {
                return .gameOver(reason)
            }
            return .notUserTurn
        }
        guard let move = parseUCI(uci, in: board.position) else {
            return .invalidInput
        }
        guard move.piece.color.openingSide == userSide,
              Self.userIsOnMove(userSide: userSide, position: board.position) else {
            return .illegalMove
        }
        guard board.canMove(pieceAt: move.start, to: move.end) else {
            return .illegalMove
        }

        let userRecord = recordApply(move, byUser: true)
        if let reason = Self.reason(forBoardState: board.state) {
            status = .gameOver(reason)
            return .gameOver(reason)
        }
        if case .promotion = board.state {
            return .accepted(userRecord, engineReply: nil)
        }

        let reply = await playEngineReply()
        return .accepted(userRecord, engineReply: reply)
    }

    public func offerDraw() async {
        guard status == .waitingForUser else { return }
        status = .drawOffered(byUser: true)
        let eval = await engine.evaluate(at: board.position, budget: .depth(12))
        if case .cp(let value) = eval, abs(value) <= 50 {
            status = .gameOver(.drawAgreed)
        } else {
            status = .waitingForUser
        }
    }

    public func resign() {
        if case .gameOver = status { return }
        status = .gameOver(.userResigned)
    }

    public func undo() {
        if case .gameOver = status { return }
        guard let lastUserMove = history.lastIndex(where: { $0.byUser }) else {
            return
        }
        let retained = history.prefix(lastUserMove)
        rebuild(from: Array(retained))
        status = .waitingForUser
    }

    private func playEngineReply() async -> SharedPlayoutMove? {
        status = .engineThinking
        let decision = await engine.bestMove(
            at: board.position,
            skill: level.stockfishSkill,
            budget: level.opponentSearchBudget
        )
        guard let decision,
              let move = parseUCI(decision.move.uci, in: board.position) else {
            status = .waitingForUser
            return nil
        }

        let reply = recordApply(move, byUser: false)
        lastEngineEval = decision.evaluation
        if let reason = Self.reason(forBoardState: board.state) {
            status = .gameOver(reason)
        } else {
            status = .waitingForUser
        }
        return reply
    }

    private func recordApply(_ move: Move, byUser: Bool) -> SharedPlayoutMove {
        let san = SANParser.convert(move: move)
        var committed = board.move(pieceAt: move.start, to: move.end) ?? move
        if case .promotion = board.state,
           let promo = move.promotedPiece {
            committed = board.completePromotion(of: committed, to: promo.kind)
        }
        history.append((move, byUser))
        let record = SharedPlayoutMove(
            uci: EngineLANParser.convert(move: committed),
            san: san,
            byUser: byUser,
            fenAfterMove: board.position.fen
        )
        moves.append(record)
        return record
    }

    private func rebuild(from retainedHistory: [(move: Move, byUser: Bool)]) {
        guard let start = Position(fen: initialFEN) else { return }
        board = Board(position: start)
        history = []
        moves = []
        retainedHistory.forEach { record in
            _ = recordApply(record.move, byUser: record.byUser)
        }
    }

    private func parseUCI(_ uci: String, in position: Position) -> Move? {
        guard uci.count == 4 else { return nil }
        let from = String(uci.prefix(2))
        let to = String(uci.dropFirst(2).prefix(2))
        guard Self.isAlgebraicSquare(from),
              Self.isAlgebraicSquare(to),
              let piece = position.piece(at: Square(from)) else {
            return nil
        }
        return Move(result: .move, piece: piece, start: Square(from), end: Square(to))
    }

    private static func userIsOnMove(userSide: OpeningSide, position: Position) -> Bool {
        switch (userSide, position.sideToMove) {
        case (.white, .white), (.black, .black):
            return true
        default:
            return false
        }
    }

    private static func isAlgebraicSquare(_ value: String) -> Bool {
        guard value.count == 2 else { return false }
        let file = value.first!
        let rank = value.last!
        return ("a"..."h").contains(file) && ("1"..."8").contains(rank)
    }

    private static func reason(forBoardState state: Board.State) -> SharedGameOverReason? {
        switch state {
        case .checkmate(let losingColor):
            return .checkmate(winner: losingColor.openingSide.opposite)
        case .draw(let reason):
            switch reason {
            case .stalemate: return .stalemate
            case .fiftyMoves: return .fiftyMoveRule
            case .repetition: return .threefoldRepetition
            case .insufficientMaterial: return .insufficientMaterial
            case .agreement: return .drawAgreed
            }
        case .active, .check, .promotion:
            return nil
        }
    }
}

public enum SharedEnginePlayoutError: Error, Equatable {
    case invalidFEN
}

private extension OpeningSide {
    var opposite: OpeningSide {
        switch self {
        case .white: return .black
        case .black: return .white
        }
    }
}

private extension Piece.Color {
    var openingSide: OpeningSide {
        switch self {
        case .white: return .white
        case .black: return .black
        }
    }
}
