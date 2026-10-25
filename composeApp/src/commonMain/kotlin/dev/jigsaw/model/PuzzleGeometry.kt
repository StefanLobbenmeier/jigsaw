package dev.jigsaw.model

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

data class Point(val x: Float, val y: Float) {
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    operator fun times(scale: Float) = Point(x * scale, y * scale)
    fun distance(other: Point): Float { val d = this - other; return sqrt(d.x*d.x + d.y*d.y) }
}
data class Curve(val start: Point, val control1: Point, val control2: Point, val end: Point) {
    fun reversed() = Curve(end, control2, control1, start)
    fun at(t: Float): Point { val u = 1-t; return start*(u*u*u) + control1*(3*u*u*t) + control2*(3*u*t*t) + end*(t*t*t) }
}
data class Edge(val curves: List<Curve>) {
    fun reversed() = Edge(curves.reversed().map { it.reversed() })
}
data class PieceGeometry(val id: Int, val edges: List<Edge>) {
    val outline get() = edges.flatMap { it.curves }
    fun contains(point: Point): Boolean {
        val polygon = outline.flatMap { curve -> (0 until 12).map { curve.at(it / 12f) } }
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            if ((current.y > point.y) != (previous.y > point.y) &&
                point.x < (previous.x-current.x)*(point.y-current.y)/(previous.y-current.y)+current.x) inside = !inside
            previous = current
        }
        return inside
    }
}

/** Coordinates are in cells. Shared seams are generated once, then traversed backwards. */
class PuzzleGeometry(val spec: PuzzleSpec) {
    val pieces: List<PieceGeometry>
    init {
        val random = Random(spec.seed)
        fun edge(a: Point, b: Point, flat: Boolean): Edge {
            val direction = b-a
            val normal = Point(-direction.y, direction.x)
            fun p(x: Float, y: Float) = a + direction*x + normal*y
            fun line(a: Point, b: Point) = Curve(a, a+(b-a)*(1f/3), a+(b-a)*(2f/3), b)
            if (flat) return Edge(listOf(line(a,b)))
            val center = .45f + random.nextFloat()*.1f
            val width = .09f + random.nextFloat()*.045f
            val depth = (.18f + random.nextFloat()*.07f) * if(random.nextBoolean()) 1 else -1
            return Edge(listOf(
                line(a,p(center-width,0f)),
                Curve(p(center-width,0f),p(center-width*.35f,depth*.15f),p(center-width*1.8f,depth),p(center,depth)),
                Curve(p(center,depth),p(center+width*1.8f,depth),p(center+width*.35f,depth*.15f),p(center+width,0f)),
                line(p(center+width,0f),b)))
        }
        val horizontal = (0..spec.rows).map { y -> (0 until spec.columns).map { x -> edge(Point(x.toFloat(),y.toFloat()), Point(x+1f,y.toFloat()),y==0 || y==spec.rows) } }
        val vertical = (0 until spec.rows).map { y -> (0..spec.columns).map { x -> edge(Point(x.toFloat(),y.toFloat()),Point(x.toFloat(),y+1f),x==0 || x==spec.columns) } }
        pieces = (0 until spec.pieceCount).map { id -> val x=id%spec.columns; val y=id/spec.columns
            PieceGeometry(id,listOf(horizontal[y][x],vertical[y][x+1],horizontal[y+1][x].reversed(),vertical[y][x].reversed())) }
    }
    fun neighbors(a: Int,b: Int): Boolean = abs(a%spec.columns-b%spec.columns)+abs(a/spec.columns-b/spec.columns)==1
}

/** A rigid group has one translation from solved artwork coordinates. Immutable for save adapters. */
data class PieceGroup(val pieces: Set<Int>, val translation: Point)
data class PuzzleSession(val geometry: PuzzleGeometry, val groups: List<PieceGroup>) {
    val progress: Float get() = if (complete) 1f else (geometry.spec.pieceCount-groups.size).toFloat() / (geometry.spec.pieceCount-1).coerceAtLeast(1)
    val complete: Boolean get() = groups.size == 1
    fun move(piece: Int, delta: Point) = copy(groups=groups.map { if(piece in it.pieces) it.copy(translation=it.translation+delta) else it })
    /** Only topologically adjacent pieces with matching solved offsets can merge. Recheck after every merge. */
    fun snap(piece: Int, tolerance: Float = .28f): PuzzleSession {
        require(tolerance >= 0)
        val remaining = groups.toMutableList()
        var active = remaining.first { piece in it.pieces }
        remaining.remove(active)
        while(true) {
            val target = remaining.filter { other -> active.translation.distance(other.translation) <= tolerance && active.pieces.any { a -> other.pieces.any { b -> geometry.neighbors(a,b) } } }
                .minByOrNull { active.translation.distance(it.translation) } ?: break
            active = PieceGroup(active.pieces+target.pieces,target.translation)
            remaining.remove(target)
        }
        return copy(groups=remaining+active)
    }
    fun hitTest(point: Point): Int? = groups.asReversed().firstNotNullOfOrNull { group -> group.pieces.firstOrNull { geometry.pieces[it].contains(point-group.translation) } }
    fun raise(piece: Int): PuzzleSession { val group=groups.first { piece in it.pieces }; return copy(groups=groups.filterNot { it===group }+group) }
    companion object {
        fun scattered(spec: PuzzleSpec): PuzzleSession {
            val random=Random(spec.seed xor 0x134ab)
            return PuzzleSession(PuzzleGeometry(spec),(0 until spec.pieceCount).map { id ->
                val x=id%spec.columns; val y=id/spec.columns
                PieceGroup(setOf(id),Point(random.nextFloat()*(spec.columns+2)-x-1,random.nextFloat()*(spec.rows+2)-y-1)) })
        }
    }
}
