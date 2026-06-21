import Foundation

public struct SeedCatalogue: Codable, Equatable, Sendable {
    public let version: Int
    public let openings: [Opening]

    public init(version: Int, openings: [Opening]) {
        self.version = version
        self.openings = openings
    }

    public static func decode(from data: Data, using decoder: JSONDecoder = JSONDecoder()) throws -> SeedCatalogue {
        try decoder.decode(SeedCatalogue.self, from: data)
    }

    public static func decode(from json: String, using decoder: JSONDecoder = JSONDecoder()) throws -> SeedCatalogue {
        try decode(from: Data(json.utf8), using: decoder)
    }

    public var openingSummaries: [OpeningSummary] {
        openings.map(\.summary)
    }

    public func opening(named name: String) -> Opening? {
        openings.first { $0.name == name }
    }

    public func openings(for side: OpeningSide) -> [Opening] {
        openings.filter { $0.side == side }
    }
}

public struct Opening: Codable, Equatable, Sendable {
    public let name: String
    public let eco: String?
    public let side: OpeningSide
    public let rootFen: String
    public let description: String?
    public let isSeed: Bool
    public let lines: [Line]

    public init(
        name: String,
        eco: String?,
        side: OpeningSide,
        rootFen: String,
        description: String?,
        isSeed: Bool,
        lines: [Line]
    ) {
        self.name = name
        self.eco = eco
        self.side = side
        self.rootFen = rootFen
        self.description = description
        self.isSeed = isSeed
        self.lines = lines
    }

    public var summary: OpeningSummary {
        OpeningSummary(
            name: name,
            eco: eco,
            side: side,
            description: description,
            lineCount: lines.count,
            plyCount: lines.reduce(0) { $0 + $1.plies.count }
        )
    }

    public var lineSummaries: [LineSummary] {
        lines.map(\.summary)
    }
}

public struct Line: Codable, Equatable, Sendable {
    public let name: String
    public let source: LineSource
    public let tags: [String]
    public let plies: [BookPly]

    private enum CodingKeys: String, CodingKey {
        case name, source, tags, plies
    }

    public init(name: String, source: LineSource, tags: [String], plies: [BookPly]) {
        self.name = name
        self.source = source
        self.tags = tags
        self.plies = plies
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        name = try container.decode(String.self, forKey: .name)
        source = try container.decodeIfPresent(LineSource.self, forKey: .source) ?? .masters
        tags = try container.decodeIfPresent([String].self, forKey: .tags) ?? []
        plies = try container.decode([BookPly].self, forKey: .plies)
    }

    public var summary: LineSummary {
        LineSummary(
            name: name,
            source: source,
            tags: tags,
            plyCount: plies.count,
            firstMoveSAN: plies.first?.san,
            moveText: moveText
        )
    }

    public var moveText: String {
        plies.enumerated().map { index, ply in
            if index.isMultiple(of: 2) {
                return "\(index / 2 + 1). \(ply.san)"
            }
            return ply.san
        }.joined(separator: " ")
    }
}

public struct BookPly: Codable, Equatable, Sendable {
    public let san: String
    public let uci: String
    public let annotation: String?
    public let alternativeSans: [String]

    public init(san: String, uci: String, annotation: String?, alternativeSans: [String]) {
        self.san = san
        self.uci = uci
        self.annotation = annotation
        self.alternativeSans = alternativeSans
    }
}

public enum OpeningSide: String, Codable, Equatable, Sendable, CaseIterable {
    case white
    case black
}

public enum LineSource: String, Codable, Equatable, Sendable, CaseIterable {
    case masters
    case open
}

public struct OpeningSummary: Codable, Equatable, Sendable {
    public let name: String
    public let eco: String?
    public let side: OpeningSide
    public let description: String?
    public let lineCount: Int
    public let plyCount: Int

    public init(
        name: String,
        eco: String?,
        side: OpeningSide,
        description: String?,
        lineCount: Int,
        plyCount: Int
    ) {
        self.name = name
        self.eco = eco
        self.side = side
        self.description = description
        self.lineCount = lineCount
        self.plyCount = plyCount
    }
}

public struct LineSummary: Codable, Equatable, Sendable {
    public let name: String
    public let source: LineSource
    public let tags: [String]
    public let plyCount: Int
    public let firstMoveSAN: String?
    public let moveText: String

    public init(
        name: String,
        source: LineSource,
        tags: [String],
        plyCount: Int,
        firstMoveSAN: String?,
        moveText: String
    ) {
        self.name = name
        self.source = source
        self.tags = tags
        self.plyCount = plyCount
        self.firstMoveSAN = firstMoveSAN
        self.moveText = moveText
    }
}
