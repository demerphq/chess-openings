import ChessKit
import Foundation

public struct SharedSANLinePly: Codable, Equatable, Sendable {
    public let san: String
    public let uci: String

    public init(san: String, uci: String) {
        self.san = san
        self.uci = uci
    }
}

public enum SharedSANLineValidation: Equatable, Sendable {
    case valid([SharedSANLinePly])
    case invalid(ply: Int, san: String)
    case empty
}

public enum SharedSANLine {
    public static func validate(_ text: String) -> SharedSANLineValidation {
        let tokens = tokenize(text)
        guard !tokens.isEmpty else { return .empty }

        var board = Board(position: .standard)
        var plies: [SharedSANLinePly] = []
        for (index, san) in tokens.enumerated() {
            guard let parsed = SANParser.parse(move: san, in: board.position),
                  var applied = board.move(pieceAt: parsed.start, to: parsed.end) else {
                return .invalid(ply: index, san: san)
            }
            if case .promotion = board.state,
               let promotedPiece = parsed.promotedPiece {
                applied = board.completePromotion(of: applied, to: promotedPiece.kind)
            }
            plies.append(
                SharedSANLinePly(
                    san: SANParser.convert(move: applied),
                    uci: EngineLANParser.convert(move: applied)
                )
            )
        }
        return .valid(plies)
    }

    public static func tokenize(_ text: String) -> [String] {
        text
            .split(whereSeparator: \.isWhitespace)
            .compactMap { rawToken in
                let token = String(rawToken)
                let trimmed = token.trimmingCharacters(in: CharacterSet(charactersIn: "."))
                if trimmed.isEmpty || Int(trimmed) != nil {
                    return nil
                }
                return trimmed
            }
    }
}
