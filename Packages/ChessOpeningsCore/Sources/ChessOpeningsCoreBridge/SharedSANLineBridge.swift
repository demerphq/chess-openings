import ChessOpeningsCore
import Foundation

private struct SharedSANLineBridgeResult: Encodable {
    let ok: Bool
    let plies: [SharedSANLinePly]?
    let error: String?
    let ply: Int?
    let san: String?
}

@_cdecl("chess_openings_core_shared_san_line_json")
public func chessOpeningsCoreSharedSANLineJSON(
    _ sanText: UnsafePointer<CChar>?,
    _ buffer: UnsafeMutablePointer<CChar>?,
    _ capacity: Int32
) -> Int32 {
    guard let sanText else { return -1 }

    let result: SharedSANLineBridgeResult
    switch SharedSANLine.validate(String(cString: sanText)) {
    case .valid(let plies):
        result = SharedSANLineBridgeResult(
            ok: true,
            plies: plies,
            error: nil,
            ply: nil,
            san: nil
        )
    case .invalid(let ply, let san):
        result = SharedSANLineBridgeResult(
            ok: false,
            plies: nil,
            error: "illegal",
            ply: ply,
            san: san
        )
    case .empty:
        result = SharedSANLineBridgeResult(
            ok: false,
            plies: nil,
            error: "empty",
            ply: nil,
            san: nil
        )
    }

    guard let data = try? JSONEncoder().encode(result),
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
