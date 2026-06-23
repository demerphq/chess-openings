package com.chessopenings.app

import org.json.JSONArray
import org.json.JSONObject

object SharedCoreBridge {
    init {
        System.loadLibrary("ChessOpeningsCoreBridge")
    }

    external fun canPlayKingsPawnOpening(): Int
    external fun canRunSharedDrillSession(): Int
    external fun createSharedDrillSession(lineJson: String): Long
    external fun submitSharedDrillMove(handle: Long, uci: String): Int
    external fun autoplaySharedDrillNext(handle: Long): Int
    external fun sharedDrillPlyIndex(handle: Long): Int
    external fun sharedDrillStatus(handle: Long): Int
    external fun sharedDrillPositionFen(handle: Long): String?
    external fun undoSharedDrillSession(handle: Long): Int
    external fun resetSharedDrillSession(handle: Long): Int
    external fun restoreSharedDrillSession(handle: Long, plyIndex: Int, userSide: Int): Int
    external fun releaseSharedDrillSession(handle: Long)
    external fun createSharedPlayoutSession(startingFen: String, userSide: Int, engineSkill: Int): Long
    external fun bootstrapSharedPlayoutSession(handle: Long): Int
    external fun submitSharedPlayoutMove(handle: Long, uci: String): Int
    external fun sharedPlayoutPlyIndex(handle: Long): Int
    external fun sharedPlayoutStatus(handle: Long): Int
    external fun sharedPlayoutPositionFen(handle: Long): String?
    external fun sharedPlayoutMovesJson(handle: Long): String?
    external fun restoreSharedPlayoutMoves(handle: Long, movesJson: String): Int
    external fun offerSharedPlayoutDraw(handle: Long): Int
    external fun resignSharedPlayout(handle: Long): Int
    external fun undoSharedPlayoutSession(handle: Long): Int
    external fun releaseSharedPlayoutSession(handle: Long)

    fun isChessKitAvailable(): Boolean =
        canPlayKingsPawnOpening() == 1

    fun isSharedDrillSessionAvailable(): Boolean =
        canRunSharedDrillSession() == 1

    fun createSharedDrillSession(line: LineSummary): Long =
        createSharedDrillSession(line.toCoreJson())

    fun canRunSharedDrillSession(line: LineSummary): Boolean {
        val firstMove = line.plies.firstOrNull()?.uci ?: return false
        val handle = createSharedDrillSession(line)
        if (handle == 0L) return false

        return try {
            submitSharedDrillMove(handle, firstMove) == SHARED_DRILL_ACCEPTED &&
                sharedDrillPlyIndex(handle) == line.plies.size.coerceAtMost(2) &&
                sharedDrillStatus(handle) != SHARED_DRILL_INVALID_HANDLE
        } finally {
            releaseSharedDrillSession(handle)
        }
    }

    private const val SHARED_DRILL_ACCEPTED = 1
    private const val SHARED_DRILL_INVALID_HANDLE = -1
}

data class SharedPlayoutMoveSummary(
    val uci: String,
    val san: String,
    val byUser: Boolean,
    val fenAfterMove: String,
)

fun parseSharedPlayoutMoves(jsonText: String?): List<SharedPlayoutMoveSummary> {
    if (jsonText.isNullOrBlank()) return emptyList()
    return runCatching {
        val moves = JSONArray(jsonText)
        List(moves.length()) { index ->
            val move = moves.getJSONObject(index)
            SharedPlayoutMoveSummary(
                uci = move.optString("uci"),
                san = move.optString("san"),
                byUser = move.optBoolean("byUser"),
                fenAfterMove = move.optString("fenAfterMove"),
            )
        }.filter { it.uci.isNotBlank() && it.san.isNotBlank() }
    }.getOrElse { emptyList() }
}

fun LineSummary.toCoreJson(): String =
    JSONObject()
        .put("name", name)
        .put("source", source.ifBlank { "masters" })
        .put("tags", JSONArray(tags))
        .put(
            "plies",
            JSONArray(
                plies.map { ply ->
                    JSONObject()
                        .put("san", ply.san)
                        .put("uci", ply.uci)
                        .put("annotation", ply.annotation)
                        .put("alternativeSans", JSONArray(ply.alternativeSans))
                },
            ),
        )
        .toString()
