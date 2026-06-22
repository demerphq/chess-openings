import ChessOpeningsCore

@_cdecl("chess_openings_core_can_play_kings_pawn_opening")
public func chessOpeningsCoreCanPlayKingsPawnOpening() -> Int32 {
    CoreValidation.canPlayKingsPawnOpening() ? 1 : 0
}

@_cdecl("Java_com_chessopenings_app_SharedCoreBridge_canPlayKingsPawnOpening")
public func Java_com_chessopenings_app_SharedCoreBridge_canPlayKingsPawnOpening(
    _ environment: UnsafeMutableRawPointer?,
    _ receiver: UnsafeMutableRawPointer?
) -> Int32 {
    chessOpeningsCoreCanPlayKingsPawnOpening()
}
