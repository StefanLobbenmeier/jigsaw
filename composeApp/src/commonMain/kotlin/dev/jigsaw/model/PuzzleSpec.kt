package dev.jigsaw.model

/** Stable input for the deterministic geometry engine. No platform types belong here. */
data class PuzzleSpec(val columns: Int, val rows: Int, val seed: Int = 42) {
    init { require(columns > 0 && rows > 0) }
    val pieceCount: Int get() = columns * rows
}

val starterPuzzles = listOf(PuzzleSpec(6, 4), PuzzleSpec(10, 6), PuzzleSpec(15, 10))
