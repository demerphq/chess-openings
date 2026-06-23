import ChessKit
import Testing
@testable import ChessOpeningsCore

@Test func sharedPlayoutAcceptsUserMoveAndEngineReply() async throws {
    let engine = FakeSharedEngineService(
        bestMoves: ["e7e5"],
        evaluations: [.cp(12)]
    )
    let session = try SharedEnginePlayoutSession(
        startingFEN: Position.standard.fen,
        userSide: .white,
        engine: engine
    )

    let outcome = await session.submit(uci: "e2e4")

    guard case .accepted(let userMove, let reply) = outcome else {
        Issue.record("expected accepted outcome, got \(outcome)")
        return
    }
    #expect(userMove.uci == "e2e4")
    #expect(userMove.byUser)
    #expect(reply?.uci == "e7e5")
    #expect(reply?.byUser == false)
    #expect(session.status == .waitingForUser)
    #expect(session.plyIndex == 2)
    #expect(session.lastEngineEval == .cp(12))
    #expect(engine.lastSkill == 10)
}

@Test func sharedPlayoutBootstrapsEngineFirstPosition() async throws {
    let engine = FakeSharedEngineService(bestMoves: ["e2e4"])
    let session = try SharedEnginePlayoutSession(
        startingFEN: Position.standard.fen,
        userSide: .black,
        engine: engine
    )

    #expect(session.status == .engineThinking)

    await session.bootstrap()

    #expect(session.status == .waitingForUser)
    #expect(session.moves.map(\.uci) == ["e2e4"])
    #expect(session.moves.map(\.byUser) == [false])
}

@Test func sharedPlayoutRejectsIllegalMoveWithoutAdvancing() async throws {
    let session = try SharedEnginePlayoutSession(
        startingFEN: Position.standard.fen,
        userSide: .white,
        engine: FakeSharedEngineService()
    )

    let outcome = await session.submit(uci: "e7e5")

    #expect(outcome == .illegalMove)
    #expect(session.plyIndex == 0)
    #expect(session.status == .waitingForUser)
}

@Test func sharedPlayoutUndoRemovesUserMoveAndEngineReply() async throws {
    let session = try SharedEnginePlayoutSession(
        startingFEN: Position.standard.fen,
        userSide: .white,
        engine: FakeSharedEngineService(bestMoves: ["e7e5"])
    )
    _ = await session.submit(uci: "e2e4")

    session.undo()

    #expect(session.plyIndex == 0)
    #expect(session.moves.isEmpty)
    #expect(session.positionFEN == Position.standard.fen)
    #expect(session.status == .waitingForUser)
}

@Test func sharedPlayoutUndoPreservesEngineFirstMove() async throws {
    let session = try SharedEnginePlayoutSession(
        startingFEN: Position.standard.fen,
        userSide: .black,
        engine: FakeSharedEngineService(bestMoves: ["e2e4", "b8c6"])
    )
    await session.bootstrap()
    _ = await session.submit(uci: "e7e5")

    session.undo()

    #expect(session.plyIndex == 1)
    #expect(session.moves.map(\.uci) == ["e2e4"])
    #expect(session.moves.map(\.byUser) == [false])
    #expect(session.status == .waitingForUser)
}

@Test func sharedPlayoutHandlesDrawOffer() async throws {
    let engine = FakeSharedEngineService(evaluations: [.cp(40)])
    let session = try SharedEnginePlayoutSession(
        startingFEN: Position.standard.fen,
        userSide: .white,
        engine: engine
    )

    await session.offerDraw()

    #expect(session.status == .gameOver(.drawAgreed))
    #expect(engine.evaluateCalls == 1)
}

@Test func sharedPlayoutRejectsInvalidFen() async throws {
    #expect(throws: SharedEnginePlayoutError.invalidFEN) {
        _ = try SharedEnginePlayoutSession(
            startingFEN: "not a fen",
            userSide: .white,
            engine: FakeSharedEngineService()
        )
    }
}

private final class FakeSharedEngineService: SharedEngineServicing {
    var bestMoves: [String]
    var evaluations: [SharedEngineEvaluation]
    private(set) var bestMoveCalls = 0
    private(set) var evaluateCalls = 0
    private(set) var lastSkill: Int?

    init(
        bestMoves: [String] = [],
        evaluations: [SharedEngineEvaluation] = []
    ) {
        self.bestMoves = bestMoves
        self.evaluations = evaluations
    }

    func bestMove(
        at position: Position,
        skill: Int,
        budget: SharedSearchBudget
    ) async -> SharedEngineDecision? {
        bestMoveCalls += 1
        lastSkill = skill
        guard !bestMoves.isEmpty else { return nil }
        let evaluation = evaluations.isEmpty ? nil : evaluations.removeFirst()
        return SharedEngineDecision(
            move: SharedEngineMove(uci: bestMoves.removeFirst()),
            evaluation: evaluation
        )
    }

    func evaluate(
        at position: Position,
        budget: SharedSearchBudget
    ) async -> SharedEngineEvaluation {
        evaluateCalls += 1
        return evaluations.isEmpty ? .cp(0) : evaluations.removeFirst()
    }
}
