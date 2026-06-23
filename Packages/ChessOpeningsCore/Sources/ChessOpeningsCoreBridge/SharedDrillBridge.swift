import ChessOpeningsCore
import Foundation

@_cdecl("chess_openings_core_shared_drill_create")
public func chessOpeningsCoreSharedDrillCreate(
    _ lineJSON: UnsafePointer<CChar>?
) -> Int64 {
    guard let lineJSON else { return 0 }
    let json = String(cString: lineJSON)
    guard let data = json.data(using: .utf8),
          let line = try? JSONDecoder().decode(Line.self, from: data) else {
        return 0
    }
    return SharedDrillBridgeStore.shared.insert(SharedDrillSession(line: line))
}

@_cdecl("chess_openings_core_shared_drill_submit")
public func chessOpeningsCoreSharedDrillSubmit(
    _ handle: Int64,
    _ uci: UnsafePointer<CChar>?
) -> Int32 {
    guard let session = SharedDrillBridgeStore.shared.session(for: handle),
          let uci else {
        return SharedDrillBridgeOutcome.invalidHandle.rawValue
    }

    switch session.submit(uci: String(cString: uci)) {
    case .accepted:
        return SharedDrillBridgeOutcome.accepted.rawValue
    case .incorrect:
        return SharedDrillBridgeOutcome.incorrect.rawValue
    case .invalidInput:
        return SharedDrillBridgeOutcome.invalidInput.rawValue
    case .invalidBookMove:
        return SharedDrillBridgeOutcome.invalidBookMove.rawValue
    case .lineComplete:
        return SharedDrillBridgeOutcome.lineComplete.rawValue
    }
}

@_cdecl("chess_openings_core_shared_drill_autoplay_next")
public func chessOpeningsCoreSharedDrillAutoplayNext(_ handle: Int64) -> Int32 {
    guard let session = SharedDrillBridgeStore.shared.session(for: handle) else {
        return SharedDrillBridgeOutcome.invalidHandle.rawValue
    }
    guard session.autoplayNextBookPly() != nil else {
        return session.status == .lineComplete
            ? SharedDrillBridgeOutcome.lineComplete.rawValue
            : SharedDrillBridgeOutcome.invalidBookMove.rawValue
    }
    return SharedDrillBridgeOutcome.accepted.rawValue
}

@_cdecl("chess_openings_core_shared_drill_ply_index")
public func chessOpeningsCoreSharedDrillPlyIndex(_ handle: Int64) -> Int32 {
    guard let session = SharedDrillBridgeStore.shared.session(for: handle) else {
        return -1
    }
    return Int32(session.plyIndex)
}

@_cdecl("chess_openings_core_shared_drill_status")
public func chessOpeningsCoreSharedDrillStatus(_ handle: Int64) -> Int32 {
    guard let session = SharedDrillBridgeStore.shared.session(for: handle) else {
        return SharedDrillBridgeStatus.invalidHandle.rawValue
    }
    return SharedDrillBridgeStatus(session.status).rawValue
}

@_cdecl("chess_openings_core_shared_drill_position_fen")
public func chessOpeningsCoreSharedDrillPositionFEN(
    _ handle: Int64,
    _ buffer: UnsafeMutablePointer<CChar>?,
    _ capacity: Int32
) -> Int32 {
    guard let session = SharedDrillBridgeStore.shared.session(for: handle) else {
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

@_cdecl("chess_openings_core_shared_drill_reset")
public func chessOpeningsCoreSharedDrillReset(_ handle: Int64) -> Int32 {
    guard let session = SharedDrillBridgeStore.shared.session(for: handle) else {
        return 0
    }
    session.reset()
    return 1
}

@_cdecl("chess_openings_core_shared_drill_restore")
public func chessOpeningsCoreSharedDrillRestore(
    _ handle: Int64,
    _ plyIndex: Int32,
    _ userSide: Int32
) -> Int32 {
    guard let session = SharedDrillBridgeStore.shared.session(for: handle),
          let side = SharedDrillBridgeUserSide(rawValue: userSide)?.openingSide else {
        return -1
    }
    session.restore(plyIndex: Int(plyIndex), userSide: side)
    return Int32(session.plyIndex)
}

@_cdecl("chess_openings_core_shared_drill_undo")
public func chessOpeningsCoreSharedDrillUndo(_ handle: Int64) -> Int32 {
    guard let session = SharedDrillBridgeStore.shared.session(for: handle) else {
        return 0
    }
    session.undo()
    return Int32(session.plyIndex)
}

@_cdecl("chess_openings_core_shared_drill_release")
public func chessOpeningsCoreSharedDrillRelease(_ handle: Int64) {
    SharedDrillBridgeStore.shared.remove(handle)
}

private enum SharedDrillBridgeOutcome: Int32 {
    case accepted = 1
    case incorrect = 2
    case invalidInput = 3
    case invalidBookMove = 4
    case lineComplete = 5
    case invalidHandle = -1
}

private enum SharedDrillBridgeStatus: Int32 {
    case waitingForUser = 0
    case mistake = 1
    case lineComplete = 2
    case invalidHandle = -1

    init(_ status: SharedDrillStatus) {
        switch status {
        case .waitingForUser:
            self = .waitingForUser
        case .mistake:
            self = .mistake
        case .lineComplete:
            self = .lineComplete
        }
    }
}

private enum SharedDrillBridgeUserSide: Int32 {
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

private final class SharedDrillBridgeStore: @unchecked Sendable {
    static let shared = SharedDrillBridgeStore()

    private let lock = NSLock()
    private var nextHandle: Int64 = 1
    private var sessions: [Int64: SharedDrillSession] = [:]

    func insert(_ session: SharedDrillSession) -> Int64 {
        lock.lock()
        defer { lock.unlock() }
        let handle = nextHandle
        nextHandle += 1
        sessions[handle] = session
        return handle
    }

    func session(for handle: Int64) -> SharedDrillSession? {
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
