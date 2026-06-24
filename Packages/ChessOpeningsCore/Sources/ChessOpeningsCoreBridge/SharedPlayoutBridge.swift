import ChessOpeningsCore
import Foundation

@_cdecl("chess_openings_core_shared_playout_create")
public func chessOpeningsCoreSharedPlayoutCreate(
    _ startingFEN: UnsafePointer<CChar>?,
    _ userSide: Int32,
    _ engineSkill: Int32,
    _ moveAnalysisDepth: Int32
) -> Int64 {
    guard let startingFEN,
          let side = SharedPlayoutBridgeUserSide(rawValue: userSide)?.openingSide,
          let session = try? SharedEnginePlayoutSession(
            startingFEN: String(cString: startingFEN),
            userSide: side,
            level: SharedEngineLevel(
                rawSkill: Int(engineSkill),
                moveAnalysisDepth: Int(moveAnalysisDepth)
            ),
            engine: SharedPlayoutEngineFactory.shared.makeEngine()
          ) else {
        return 0
    }
    return SharedPlayoutBridgeStore.shared.insert(session)
}

@_cdecl("chess_openings_core_shared_engine_configure")
public func chessOpeningsCoreSharedEngineConfigure(
    _ executablePath: UnsafePointer<CChar>?,
    _ nnueDirectory: UnsafePointer<CChar>?
) {
    SharedPlayoutEngineFactory.shared.configure(
        executablePath: executablePath.map { String(cString: $0) },
        nnueDirectory: nnueDirectory.map { String(cString: $0) }
    )
}

@_cdecl("chess_openings_core_shared_playout_bootstrap")
public func chessOpeningsCoreSharedPlayoutBootstrap(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return SharedPlayoutBridgeOutcome.invalidHandle.rawValue
    }
    waitForAsync { await session.bootstrap() }
    return SharedPlayoutBridgeOutcome.accepted.rawValue
}

@_cdecl("chess_openings_core_shared_playout_submit")
public func chessOpeningsCoreSharedPlayoutSubmit(
    _ handle: Int64,
    _ uci: UnsafePointer<CChar>?
) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle),
          let uci else {
        return SharedPlayoutBridgeOutcome.invalidHandle.rawValue
    }
    let moveUCI = String(cString: uci)

    let outcome = waitForAsync {
        await session.submit(uci: moveUCI)
    }
    return SharedPlayoutBridgeOutcome(outcome).rawValue
}

@_cdecl("chess_openings_core_shared_playout_stage_user")
public func chessOpeningsCoreSharedPlayoutStageUser(
    _ handle: Int64,
    _ uci: UnsafePointer<CChar>?
) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle),
          let uci else {
        return SharedPlayoutBridgeOutcome.invalidHandle.rawValue
    }
    return SharedPlayoutBridgeOutcome(
        session.stageUserMove(uci: String(cString: uci))
    ).rawValue
}

@_cdecl("chess_openings_core_shared_playout_complete_turn")
public func chessOpeningsCoreSharedPlayoutCompleteTurn(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return SharedPlayoutBridgeOutcome.invalidHandle.rawValue
    }
    let outcome = waitForAsync { await session.completeStagedTurn() }
    return SharedPlayoutBridgeOutcome(outcome).rawValue
}

@_cdecl("chess_openings_core_shared_playout_best_move")
public func chessOpeningsCoreSharedPlayoutBestMove(
    _ handle: Int64,
    _ buffer: UnsafeMutablePointer<CChar>?,
    _ capacity: Int32
) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle),
          let move = waitForAsync({ await session.bestMoveHint() }) else {
        return -1
    }
    guard let buffer, capacity > 0 else {
        return Int32(move.uci.utf8.count)
    }

    let utf8 = Array(move.uci.utf8)
    let writableCount = min(utf8.count, Int(capacity) - 1)
    for index in 0..<writableCount {
        buffer[index] = CChar(bitPattern: utf8[index])
    }
    buffer[writableCount] = 0
    return Int32(utf8.count)
}

@_cdecl("chess_openings_core_shared_playout_precompute_analysis")
public func chessOpeningsCoreSharedPlayoutPrecomputeAnalysis(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return -1
    }
    return waitForAsync { await session.precomputeMoveAnalysis() } ? 1 : 0
}

@_cdecl("chess_openings_core_shared_playout_ply_index")
public func chessOpeningsCoreSharedPlayoutPlyIndex(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return -1
    }
    return Int32(session.plyIndex)
}

@_cdecl("chess_openings_core_shared_playout_status")
public func chessOpeningsCoreSharedPlayoutStatus(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return SharedPlayoutBridgeStatus.invalidHandle.rawValue
    }
    return SharedPlayoutBridgeStatus(session.status).rawValue
}

@_cdecl("chess_openings_core_shared_playout_game_over_reason")
public func chessOpeningsCoreSharedPlayoutGameOverReason(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return SharedPlayoutBridgeGameOverReason.invalidHandle.rawValue
    }
    guard case .gameOver(let reason) = session.status else {
        return SharedPlayoutBridgeGameOverReason.none.rawValue
    }
    return SharedPlayoutBridgeGameOverReason(reason).rawValue
}

@_cdecl("chess_openings_core_shared_playout_position_fen")
public func chessOpeningsCoreSharedPlayoutPositionFEN(
    _ handle: Int64,
    _ buffer: UnsafeMutablePointer<CChar>?,
    _ capacity: Int32
) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return -1
    }
    guard let buffer, capacity > 0 else {
        return Int32(session.positionFEN.utf8.count)
    }

    let utf8 = Array(session.positionFEN.utf8)
    let writableCount = min(utf8.count, Int(capacity) - 1)
    for index in 0..<writableCount {
        buffer[index] = CChar(bitPattern: utf8[index])
    }
    buffer[writableCount] = 0
    return Int32(utf8.count)
}

@_cdecl("chess_openings_core_shared_playout_moves_json")
public func chessOpeningsCoreSharedPlayoutMovesJSON(
    _ handle: Int64,
    _ buffer: UnsafeMutablePointer<CChar>?,
    _ capacity: Int32
) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle),
          let data = try? JSONEncoder().encode(session.moves),
          let json = String(data: data, encoding: .utf8) else {
        return -1
    }
    guard let buffer, capacity > 0 else {
        return Int32(json.utf8.count)
    }

    let utf8 = Array(json.utf8)
    let writableCount = min(utf8.count, Int(capacity) - 1)
    for index in 0..<writableCount {
        buffer[index] = CChar(bitPattern: utf8[index])
    }
    buffer[writableCount] = 0
    return Int32(utf8.count)
}

@_cdecl("chess_openings_core_shared_playout_restore_moves")
public func chessOpeningsCoreSharedPlayoutRestoreMoves(
    _ handle: Int64,
    _ movesJSON: UnsafePointer<CChar>?
) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle),
          let movesJSON,
          let data = String(cString: movesJSON).data(using: .utf8),
          let moves = try? JSONDecoder().decode([SharedPlayoutStoredMove].self, from: data) else {
        return -1
    }
    return session.restore(moves: moves) ? 1 : 0
}

@_cdecl("chess_openings_core_shared_playout_offer_draw")
public func chessOpeningsCoreSharedPlayoutOfferDraw(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return SharedPlayoutBridgeStatus.invalidHandle.rawValue
    }
    waitForAsync { await session.offerDraw() }
    return SharedPlayoutBridgeStatus(session.status).rawValue
}

@_cdecl("chess_openings_core_shared_playout_resign")
public func chessOpeningsCoreSharedPlayoutResign(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return SharedPlayoutBridgeStatus.invalidHandle.rawValue
    }
    session.resign()
    return SharedPlayoutBridgeStatus(session.status).rawValue
}

@_cdecl("chess_openings_core_shared_playout_engine_resignation")
public func chessOpeningsCoreSharedPlayoutEngineResignation(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return -1
    }
    guard case .gameOver(.engineResigned(let accepted)) = session.status else {
        return 0
    }
    return accepted == true ? 2 : 1
}

@_cdecl("chess_openings_core_shared_playout_accept_engine_resignation")
public func chessOpeningsCoreSharedPlayoutAcceptEngineResignation(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return SharedPlayoutBridgeStatus.invalidHandle.rawValue
    }
    session.acceptEngineResignation()
    return SharedPlayoutBridgeStatus(session.status).rawValue
}

@_cdecl("chess_openings_core_shared_playout_decline_engine_resignation")
public func chessOpeningsCoreSharedPlayoutDeclineEngineResignation(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return SharedPlayoutBridgeStatus.invalidHandle.rawValue
    }
    session.declineEngineResignation()
    return SharedPlayoutBridgeStatus(session.status).rawValue
}

@_cdecl("chess_openings_core_shared_playout_undo")
public func chessOpeningsCoreSharedPlayoutUndo(_ handle: Int64) -> Int32 {
    guard let session = SharedPlayoutBridgeStore.shared.session(for: handle) else {
        return -1
    }
    session.undo()
    return Int32(session.plyIndex)
}

@_cdecl("chess_openings_core_shared_playout_release")
public func chessOpeningsCoreSharedPlayoutRelease(_ handle: Int64) {
    SharedPlayoutBridgeStore.shared.remove(handle)
}

private enum SharedPlayoutBridgeOutcome: Int32 {
    case accepted = 1
    case invalidInput = 2
    case illegalMove = 3
    case notUserTurn = 4
    case gameOver = 5
    case invalidHandle = -1

    init(_ outcome: SharedPlayoutSubmitOutcome) {
        switch outcome {
        case .accepted:
            self = .accepted
        case .invalidInput:
            self = .invalidInput
        case .illegalMove:
            self = .illegalMove
        case .notUserTurn:
            self = .notUserTurn
        case .gameOver:
            self = .gameOver
        }
    }
}

private enum SharedPlayoutBridgeStatus: Int32 {
    case waitingForUser = 0
    case engineThinking = 1
    case drawOffered = 2
    case gameOver = 3
    case invalidHandle = -1

    init(_ status: SharedPlayoutStatus) {
        switch status {
        case .waitingForUser:
            self = .waitingForUser
        case .engineThinking:
            self = .engineThinking
        case .drawOffered:
            self = .drawOffered
        case .gameOver:
            self = .gameOver
        }
    }
}

private enum SharedPlayoutBridgeGameOverReason: Int32 {
    case none = 0
    case checkmateWhiteWins = 1
    case checkmateBlackWins = 2
    case stalemate = 3
    case fiftyMoveRule = 4
    case threefoldRepetition = 5
    case insufficientMaterial = 6
    case drawAgreed = 7
    case userResigned = 8
    case engineResignationPending = 9
    case engineResignationAccepted = 10
    case invalidHandle = -1

    init(_ reason: SharedGameOverReason) {
        switch reason {
        case .checkmate(winner: .white):
            self = .checkmateWhiteWins
        case .checkmate(winner: .black):
            self = .checkmateBlackWins
        case .stalemate:
            self = .stalemate
        case .fiftyMoveRule:
            self = .fiftyMoveRule
        case .threefoldRepetition:
            self = .threefoldRepetition
        case .insufficientMaterial:
            self = .insufficientMaterial
        case .drawAgreed:
            self = .drawAgreed
        case .userResigned:
            self = .userResigned
        case .engineResigned(accepted: .none):
            self = .engineResignationPending
        case .engineResigned(accepted: .some(true)):
            self = .engineResignationAccepted
        case .engineResigned(accepted: .some(false)):
            self = .none
        }
    }
}

private enum SharedPlayoutBridgeUserSide: Int32 {
    case white = 0
    case black = 1

    var openingSide: OpeningSide {
        switch self {
        case .white:
            return .white
        case .black:
            return .black
        }
    }
}

private final class SharedPlayoutBridgeStore: @unchecked Sendable {
    static let shared = SharedPlayoutBridgeStore()

    private let lock = NSLock()
    private var nextHandle: Int64 = 1
    private var sessions: [Int64: SharedEnginePlayoutSession] = [:]

    func insert(_ session: SharedEnginePlayoutSession) -> Int64 {
        lock.lock()
        defer { lock.unlock() }
        let handle = nextHandle
        nextHandle += 1
        sessions[handle] = session
        return handle
    }

    func session(for handle: Int64) -> SharedEnginePlayoutSession? {
        lock.lock()
        defer { lock.unlock() }
        return sessions[handle]
    }

    func remove(_ handle: Int64) {
        lock.lock()
        defer { lock.unlock() }
        sessions.removeValue(forKey: handle)
    }
}

private final class SharedPlayoutEngineFactory: @unchecked Sendable {
    static let shared = SharedPlayoutEngineFactory()

    private let lock = NSLock()
    private var executablePath: String?
    private var nnueDirectory: String?

    func configure(executablePath: String?, nnueDirectory: String?) {
        lock.lock()
        defer { lock.unlock() }
        self.executablePath = executablePath?.isEmpty == false ? executablePath : nil
        self.nnueDirectory = nnueDirectory?.isEmpty == false ? nnueDirectory : nil
    }

    func makeEngine() -> SharedEngineServicing {
        lock.lock()
        let path = executablePath
        let nnue = nnueDirectory
        lock.unlock()

        if let path, FileManager.default.isExecutableFile(atPath: path) {
            return SharedUCIProcessEngine(
                executablePath: path,
                nnueDirectory: nnue
            )
        }
        return SharedLegalMoveEngine()
    }
}

private func waitForAsync<T: Sendable>(_ operation: @escaping @Sendable () async -> T) -> T {
    let semaphore = DispatchSemaphore(value: 0)
    let box = AsyncBridgeResultBox<T>()
    Task {
        box.value = await operation()
        semaphore.signal()
    }
    semaphore.wait()
    return box.value!
}

private final class AsyncBridgeResultBox<T: Sendable>: @unchecked Sendable {
    var value: T?
}
