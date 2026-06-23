package com.chessopenings.app

object SharedCoreBridge {
    init {
        System.loadLibrary("ChessOpeningsCoreBridge")
    }

    external fun canPlayKingsPawnOpening(): Int
    external fun canRunSharedDrillSession(): Int

    fun isChessKitAvailable(): Boolean =
        canPlayKingsPawnOpening() == 1

    fun isSharedDrillSessionAvailable(): Boolean =
        canRunSharedDrillSession() == 1
}
