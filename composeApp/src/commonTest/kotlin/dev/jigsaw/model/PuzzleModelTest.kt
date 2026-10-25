package dev.jigsaw.model

import kotlin.test.*

class PuzzleModelTest {
    @Test fun complementaryDeterministicVariedSeams() {
        val spec=PuzzleSpec(4,3,51)
        val a=PuzzleGeometry(spec)
        assertEquals(a.pieces,PuzzleGeometry(spec).pieces)
        assertNotEquals(a.pieces,PuzzleGeometry(spec.copy(seed=52)).pieces)
        for(y in 0 until spec.rows) for(x in 0 until spec.columns) {
            val piece=a.pieces[y*spec.columns+x]
            if(x+1<spec.columns) assertEquals(piece.edges[1],a.pieces[piece.id+1].edges[3].reversed())
            if(y+1<spec.rows) assertEquals(piece.edges[2],a.pieces[piece.id+spec.columns].edges[0].reversed())
            if(y==0) assertTrue(piece.edges[0].curves.all { it.start.y==0f && it.control1.y==0f && it.control2.y==0f && it.end.y==0f })
            assertTrue(piece.contains(Point(x+.5f,y+.5f)))
            assertFalse(piece.contains(Point(x+2f,y+2f)))
        }
        assertTrue(a.pieces.flatMap { it.edges }.distinct().size>spec.pieceCount)
    }
    private fun session(vararg groups: PieceGroup) = PuzzleSession(PuzzleGeometry(PuzzleSpec(3,2)),groups.toList())
    @Test fun rigidMoveAndRecursiveMerge() {
        val a=PieceGroup(setOf(0,1),Point(2f,3f))
        val b=PieceGroup(setOf(2),Point(2.2f,3f))
        val c=PieceGroup(setOf(5),Point(2.4f,3f))
        val original=session(a,b,c)
        val moved=original.move(0,Point(.1f,-.1f))
        assertEquals(Point(2.1f,2.9f),moved.groups[0].translation)
        assertEquals(setOf(0,1),moved.groups[0].pieces)
        assertEquals(b,moved.groups[1])
        val merged=original.snap(0,.25f)
        assertEquals(1,merged.groups.size)
        assertEquals(setOf(0,1,2,5),merged.groups.single().pieces)
        assertEquals(c.translation,merged.groups.single().translation)
        assertEquals(a,original.groups[0])
    }
    @Test fun rejectsWrongNeighborsAndOutsideTolerance() {
        assertEquals(2,session(PieceGroup(setOf(0),Point(0f,0f)),PieceGroup(setOf(2),Point(0f,0f))).snap(0).groups.size)
        assertEquals(2,session(PieceGroup(setOf(2),Point(0f,0f)),PieceGroup(setOf(3),Point(0f,0f))).snap(2).groups.size)
        assertEquals(2,session(PieceGroup(setOf(0),Point(0f,0f)),PieceGroup(setOf(1),Point(.3f,0f))).snap(0,.28f).groups.size)
        assertEquals(1,session(PieceGroup(setOf(0),Point(0f,0f)),PieceGroup(setOf(1),Point(.28f,0f))).snap(0,.28f).groups.size)
    }
    @Test fun singlePieceIsComplete() {
        val session=PuzzleSession.scattered(PuzzleSpec(1,1))
        assertTrue(session.complete)
        assertEquals(1f,session.progress)
    }
    @Test fun completeProgressAndTranslatedHitTest() {
        val geometry=PuzzleGeometry(PuzzleSpec(2,1))
        val s=PuzzleSession(geometry,listOf(PieceGroup(setOf(0),Point(3f,4f)),PieceGroup(setOf(1),Point(3.1f,4f))))
        assertEquals(0f,s.progress)
        assertEquals(0,s.hitTest(Point(3.5f,4.5f)))
        val done=s.snap(0)
        assertTrue(done.complete)
        assertEquals(1f,done.progress)
        assertEquals(1,done.move(1,Point(5f,1f)).groups.size)
    }
}
