package dev.jigsaw.model

enum class StashSort { MANUAL, COLOR, SHAPE }
/** Stash entries are group representatives; moving a group never splits it. */
data class Workspace(val session: PuzzleSession, val stash: List<Int>, val sort: StashSort = StashSort.MANUAL) {
    fun transfer(piece: Int, toStash: Boolean): Workspace {
        val group = session.groups.first { piece in it.pieces }
        val key = group.pieces.min()
        val remaining = stash.filterNot { it in group.pieces }
        val spec = session.geometry.spec
        val center = Point(
            (group.pieces.minOf { it % spec.columns } + group.pieces.maxOf { it % spec.columns } + 1) / 2f,
            (group.pieces.minOf { it / spec.columns } + group.pieces.maxOf { it / spec.columns } + 1) / 2f)
        val destination = Point(spec.columns / 2f, spec.rows / 2f) - center
        val moved = if (toStash) session else session.move(piece, destination - group.translation)
        return copy(session = moved, stash = if (toStash) remaining + key else remaining)
    }
    fun reorder(key: Int, offset: Int): Workspace {
        val list = stash.toMutableList(); val index = list.indexOf(key)
        if (index < 0) return this
        list.removeAt(index); list.add((index + offset).coerceIn(0, list.size), key)
        return copy(stash = list, sort = StashSort.MANUAL)
    }
    fun sorted(mode: StashSort, colors: Map<Int, Triple<Float, Float, Float>>): Workspace {
        val ordered = when (mode) {
            StashSort.MANUAL -> stash
            StashSort.COLOR -> stash.sortedWith(compareBy<Int> {
                val group = session.groups.first { g -> it in g.pieces }
                group.pieces.map { id -> colors[id]?.let { c -> c.first * .299f + c.second * .587f + c.third * .114f } ?: 0f }.average()
            }.thenBy { colors[it]?.first ?: 0f }.thenBy { it })
            StashSort.SHAPE -> stash.sortedWith(compareBy<Int> { shapeKey(session.geometry, it) }.thenBy { it })
        }
        return copy(stash = ordered, sort = mode)
    }
    companion object {
        fun fresh(spec: PuzzleSpec): Workspace {
            val session = PuzzleSession.scattered(spec)
            return Workspace(session, session.groups.map { it.pieces.min() })
        }
    }
}
/** Flat edges first (corners, then edges), followed by clockwise tab/socket profile. */
fun shapeKey(geometry: PuzzleGeometry, id: Int): String {
    val edges = geometry.pieces[id].edges
    val profile = edges.map { edge ->
        if (edge.curves.size == 1) '0' else {
            val a = edge.curves.first().start; val b = edge.curves.last().end
            val p = edge.curves[1].end; val d = b - a
            if (d.x * (p.y-a.y) - d.y * (p.x-a.x) < 0) '1' else '2'
        }
    }
    return "${4-profile.count { it == '0' }}" + profile.joinToString("")
}

/** Sample the same landscape primitives as the procedural renderer, at 64 points per cell. */
fun originalPieceColor(id: Int, spec: PuzzleSpec): Triple<Float, Float, Float> {
    var red = 0f; var green = 0f; var blue = 0f
    fun boundary(x: Float, start: Float, control: Float, end: Float): Float {
        var lo = 0f; var hi = 1f
        repeat(20) { val t = (lo+hi)/2; val u=1-t; val px=3*u*u*t*.3f+3*u*t*t*.6f+t*t*t; if(px<x) lo=t else hi=t }
        val t=(lo+hi)/2; val u=1-t
        return u*u*u*start+3*u*u*t*control+3*u*t*t*end+t*t*t*(start-.1f)
    }
    for (sy in 0..7) for (sx in 0..7) {
        val x=(id%spec.columns+(sx+.5f)/8)/spec.columns; val y=(id/spec.columns+(sy+.5f)/8)/spec.rows
        var rgb=0xDCE7DE
        val dx=(x-.73f)*spec.columns/spec.rows; val dy=y-.25f
        if(dx*dx+dy*dy < .14f*.14f) rgb=0xF5D99B
        if(y>=boundary(x,.64f,.03f,.85f)) rgb=0x9CB5A1
        if(y>=boundary(x,.85f,.37f,.45f)) rgb=0x668E79
        if(y>=boundary(x,1.08f,.62f,.7f)) rgb=0x365F50
        red+=(rgb shr 16 and 255)/255f; green+=(rgb shr 8 and 255)/255f; blue+=(rgb and 255)/255f
    }
    return Triple(red/64,green/64,blue/64)
}
