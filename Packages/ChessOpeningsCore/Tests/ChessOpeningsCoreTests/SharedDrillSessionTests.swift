import Testing
@testable import ChessOpeningsCore

@Test func sharedDrillAcceptsUserMoveAndAutoplaysReply() throws {
    let session = SharedDrillSession(line: italianLine)

    let outcome = session.submit(uci: "e2e4")

    guard case .accepted(let turn) = outcome else {
        Issue.record("expected accepted outcome, got \(outcome)")
        return
    }
    #expect(turn.userMove.san == "e4")
    #expect(turn.userMove.uci == "e2e4")
    #expect(turn.userMove.byUser)
    #expect(turn.scriptedReply?.san == "e5")
    #expect(turn.scriptedReply?.uci == "e7e5")
    #expect(turn.scriptedReply?.byUser == false)
    #expect(turn.status == .waitingForUser)
    #expect(session.plyIndex == 2)
    #expect(session.nextBookPly?.san == "Nf3")
}

@Test func sharedDrillRejectsOffBookMoveWithoutAdvancing() throws {
    let session = SharedDrillSession(line: italianLine)

    let outcome = session.submit(uci: "d2d4")

    guard case .incorrect(let mistake) = outcome else {
        Issue.record("expected incorrect outcome, got \(outcome)")
        return
    }
    #expect(mistake.expected.san == "e4")
    #expect(mistake.playedUCI == "d2d4")
    #expect(mistake.playedSAN == "d4")
    #expect(session.status == .mistake)
    #expect(!session.completedWithoutMistake)
    #expect(session.plyIndex == 0)
}

@Test func sharedDrillAutoplaysOpeningMoveForBlackSideDrill() throws {
    let session = SharedDrillSession(line: italianLine)

    let opener = try #require(session.autoplayNextBookPly())

    #expect(opener.san == "e4")
    #expect(opener.uci == "e2e4")
    #expect(!opener.byUser)
    #expect(session.plyIndex == 1)
    #expect(session.nextBookPly?.san == "e5")

    let replyOutcome = session.submit(uci: "e7e5")
    guard case .accepted(let turn) = replyOutcome else {
        Issue.record("expected accepted reply, got \(replyOutcome)")
        return
    }
    #expect(turn.userMove.san == "e5")
    #expect(turn.scriptedReply?.san == "Nf3")
    #expect(session.plyIndex == 3)
    #expect(session.status == .lineComplete)
}

@Test func sharedDrillResetClearsHistory() throws {
    let session = SharedDrillSession(line: italianLine)
    _ = session.submit(uci: "e2e4")

    session.reset()

    #expect(session.status == .waitingForUser)
    #expect(session.completedWithoutMistake)
    #expect(session.moves.isEmpty)
    #expect(session.plyIndex == 0)
    #expect(session.nextBookPly?.san == "e4")
}

@Test func sharedDrillUndoRemovesUserMoveAndScriptedReply() throws {
    let session = SharedDrillSession(line: italianLine)
    _ = session.submit(uci: "e2e4")

    session.undo()

    #expect(session.status == .waitingForUser)
    #expect(session.plyIndex == 0)
    #expect(session.moves.isEmpty)
    #expect(session.nextBookPly?.san == "e4")
}

@Test func sharedDrillUndoPreservesBlackSideOpeningMove() throws {
    let session = SharedDrillSession(line: italianLine)
    _ = session.autoplayNextBookPly()
    _ = session.submit(uci: "e7e5")

    session.undo()

    #expect(session.status == .waitingForUser)
    #expect(session.plyIndex == 1)
    #expect(session.moves.map(\.san) == ["e4"])
    #expect(session.nextBookPly?.san == "e5")
}

@Test func sharedDrillRestoreRebuildsWhiteSideHistoryForUndo() throws {
    let session = SharedDrillSession(line: italianLine)

    session.restore(plyIndex: 2, userSide: .white)

    #expect(session.plyIndex == 2)
    #expect(session.moves.map(\.san) == ["e4", "e5"])
    #expect(session.moves.map(\.byUser) == [true, false])
    #expect(session.nextBookPly?.san == "Nf3")

    session.undo()

    #expect(session.plyIndex == 0)
    #expect(session.moves.isEmpty)
    #expect(session.nextBookPly?.san == "e4")
}

@Test func sharedDrillRestoreRebuildsBlackSideHistoryForUndo() throws {
    let session = SharedDrillSession(line: italianLine)

    session.restore(plyIndex: 3, userSide: .black)

    #expect(session.plyIndex == 3)
    #expect(session.status == .lineComplete)
    #expect(session.moves.map(\.san) == ["e4", "e5", "Nf3"])
    #expect(session.moves.map(\.byUser) == [false, true, false])

    session.undo()

    #expect(session.plyIndex == 1)
    #expect(session.moves.map(\.san) == ["e4"])
    #expect(session.nextBookPly?.san == "e5")
}

private let italianLine = Line(
    name: "Bc5",
    source: .masters,
    tags: [],
    plies: [
        BookPly(san: "e4", uci: "e2e4", annotation: nil, alternativeSans: []),
        BookPly(san: "e5", uci: "e7e5", annotation: nil, alternativeSans: []),
        BookPly(san: "Nf3", uci: "g1f3", annotation: nil, alternativeSans: []),
    ]
)
