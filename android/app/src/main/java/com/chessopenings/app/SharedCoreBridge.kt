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
    external fun releaseSharedDrillSession(handle: Long)

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
