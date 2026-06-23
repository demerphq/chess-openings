import ChessKit
import Foundation

public enum SharedSearchBudget: Equatable, Sendable {
    case depth(Int)
    case movetimeMs(Int)
}

public struct SharedEngineLevel: Equatable, Sendable {
    public let stockfishSkill: Int
    public let moveAnalysisDepth: Int

    public init(rawSkill: Int, moveAnalysisDepth: Int = 10) {
        stockfishSkill = max(0, min(20, rawSkill))
        self.moveAnalysisDepth = max(6, min(20, moveAnalysisDepth))
    }

    public static let `default` = SharedEngineLevel(rawSkill: 10)

    public var opponentSearchBudget: SharedSearchBudget {
        .depth(4 + (stockfishSkill * 11) / 20)
    }

    public var moveAnalysisBudget: SharedSearchBudget {
        .depth(moveAnalysisDepth)
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
    var supportsAnalysis: Bool { get }

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

public extension SharedEngineServicing {
    var supportsAnalysis: Bool { false }
}

public final class SharedLegalMoveEngine: SharedEngineServicing {
    public init() {}

    public func bestMove(
        at position: Position,
        skill: Int,
        budget: SharedSearchBudget
    ) async -> SharedEngineDecision? {
        let board = Board(position: position)
        guard let candidate = position.pieces
            .filter({ $0.color == position.sideToMove })
            .sorted(by: { $0.square.notation < $1.square.notation })
            .compactMap({ piece -> String? in
                board.legalMoves(forPieceAt: piece.square)
                    .sorted(by: { $0.notation < $1.notation })
                    .first
                    .map { "\(piece.square.notation)\($0.notation)" }
            })
            .first else {
            return nil
        }
        return SharedEngineDecision(
            move: SharedEngineMove(uci: candidate),
            evaluation: .cp(0)
        )
    }

    public func evaluate(
        at position: Position,
        budget: SharedSearchBudget
    ) async -> SharedEngineEvaluation {
        .cp(0)
    }
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

public struct SharedPlayoutMove: Codable, Equatable, Sendable {
    public let uci: String
    public let san: String
    public let byUser: Bool
    public let fenAfterMove: String
    public let quality: SharedMoveQuality?
}

public struct SharedPlayoutStoredMove: Codable, Equatable, Sendable {
    public let uci: String
    public let byUser: Bool

    public init(uci: String, byUser: Bool) {
        self.uci = uci
        self.byUser = byUser
    }
}

public enum SharedPlayoutSubmitOutcome: Equatable, Sendable {
    case accepted(SharedPlayoutMove, engineReply: SharedPlayoutMove?)
    case invalidInput
    case illegalMove
    case notUserTurn
    case gameOver(SharedGameOverReason)
}

public final class SharedEnginePlayoutSession: @unchecked Sendable {
    public let userSide: OpeningSide
    public let level: SharedEngineLevel
    public private(set) var status: SharedPlayoutStatus
    public private(set) var moves: [SharedPlayoutMove]
    public private(set) var lastEngineEval: SharedEngineEvaluation?
    var resignationThresholdCp: Int = 300
    var resignationWindowSize: Int = 6

    private let engine: SharedEngineServicing
    private let initialFEN: String
    private var board: Board
    private var history: [(move: Move, byUser: Bool)]
    private var resignationWindow: [SharedEngineEvaluation]

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
        self.resignationWindow = []
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

        let preMovePosition = board.position
        let analysisDecision = await analysisDecisionIfAvailable(
            at: preMovePosition
        )
        var userRecord = recordApply(move, byUser: true)
        if let quality = await moveQualityIfAvailable(
            userMove: move,
            pre: preMovePosition,
            post: board.position,
            decision: analysisDecision
        ) {
            userRecord = userRecord.withQuality(quality)
            updateLatestMoveQuality(quality)
        }
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

    public func declineEngineResignation() {
        guard case .gameOver(.engineResigned(accepted: nil)) = status else {
            return
        }
        resignationWindow.removeAll()
        status = .waitingForUser
    }

    public func acceptEngineResignation() {
        guard case .gameOver(.engineResigned(accepted: nil)) = status else {
            return
        }
        status = .gameOver(.engineResigned(accepted: true))
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

    public func restore(moves persisted: [SharedPlayoutStoredMove]) -> Bool {
        resetToInitialPosition()
        for stored in persisted {
            guard let move = parseUCI(stored.uci, in: board.position),
                  board.canMove(pieceAt: move.start, to: move.end),
                  !stored.byUser || move.piece.color.openingSide == userSide else {
                resetToInitialPosition()
                return false
            }
            _ = recordApply(move, byUser: stored.byUser)
        }

        if let reason = Self.reason(forBoardState: board.state) {
            status = .gameOver(reason)
        } else {
            status = Self.userIsOnMove(userSide: userSide, position: board.position)
                ? .waitingForUser
                : .engineThinking
        }
        return true
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
        } else if let eval = decision.evaluation,
                  isEngineLosing(eval, thresholdCp: resignationThresholdCp) {
            resignationWindow.append(eval)
            if resignationWindow.count >= resignationWindowSize {
                status = .gameOver(.engineResigned(accepted: nil))
            } else {
                status = .waitingForUser
            }
        } else {
            resignationWindow.removeAll()
            status = .waitingForUser
        }
        return reply
    }

    private func isEngineLosing(
        _ eval: SharedEngineEvaluation,
        thresholdCp: Int
    ) -> Bool {
        switch eval {
        case .cp(let value):
            return value <= -thresholdCp
        case .mate(let moves):
            return moves < 0
        }
    }

    private func recordApply(
        _ move: Move,
        byUser: Bool,
        quality: SharedMoveQuality? = nil
    ) -> SharedPlayoutMove {
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
            fenAfterMove: board.position.fen,
            quality: quality
        )
        moves.append(record)
        return record
    }

    private func updateLatestMoveQuality(_ quality: SharedMoveQuality) {
        guard let last = moves.last else { return }
        moves[moves.count - 1] = SharedPlayoutMove(
            uci: last.uci,
            san: last.san,
            byUser: last.byUser,
            fenAfterMove: last.fenAfterMove,
            quality: quality
        )
    }

    private func analysisDecisionIfAvailable(
        at position: Position
    ) async -> SharedEngineDecision? {
        guard engine.supportsAnalysis else { return nil }
        return await engine.bestMove(
            at: position,
            skill: 20,
            budget: level.moveAnalysisBudget
        )
    }

    private func moveQualityIfAvailable(
        userMove: Move,
        pre: Position,
        post: Position,
        decision: SharedEngineDecision?
    ) async -> SharedMoveQuality? {
        guard engine.supportsAnalysis, let decision else { return nil }
        let postEval = await engine.evaluate(
            at: post,
            budget: level.moveAnalysisBudget
        )
        let bestCp = decision.evaluation?.clampedCp ?? 0
        let actualCp = -postEval.clampedCp
        let isBrilliant = SharedMoveQualityHeuristics.isBrilliantCandidate(
            userMove: userMove,
            pre: pre,
            post: post,
            userSide: userSide,
            bestUCI: decision.move.uci,
            bestEvalCp: bestCp,
            actualEvalCp: actualCp
        )
        return SharedMoveQuality.classify(
            bestEvalCp: bestCp,
            actualEvalCp: actualCp,
            bestEvalIsWinning: bestCp >= 200,
            isBrilliantCandidate: isBrilliant
        )
    }

    private func rebuild(from retainedHistory: [(move: Move, byUser: Bool)]) {
        resetToInitialPosition()
        retainedHistory.forEach { record in
            _ = recordApply(record.move, byUser: record.byUser)
        }
    }

    private func resetToInitialPosition() {
        guard let start = Position(fen: initialFEN) else { return }
        board = Board(position: start)
        history = []
        moves = []
        lastEngineEval = nil
        resignationWindow = []
        status = Self.userIsOnMove(userSide: userSide, position: start)
            ? .waitingForUser
            : .engineThinking
    }

    private func parseUCI(_ uci: String, in position: Position) -> Move? {
        EngineLANParser.parse(
            move: uci,
            for: position.sideToMove,
            in: position
        )
    }

    private static func userIsOnMove(userSide: OpeningSide, position: Position) -> Bool {
        switch (userSide, position.sideToMove) {
        case (.white, .white), (.black, .black):
            return true
        default:
            return false
        }
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

private extension SharedPlayoutMove {
    func withQuality(_ quality: SharedMoveQuality) -> SharedPlayoutMove {
        SharedPlayoutMove(
            uci: uci,
            san: san,
            byUser: byUser,
            fenAfterMove: fenAfterMove,
            quality: quality
        )
    }
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
