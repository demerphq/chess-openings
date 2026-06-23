import ChessKit
import Foundation

public final class SharedUCIProcessEngine: SharedEngineServicing {
    private let client: SharedUCIProcessClient
    public let supportsAnalysis = true

    public init(executablePath: String, nnueDirectory: String? = nil) {
        client = SharedUCIProcessClient(
            executablePath: executablePath,
            nnueDirectory: nnueDirectory
        )
    }

    public func bestMove(
        at position: Position,
        skill: Int,
        budget: SharedSearchBudget
    ) async -> SharedEngineDecision? {
        await client.bestMove(at: position, skill: skill, budget: budget)
    }

    public func evaluate(
        at position: Position,
        budget: SharedSearchBudget
    ) async -> SharedEngineEvaluation {
        await client.evaluate(at: position, budget: budget)
    }
}

private actor SharedUCIProcessClient {
    private struct BestMoveResult {
        let uci: String?
        let evaluation: SharedEngineEvaluation?
    }

    private let executablePath: String
    private let nnueDirectory: String?

    private var process: Process?
    private var input: FileHandle?
    private var output: FileHandle?
    private var pendingOutput = Data()
    private var configuredSkill: Int?

    init(executablePath: String, nnueDirectory: String?) {
        self.executablePath = executablePath
        self.nnueDirectory = nnueDirectory
    }

    deinit {
        if let data = "quit\n".data(using: .utf8) {
            input?.write(data)
        }
        input?.closeFile()
        output?.closeFile()
        process?.terminate()
    }

    func bestMove(
        at position: Position,
        skill: Int,
        budget: SharedSearchBudget
    ) -> SharedEngineDecision? {
        guard ensureReady(skill: skill) else { return nil }
        send("position fen \(position.fen)")
        let result = runUntilBestMove(budget: budget)
        guard let uci = result.uci, !uci.isEmpty, uci != "(none)" else {
            return nil
        }
        return SharedEngineDecision(
            move: SharedEngineMove(uci: uci),
            evaluation: result.evaluation
        )
    }

    func evaluate(
        at position: Position,
        budget: SharedSearchBudget
    ) -> SharedEngineEvaluation {
        guard ensureReady(skill: 20) else { return .cp(0) }
        send("position fen \(position.fen)")
        return runUntilBestMove(budget: budget).evaluation ?? .cp(0)
    }

    private func ensureReady(skill: Int) -> Bool {
        guard ensureProcessStarted() else { return false }
        if configuredSkill == nil {
            send("uci")
            guard consume(until: { $0 == "uciok" }) else { return false }
            configureNNUEFiles()
        }
        let clamped = max(0, min(20, skill))
        if configuredSkill != clamped {
            send("setoption name Skill Level value \(clamped)")
            configuredSkill = clamped
        }
        send("isready")
        return consume(until: { $0 == "readyok" })
    }

    private func ensureProcessStarted() -> Bool {
        if let process, process.isRunning { return true }

        let stdin = Pipe()
        let stdout = Pipe()
        let task = Process()
        task.executableURL = URL(fileURLWithPath: executablePath)
        task.standardInput = stdin
        task.standardOutput = stdout
        task.standardError = Pipe()

        do {
            try task.run()
        } catch {
            return false
        }

        process = task
        input = stdin.fileHandleForWriting
        output = stdout.fileHandleForReading
        pendingOutput.removeAll(keepingCapacity: true)
        configuredSkill = nil
        return true
    }

    private func configureNNUEFiles() {
        guard let nnueDirectory else { return }
        let mainPath = "\(nnueDirectory)/nn-1111cefa1111.nnue"
        let smallPath = "\(nnueDirectory)/nn-37f18f62d772.nnue"
        let files = FileManager.default

        if files.fileExists(atPath: mainPath) {
            send("setoption name EvalFile value \(mainPath)")
        }
        if files.fileExists(atPath: smallPath) {
            send("setoption name EvalFileSmall value \(smallPath)")
        }
    }

    private func runUntilBestMove(
        budget: SharedSearchBudget
    ) -> BestMoveResult {
        switch budget {
        case .depth(let depth):
            send("go depth \(depth)")
        case .movetimeMs(let ms):
            send("go movetime \(ms)")
        }

        var latestScore: SharedEngineEvaluation?
        while let line = readLine() {
            if let score = parseScore(from: line) {
                latestScore = score
            }
            if line.hasPrefix("bestmove ") {
                let parts = line.split(separator: " ")
                return BestMoveResult(
                    uci: parts.count > 1 ? String(parts[1]) : nil,
                    evaluation: latestScore
                )
            }
        }
        return BestMoveResult(uci: nil, evaluation: latestScore)
    }

    private func consume(until match: (String) -> Bool) -> Bool {
        while let line = readLine() {
            if match(line) { return true }
        }
        return false
    }

    private func send(_ command: String) {
        guard let data = "\(command)\n".data(using: .utf8) else { return }
        input?.write(data)
    }

    private func readLine() -> String? {
        while true {
            if let newlineIndex = pendingOutput.firstIndex(of: 10) {
                let lineData = pendingOutput[..<newlineIndex]
                pendingOutput.removeSubrange(...newlineIndex)
                return String(data: lineData, encoding: .utf8)?
                    .trimmingCharacters(in: .whitespacesAndNewlines)
            }

            guard let chunk = output?.readData(ofLength: 1),
                  !chunk.isEmpty else {
                return nil
            }
            pendingOutput.append(chunk)
        }
    }

    private func parseScore(from line: String) -> SharedEngineEvaluation? {
        guard line.hasPrefix("info ") else { return nil }
        let parts = line.split(separator: " ")
        guard let scoreIndex = parts.firstIndex(of: "score"),
              parts.indices.contains(scoreIndex + 2) else {
            return nil
        }

        switch parts[scoreIndex + 1] {
        case "cp":
            return Int(parts[scoreIndex + 2]).map { .cp($0) }
        case "mate":
            return Int(parts[scoreIndex + 2]).map { .mate(in: $0) }
        default:
            return nil
        }
    }
}
