package dev.jigsaw.save

import dev.jigsaw.model.*
import kotlin.io.encoding.Base64

const val MAX_SAVED_IMAGE = 2 * 1024 * 1024
const val MAX_SAVE_CHARS = 3_000_000
expect fun readSave(): String?
expect fun writeSave(value: String)

data class SavedGame(val workspace: Workspace, val imageId: String, val imageName: String, val imageBytes: ByteArray? = null)

/** A bounded, versioned text envelope. Geometry is regenerated from the exact seeded spec. */
object SessionSave {
    fun encode(game: SavedGame): String {
        require((game.imageBytes?.size ?: 0) <= MAX_SAVED_IMAGE) { "Image exceeds the 2 MiB save limit. Choose a smaller image to save progress." }
        val w = game.workspace; val s = w.session.geometry.spec
        return listOf("JIGSAW:1", "${s.columns},${s.rows},${s.seed},${s.cutVersion}", Base64.encode(game.imageId.encodeToByteArray()), Base64.encode(game.imageName.encodeToByteArray()), game.imageBytes?.let { Base64.encode(it) } ?: "-", w.sort.name,
            w.stash.joinToString(","), w.session.groups.joinToString(";") { "${it.translation.x},${it.translation.y}:" + it.pieces.sorted().joinToString(",") }).joinToString("\n").also { require(it.length <= MAX_SAVE_CHARS) }
    }
    fun decode(text: String): SavedGame {
        require(text.length <= MAX_SAVE_CHARS) { "Saved game is too large." }
        val lines = text.split('\n'); require(lines.size == 8 && lines[0] == "JIGSAW:1") { "Unsupported or damaged save version." }
        val specParts = lines[1].split(',').map { it.toInt() }; require(specParts.size in 3..4)
        require(specParts[0] in 1..1000 && specParts[1] in 1..1000 && specParts[0].toLong()*specParts[1] in 2L..1000L)
        val spec = PuzzleSpec(specParts[0], specParts[1], specParts[2], specParts.getOrElse(3) { 1 }); val geometry = PuzzleGeometry(spec)
        val groups = lines[7].split(';').map { entry ->
            val parts = entry.split(':'); require(parts.size == 2)
            val xy = parts[0].split(',').map { it.toFloat() }; require(xy.size == 2 && xy.all { it.isFinite() && kotlin.math.abs(it) <= 1000 })
            val ids = parts[1].split(',').map { it.toInt() }; require(ids.isNotEmpty() && ids.distinct().size == ids.size)
            val connected = mutableSetOf(ids.first())
            while (true) { val next = ids.filter { it !in connected && connected.any { a -> geometry.neighbors(a,it) } }; if (next.isEmpty()) break; connected.addAll(next) }
            require(connected.size == ids.size) { "Disconnected saved group." }
            PieceGroup(ids.toSet(), Point(xy[0],xy[1]))
        }
        val ids = groups.flatMap { it.pieces }; require(ids.sorted() == (0 until spec.pieceCount).toList()) { "Missing or duplicate pieces." }
        val stash = if (lines[6].isEmpty()) emptyList() else lines[6].split(',').map { it.toInt() }
        require(stash.distinct().size == stash.size && stash.all { key -> groups.any { it.pieces.min() == key } })
        val bytes = if (lines[4] == "-") null else Base64.decode(lines[4]); require((bytes?.size ?: 0) <= MAX_SAVED_IMAGE)
        val id = Base64.decode(lines[2]).decodeToString(); require(id in listOf("original", "starry-night", "great-wave") || (id.startsWith("upload-") && bytes != null))
        return SavedGame(Workspace(PuzzleSession(geometry,groups),stash,StashSort.valueOf(lines[5])), id, Base64.decode(lines[3]).decodeToString(), bytes)
    }
}
