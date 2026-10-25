package dev.jigsaw.model

import dev.jigsaw.save.*
import kotlin.test.*

class WorkspaceTest {
    @Test fun roundTripRetainsGroupsOrderAndSpec() {
        val spec=PuzzleSpec(3,2,891)
        val session=PuzzleSession(PuzzleGeometry(spec), listOf(PieceGroup(setOf(0,1),Point(2f,-3f)))+(2..5).map { PieceGroup(setOf(it),Point(it.toFloat(),0f)) })
        val game=SavedGame(Workspace(session,listOf(4,0,5),StashSort.SHAPE),"original","Quiet hills")
        val restored=SessionSave.decode(SessionSave.encode(game))
        assertEquals(spec,restored.workspace.session.geometry.spec)
        assertEquals(session.groups,restored.workspace.session.groups)
        assertEquals(game.workspace.stash,restored.workspace.stash)
        assertEquals(StashSort.SHAPE,restored.workspace.sort)
    }
    @Test fun rejectsCorruptionAndUnknownVersions() {
        val raw=SessionSave.encode(SavedGame(Workspace.fresh(PuzzleSpec(2,2)),"original","x"))
        assertFails { SessionSave.decode(raw.replace("JIGSAW:1","JIGSAW:9")) }
        val lines=raw.split('\n').toMutableList()
        lines[7]="NaN,0:0;0,0:1;0,0:2;0,0:3"; assertFails { SessionSave.decode(lines.joinToString("\n")) }
        lines[7]="0,0:0;0,0:0;0,0:2;0,0:3"; assertFails { SessionSave.decode(lines.joinToString("\n")) }
        lines[7]="0,0:0,3;0,0:1;0,0:2"; assertFails { SessionSave.decode(lines.joinToString("\n")) }
        lines[7]=raw.split('\n')[7]; lines[6]="0,0"; assertFails { SessionSave.decode(lines.joinToString("\n")) }
    }
    @Test fun transferAndManualOrderPreserveAllPiecesAndGroups() {
        val initial=Workspace.fresh(PuzzleSpec(2,2))
        val merged=initial.session.copy(groups=listOf(PieceGroup(setOf(0,1),Point(4f,3f)),PieceGroup(setOf(2),Point(0f,0f)),PieceGroup(setOf(3),Point(0f,0f))))
        val w=Workspace(merged,listOf(0,2,3)).transfer(1,false)
        assertEquals(listOf(2,3),w.stash)
        assertEquals(setOf(0,1),w.session.groups.first().pieces)
        assertEquals(Point(0f,.5f),w.session.groups.first().translation)
        val back=w.transfer(1,true).reorder(0,-2)
        assertEquals(listOf(0,2,3),back.stash)
        assertEquals((0..3).toList(),back.session.groups.flatMap { it.pieces }.sorted())
    }
    @Test fun sortingUsesColorsAndCornerProfiles() {
        val w=Workspace.fresh(PuzzleSpec(3,3))
        val colors=(0..8).associateWith { Triple((8-it)/8f,0f,0f) }
        assertEquals((8 downTo 0).toList(),w.sorted(StashSort.COLOR,colors).stash)
        val shapes=w.sorted(StashSort.SHAPE,colors).stash
        assertEquals(setOf(0,2,6,8),shapes.take(4).toSet())
        assertEquals(4,shapes.last())
        assertEquals(w.session.groups,w.sorted(StashSort.SHAPE,colors).session.groups)
    }
    @Test fun uploadedBytesAndLimits() {
        val bytes=byteArrayOf(1,2,3)
        val game=SavedGame(Workspace.fresh(PuzzleSpec(2,2)),"upload-unique","a\nb",bytes)
        val restored=SessionSave.decode(SessionSave.encode(game))
        assertContentEquals(bytes,restored.imageBytes); assertEquals(game.imageName,restored.imageName)
        assertFails { SessionSave.encode(game.copy(imageBytes=ByteArray(MAX_SAVED_IMAGE+1))) }
    }
}
