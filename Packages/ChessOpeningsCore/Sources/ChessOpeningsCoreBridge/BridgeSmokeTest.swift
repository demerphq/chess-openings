import ChessOpeningsCore

@_cdecl("chess_openings_core_can_play_kings_pawn_opening")
public func chessOpeningsCoreCanPlayKingsPawnOpening() -> Int32 {
    CoreValidation.canPlayKingsPawnOpening() ? 1 : 0
}

@_cdecl("chess_openings_core_can_run_shared_drill_session")
public func chessOpeningsCoreCanRunSharedDrillSession() -> Int32 {
    let session = SharedDrillSession(line: bridgeItalianLine)

    guard case .accepted(let turn) = session.submit(uci: "e2e4") else {
        return 0
    }
    guard turn.userMove.san == "e4",
          turn.userMove.uci == "e2e4",
          turn.userMove.byUser,
          turn.scriptedReply?.san == "e5",
          turn.scriptedReply?.uci == "e7e5",
          turn.scriptedReply?.byUser == false,
          turn.status == .waitingForUser,
          session.plyIndex == 2,
          session.nextBookPly?.san == "Nf3" else {
        return 0
    }
    return 1
}

@_cdecl("Java_com_chessopenings_app_SharedCoreBridge_canPlayKingsPawnOpening")
public func Java_com_chessopenings_app_SharedCoreBridge_canPlayKingsPawnOpening(
    _ environment: UnsafeMutableRawPointer?,
    _ receiver: UnsafeMutableRawPointer?
) -> Int32 {
    chessOpeningsCoreCanPlayKingsPawnOpening()
}

@_cdecl("Java_com_chessopenings_app_SharedCoreBridge_canRunSharedDrillSession")
public func Java_com_chessopenings_app_SharedCoreBridge_canRunSharedDrillSession(
    _ environment: UnsafeMutableRawPointer?,
    _ receiver: UnsafeMutableRawPointer?
) -> Int32 {
    chessOpeningsCoreCanRunSharedDrillSession()
}

private let bridgeItalianLine = Line(
    name: "Bc5",
    source: .masters,
    tags: [],
    plies: [
        BookPly(san: "e4", uci: "e2e4", annotation: nil, alternativeSans: []),
        BookPly(san: "e5", uci: "e7e5", annotation: nil, alternativeSans: []),
        BookPly(san: "Nf3", uci: "g1f3", annotation: nil, alternativeSans: []),
    ]
)
