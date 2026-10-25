package dev.jigsaw.ui

import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import dev.jigsaw.artwork.PuzzleArtwork
import dev.jigsaw.model.*
import kotlin.math.abs
import kotlin.math.floor

private val Outline = Color(0xFF243E36)
private data class Drag(val key: Int, val group: PieceGroup, val anchor: Point, val position: Offset, val fromTray: Boolean)
private data class Camera(val scale: Float, val offset: Offset) {
    fun world(p: Offset) = Point((p.x-offset.x)/scale, (p.y-offset.y)/scale)
}

/** One input surface owns a gesture from press to release, including cross-tray drags.
 * Pointer movement updates only the preview; the persistent model is committed on drop.
 */
@Composable
internal fun PlaySurface(workspace: Workspace, artwork: PuzzleArtwork, edgesOnly: Boolean, fitRequest: Int,
                         update: (Workspace) -> Unit, modifier: Modifier) {
    val current by rememberUpdatedState(workspace)
    val commit by rememberUpdatedState(update)
    val geometry = workspace.session.geometry
    val spec = geometry.spec
    val paths = remember(geometry) { geometry.pieces.map { it.path() } }
    val groups = remember(workspace.session) { workspace.session.groups.associateBy { it.pieces.min() } }
    val stashKeys = remember(workspace.stash) { workspace.stash.toSet() }
    val board = remember(workspace) { workspace.session.copy(groups = workspace.session.groups.filter { it.pieces.min() !in stashKeys }) }
    val tray = remember(workspace, edgesOnly) { workspace.stash.filter { key ->
        !edgesOnly || groups.getValue(key).pieces.any { id -> id % spec.columns == 0 || id % spec.columns == spec.columns-1 || id / spec.columns == 0 || id / spec.columns == spec.rows-1 }
    } }
    val liveBoard by rememberUpdatedState(board)
    val liveTray by rememberUpdatedState(tray)
    val density = LocalDensity.current.density
    val slot = 76f * density
    val trayHeight = slot * 2 + 16f * density
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    var camera by remember(geometry) { mutableStateOf(Camera(1f, Offset.Zero)) }
    var fittedScale by remember(geometry) { mutableStateOf(1f) }
    var trayScroll by remember(geometry) { mutableStateOf(0f) }
    var selected by remember(geometry) { mutableStateOf<Int?>(null) }
    var drag by remember(geometry) { mutableStateOf<Drag?>(null) }
    val boardHeight = (dimensions.height - trayHeight).coerceAtLeast(1f)
    fun maxScroll() = ((liveTray.size + 1) / 2 * slot - dimensions.width).coerceAtLeast(0f)
    LaunchedEffect(dimensions, geometry, fitRequest) {
        val pieces = liveBoard.groups.flatMap { g -> g.pieces.map { id -> Point(id % spec.columns + g.translation.x, id / spec.columns + g.translation.y) } }
        val minX = minOf(0f, pieces.minOfOrNull { it.x } ?: 0f) - .4f
        val minY = minOf(0f, pieces.minOfOrNull { it.y } ?: 0f) - .4f
        val maxX = maxOf(spec.columns.toFloat(), pieces.maxOfOrNull { it.x + 1 } ?: 0f) + .4f
        val maxY = maxOf(spec.rows.toFloat(), pieces.maxOfOrNull { it.y + 1 } ?: 0f) + .4f
        val scale = minOf(dimensions.width / (maxX-minX), boardHeight / (maxY-minY)).coerceAtLeast(.01f)
        fittedScale = scale
        camera = Camera(scale, Offset((dimensions.width-(maxX-minX)*scale)/2-minX*scale, (boardHeight-(maxY-minY)*scale)/2-minY*scale))
    }
    LaunchedEffect(tray.size, dimensions) { trayScroll = trayScroll.coerceIn(0f, maxScroll()) }
    fun selectNext(): Boolean {
        val keys = current.session.groups.map { it.pieces.min() }
        selected = keys[(keys.indexOf(selected)+1) % keys.size]
        return true
    }
    fun transferSelected(): Boolean {
        val id = selected ?: return false
        val key = current.session.groups.first { id in it.pieces }.pieces.min()
        commit(current.transfer(id, key !in current.stash))
        selected = key
        return true
    }
    fun moveSelected(delta: Point): Boolean {
        val id = selected ?: return false
        if (id in current.stash) return false
        commit(current.copy(session = current.session.move(id,delta)))
        return true
    }
    Canvas(modifier.semantics {
        contentDescription = "Puzzle board and visual piece tray"
        stateDescription = selected?.let { "Selected group ${it+1}" } ?: "No group selected"
        customActions = listOf(
            CustomAccessibilityAction("Select next group", ::selectNext),
            CustomAccessibilityAction("Transfer selected group between board and tray", ::transferSelected),
            CustomAccessibilityAction("Move left") { moveSelected(Point(-.2f,0f)) },
            CustomAccessibilityAction("Move right") { moveSelected(Point(.2f,0f)) },
            CustomAccessibilityAction("Move up") { moveSelected(Point(0f,-.2f)) },
            CustomAccessibilityAction("Move down") { moveSelected(Point(0f,.2f)) })
    }.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.N -> selectNext()
            Key.Enter -> transferSelected()
            Key.DirectionLeft -> moveSelected(Point(-.2f,0f))
            Key.DirectionRight -> moveSelected(Point(.2f,0f))
            Key.DirectionUp -> moveSelected(Point(0f,-.2f))
            Key.DirectionDown -> moveSelected(Point(0f,.2f))
            Key.J -> {
                val id = selected
                if (id != null && id !in current.stash) {
                    val visible = liveBoard.snap(id)
                    commit(current.copy(session = current.session.copy(groups = current.session.groups.filter { it.pieces.min() in current.stash } + visible.groups)))
                    selected = visible.groups.first { id in it.pieces }.pieces.min()
                    true
                } else false
            }
            else -> false
        }
    }.focusable().clipToBounds().onSizeChanged { dimensions = it }
        .pointerInput(geometry, dimensions, density) {
            awaitEachGesture {
                // A hovering mouse can coexist with a new touch on hybrid devices.
                // awaitFirstDown requires *all* changes to be new presses, so pick
                // the newly pressed pointer explicitly instead.
                var press: PointerInputChange? = null
                while (press == null) {
                    val event = awaitPointerEvent()
                    press = event.changes.firstOrNull {
                        it.pressed && !it.previousPressed && (it.type != PointerType.Mouse || event.buttons.isPrimaryPressed)
                    }
                }
                val down = press
                down.consume()
                val fromTray = down.position.y >= boardHeight
                val index = floor((down.position.x+trayScroll)/slot).toInt()*2 + floor((down.position.y-boardHeight-8*density)/slot).toInt()
                val key = if (fromTray) liveTray.getOrNull(index) else liveBoard.hitTest(camera.world(down.position))
                val group = key?.let { id -> current.session.groups.firstOrNull { id in it.pieces } }
                val anchor = if (group == null) Point(0f,0f) else if (fromTray) {
                    Point((group.pieces.minOf { it % spec.columns }+group.pieces.maxOf { it % spec.columns }+1)/2f,
                        (group.pieces.minOf { it / spec.columns }+group.pieces.maxOf { it / spec.columns }+1)/2f)
                } else camera.world(down.position)-group.translation
                var mode = if (fromTray) 0 else 1 // Tray resolves horizontal browsing vs vertical pickup.
                var transforming = false
                var released = false
                try {
                    if (!fromTray && group != null) drag = Drag(key,group,anchor,down.position,false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                        if (pressed >= 2 && !fromTray) {
                            transforming = true
                            drag = null // A second finger cancels the uncommitted piece pickup.
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val ratio = event.calculateZoom()
                            val scale = (camera.scale*ratio).coerceIn(fittedScale*.5f, fittedScale*12f)
                            camera = Camera(scale, centroid + event.calculatePan() - (centroid-camera.offset)*(scale/camera.scale))
                        } else if (!transforming) {
                            val distance = change.position-down.position
                            if (mode == 0 && distance.getDistance() > viewConfiguration.touchSlop) {
                                mode = if (abs(distance.x) > abs(distance.y)) 2 else 1
                                if (mode == 1 && group != null) drag = Drag(key,group,anchor,change.position,true)
                            }
                            if (mode == 2) trayScroll = (trayScroll-change.positionChange().x).coerceIn(0f,maxScroll())
                            else if (mode == 1) {
                                if (drag != null) drag = drag!!.copy(position = change.position)
                                else if (!fromTray) camera = camera.copy(offset = camera.offset+change.positionChange())
                            }
                        }
                        event.changes.forEach { it.consume() }
                        if (pressed == 0) released = true
                    } while (event.changes.any { it.pressed })
                    if (released) drag?.let { d ->
                        if (d.position.x !in 0f..dimensions.width.toFloat() || d.position.y !in 0f..dimensions.height.toFloat()) return@let
                        val w = current
                        if (d.position.y >= boardHeight) {
                            if (!d.fromTray) commit(w.transfer(d.key,true))
                            else {
                                val targetIndex = (floor((d.position.x+trayScroll)/slot).toInt()*2 + floor((d.position.y-boardHeight-8*density)/slot).toInt()).coerceIn(0,(liveTray.size-1).coerceAtLeast(0))
                                liveTray.getOrNull(targetIndex)?.let { target ->
                                    commit(w.reorder(d.key,w.stash.indexOf(target)-w.stash.indexOf(d.key)))
                                }
                            }
                        } else if (d.position.x in 0f..dimensions.width.toFloat() && d.position.y >= 0f) {
                            val translation = camera.world(d.position)-d.anchor
                            val moved = w.session.move(d.key,translation-d.group.translation).raise(d.key)
                            val remaining = w.stash.filterNot { it in d.group.pieces }
                            val hidden = remaining.toSet()
                            val visible = moved.copy(groups = moved.groups.filter { it.pieces.min() !in hidden }).snap(d.key)
                            commit(w.copy(session = moved.copy(groups = moved.groups.filter { it.pieces.min() in hidden }+visible.groups), stash = remaining))
                        }
                    }
                } finally { drag = null }
            }
        }.pointerInput(geometry, dimensions) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val change = event.changes.first()
                        if (change.position.y >= boardHeight) trayScroll = (trayScroll + (change.scrollDelta.x+change.scrollDelta.y)*slot*.65f).coerceIn(0f,maxScroll())
                        else {
                            val scale = (camera.scale*(1f-change.scrollDelta.y*.12f).coerceIn(.5f,2f)).coerceIn(fittedScale*.5f,fittedScale*12f)
                            camera = Camera(scale,change.position-(change.position-camera.offset)*(scale/camera.scale))
                        }
                        change.consume()
                    }
                }
            }
        }) {
        fun DrawScope.group(g: PieceGroup, scale: Float, offset: Offset) {
            withTransform({ translate(offset.x,offset.y); scale(scale,scale,Offset.Zero); translate(g.translation.x,g.translation.y) }) {
                g.pieces.forEach { id ->
                    val x = (id % spec.columns+g.translation.x)*scale+offset.x
                    val y = (id / spec.columns+g.translation.y)*scale+offset.y
                    if (x+1.25f*scale >= 0 && x-.25f*scale <= size.width && y+1.25f*scale >= 0 && y-.25f*scale <= size.height) {
                        val path = paths[id]
                        clipPath(path) { drawArtwork(artwork,spec.columns,spec.rows,spec.columns.toFloat(),spec.rows.toFloat()) }
                        drawPath(path,if (selected in g.pieces) Color(0xFFBD7228) else Outline.copy(alpha=.7f),style=Stroke((if (selected in g.pieces) 2f else 1f)*density/scale))
                    }
                }
            }
        }
        drawRect(Color(0xFFE4E9DF))
        clipRect(0f,0f,size.width,boardHeight) {
            drawRect(Outline.copy(alpha=.07f),camera.offset,Size(spec.columns*camera.scale,spec.rows*camera.scale))
            board.groups.forEach { if (drag?.key !in it.pieces) group(it,camera.scale,camera.offset) }
        }
        drawRect(Color(0xFFFAF8F2),Offset(0f,boardHeight),Size(size.width,trayHeight))
        drawLine(Outline.copy(alpha=.2f),Offset(0f,boardHeight),Offset(size.width,boardHeight),density)
        val first = (floor(trayScroll/slot).toInt()*2).coerceAtLeast(0)
        val last = minOf(tray.size,first+(size.width/slot).toInt()*2+4)
        clipRect(0f,boardHeight,size.width,size.height) {
            for (i in first until last) {
                val key = tray[i]
                if (drag?.key == key) continue
                val g = groups.getValue(key)
                val left = g.pieces.minOf { it % spec.columns }.toFloat()-.28f
                val top = g.pieces.minOf { it / spec.columns }.toFloat()-.28f
                val width = g.pieces.maxOf { it % spec.columns }+1.28f-left
                val height = g.pieces.maxOf { it / spec.columns }+1.28f-top
                val scale = (slot-12*density)/maxOf(width,height)
                val offset = Offset(i/2*slot-trayScroll+(slot-width*scale)/2-left*scale,
                    boardHeight+8*density+i%2*slot+(slot-height*scale)/2-top*scale)
                group(g.copy(translation=Point(0f,0f)),scale,offset)
            }
        }
        drag?.let { d ->
            // Keep the grabbed board point under the pointer; tray pickup is centered.
            val scale = if (d.position.y >= boardHeight && d.fromTray) minOf(camera.scale,slot*.65f) else camera.scale
            group(d.group.copy(translation=Point(0f,0f)),scale,d.position-Offset(d.anchor.x*scale,d.anchor.y*scale))
        }
        if (maxScroll() > 0) {
            val width = size.width * size.width/(maxScroll()+size.width)
            drawRoundRect(Outline.copy(alpha=.3f),Offset(trayScroll/maxScroll()*(size.width-width),size.height-4*density),Size(width,3*density))
        }
    }
}
