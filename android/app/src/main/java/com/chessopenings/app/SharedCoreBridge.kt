package com.chessopenings.app

object SharedCoreBridge {
    init {
        System.loadLibrary("ChessOpeningsCoreBridge")
    }

    external fun canPlayKingsPawnOpening(): Int

    fun isChessKitAvailable(): Boolean =
        canPlayKingsPawnOpening() == 1
}
