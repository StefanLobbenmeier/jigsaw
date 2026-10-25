package dev.jigsaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jigsaw.model.*
import dev.jigsaw.artwork.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

private val Ink = Color(0xFF243E36)
private val Paper = Color(0xFFF6F3EB)

@Composable
fun JigsawApp() {
    var workspace by remember { mutableStateOf(Workspace.fresh(starterPuzzles.first())) }
    val session = workspace.session
    val selected = session.geometry.spec
    var playing by remember { mutableStateOf(false) }
    var artwork by remember { mutableStateOf(PuzzleArtwork(originalArtwork, null)) }
    var loading by remember { mutableStateOf(true) }
    var ready by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Opening saved game…") }
    var reference by remember { mutableStateOf(false) }
    var edges by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<Int?>(null) }
    var zoom by remember { mutableStateOf(1f) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) {
        try {
            dev.jigsaw.save.readSave()?.let { raw ->
                val saved = dev.jigsaw.save.SessionSave.decode(raw)
                val restored = if (saved.imageId.startsWith("upload-")) decodeArtwork(ArtworkCredit(saved.imageId, saved.imageName, "Your local image", "Private local file"), saved.imageBytes!!)
                    else loadArtwork(artworkLibrary.first { it.id == saved.imageId })
                artwork = restored; workspace = saved.workspace; playing = true
            }
            message = "Progress saves on this device."
        } catch (failure: Exception) { message = "Could not restore progress: ${failure.message}. Start a new game below." }
        finally { ready = true; loading = false }
    }
    LaunchedEffect(workspace, artwork, ready) {
        if (ready) {
            kotlinx.coroutines.delay(500)
            try {
                dev.jigsaw.save.writeSave(dev.jigsaw.save.SessionSave.encode(dev.jigsaw.save.SavedGame(workspace, artwork.credit.id, artwork.credit.title, if (artwork.credit.id.startsWith("upload-")) artwork.encodedBytes else null)))
                message = "Saved on this device"
            } catch (failure: Exception) { message = "Progress could not be saved. Storage may be full or unavailable. ${failure.message ?: "Try a smaller image or free device storage."}" }
        }
    }
    val colors = remember(artwork, selected) { (0 until selected.pieceCount).associateWith { id ->
        val c = artwork.averagePieceColor(id % selected.columns, id / selected.columns, selected.columns, selected.rows)
        c?.let { Triple(it.red,it.green,it.blue) } ?: originalPieceColor(id, selected)
    } }
    fun changeArtwork(credit: ArtworkCredit? = null) {
        scope.launch {
            loading = true
            try {
                val next = if (credit != null) loadArtwork(credit) else {
                    val picked = pickLocalImage() ?: return@launch
                    decodeArtwork(ArtworkCredit("upload-${kotlin.random.Random.nextLong()}", picked.name, "Your local image", "Private local file"), picked.bytes)
                }
                artwork = next; workspace = Workspace.fresh(selected); active = null
            } catch (failure: Exception) { message = failure.message ?: "Could not open image." }
            finally { loading = false }
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, background = Paper, surface = Paper)) {
        Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("jigsaw", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
                TextButton(onClick = { playing = !playing }, enabled = !loading) { Text(if (playing) "Collection" else "Play") }
            }
            Text(if (session.complete) "The bigger picture. Beautifully joined." else "A little piece of calm.", fontSize = 25.sp, color = Ink)
            Text(message, fontSize = 12.sp, color = Ink)
            if (!playing) {
                ArtworkPreview(artwork, selected, Modifier.fillMaxWidth().height(240.dp))
                artworkLibrary.forEach { credit -> FilterChip(selected = artwork.credit.id == credit.id, enabled = !loading, onClick = { changeArtwork(credit) }, label = { Text(credit.title) }) }
                OutlinedButton(onClick = { changeArtwork() }, enabled = !loading) { Text("Open PNG / JPEG…") }
                Text("Images up to 12 MiB / 16 MP. Uploads up to 2 MiB can be saved. Files stay on your device.", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    starterPuzzles.forEach { spec -> FilterChip(selected = selected.columns == spec.columns, enabled = !loading, onClick = { workspace = Workspace.fresh(spec); active = null }, label = { Text("${spec.pieceCount}") }) }
                }
                Text(artwork.credit.title + " · " + artwork.credit.author + "\n" + artwork.credit.license, fontSize = 12.sp)
                artwork.credit.source?.let { source -> TextButton(onClick = { uriHandler.openUri(source) }) { Text("Image source & rights ↗") } }
                Button(onClick = { playing = true }, enabled = !loading) { Text("Continue puzzle") }
            } else {
                LinearProgressIndicator(progress = { session.progress }, modifier = Modifier.fillMaxWidth())
                Text("${selected.pieceCount-session.groups.size} / ${selected.pieceCount-1} joins · ${workspace.stash.size} stashed groups", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { reference = !reference }) { Text("Reference") }
                    TextButton(onClick = { zoom = 1f }) { Text("Fit all") }
                    TextButton(onClick = { edges = !edges }) { Text(if (edges) "All pieces" else "Edges first") }
                }
                if (reference) ArtworkPreview(artwork, selected, Modifier.fillMaxWidth().height(180.dp))
                Text("Workspace zoom", fontSize = 12.sp)
                Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..2.5f)
                BoxWithConstraints(Modifier.fillMaxWidth().height(420.dp).background(Color(0xFFE4E9DF), RoundedCornerShape(20.dp))) {
                    val boardWidth = maxWidth
                    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
                    PuzzleBoard(session.copy(groups = session.groups.filter { g -> workspace.stash.none { it in g.pieces } }), artwork, { updated ->
                        workspace = workspace.copy(session = session.copy(groups = session.groups.filter { g -> workspace.stash.any { it in g.pieces } } + updated.groups))
                    }, Modifier.width(boardWidth * zoom).height(410.dp * zoom), { active = it })
                    }
                }
                val boardGroups = session.groups.filter { g -> workspace.stash.none { it in g.pieces } }
                if (boardGroups.isNotEmpty()) {
                    TextButton(onClick = {
                        val index = boardGroups.indexOfFirst { active in it.pieces }
                        active = boardGroups[(index+1) % boardGroups.size].pieces.min()
                    }) { Text("Select next board group (${active?.plus(1) ?: "none"})") }
                }
                active?.let { id ->
                    if (session.groups.any { id in it.pieces } && workspace.stash.none { key -> session.groups.first { id in it.pieces }.pieces.contains(key) }) {
                        Row {
                            listOf("←" to Point(-.2f,0f), "↑" to Point(0f,-.2f), "↓" to Point(0f,.2f), "→" to Point(.2f,0f)).forEach { (label,delta) ->
                                TextButton(onClick = { workspace = workspace.copy(session = session.move(id,delta)) }) { Text(label) }
                            }
                            TextButton(onClick = {
                                val visible = session.copy(groups = boardGroups).snap(id)
                                workspace = workspace.copy(session = session.copy(groups = session.groups.filter { g -> workspace.stash.any { it in g.pieces } } + visible.groups))
                            }) { Text("Join") }
                        }
                        OutlinedButton(onClick = { workspace = workspace.transfer(id, true); active = null }) { Text("Return selected group to stash") }
                    }
                }
                Text("Stash", fontWeight = FontWeight.Bold, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StashSort.entries.forEach { mode -> FilterChip(selected = workspace.sort == mode, onClick = { workspace = workspace.sorted(mode, colors) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    workspace.stash.filter { key -> !edges || session.groups.first { key in it.pieces }.pieces.any { shapeKey(session.geometry,it).first() < '4' } }.forEach { key ->
                        val group = session.groups.first { key in it.pieces }
                        Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)), verticalAlignment = Alignment.CenterVertically) {
                            PuzzleBoard(session.copy(groups = listOf(group)), artwork, {}, Modifier.size(64.dp), {}, true)
                            Text("${key+1} · ${group.pieces.size} pc", modifier = Modifier.weight(1f), fontSize = 12.sp)
                            TextButton(onClick = { workspace = workspace.reorder(key,-1) }) { Text("↑") }
                            TextButton(onClick = { workspace = workspace.reorder(key,1) }) { Text("↓") }
                            TextButton(onClick = { workspace = workspace.transfer(key,false); active = key }) { Text("Play") }
                        }
                    }
                }
                Text("Play brings a whole group to the center. Drag matching pieces close to join. Tap or drag a board piece to select it, then return it to the stash. Use stash arrows to order manually. Fit all recovers every board piece. Tab navigates controls; Enter activates. Scroll the enlarged workspace to pan.", fontSize = 12.sp, color = Ink)
                Row {
                    TextButton(onClick = { workspace = Workspace.fresh(selected); active = null }) { Text("Reset") }
                    TextButton(onClick = { workspace = Workspace.fresh(selected.copy(seed = selected.seed+1)); active = null }) { Text("New game") }
                }
            }
        }
    }
}

/** Original procedural artwork remains available independently of external images. */
fun DrawScope.drawOriginalArtwork(width: Float, height: Float) {
    val w = width
    val h = height
    drawRect(Color(0xFFDCE7DE), size = androidx.compose.ui.geometry.Size(w,h))
    drawCircle(Color(0xFFF5D99B), h * .14f, Offset(w * .73f, h * .25f))
    fun hill(color: Color, start: Float, control: Float, end: Float) {
        drawPath(Path().apply {
            moveTo(0f, h * start)
            cubicTo(w * .3f, h * control, w * .6f, h * end, w, h * (start - .1f))
            lineTo(w, h); lineTo(0f, h); close()
        }, color)
    }
    hill(Color(0xFF9CB5A1), .64f, .03f, .85f)
    hill(Color(0xFF668E79), .85f, .37f, .45f)
    hill(Color(0xFF365F50), 1.08f, .62f, .7f)
}

/** The same centered source rectangle drives preview, pieces and color sampling. */
private fun DrawScope.drawArtwork(artwork: PuzzleArtwork, columns: Int, rows: Int, width: Float, height: Float) {
    val image = artwork.bitmap
    if (image == null) drawOriginalArtwork(width, height)
    else {
        val crop = centerCrop(image.width, image.height, columns, rows)
        // Transform instead of rounding cell-unit destination dimensions.
        withTransform({ scale(width / crop.size.width, height / crop.size.height, Offset.Zero) }) {
            drawImage(image, srcOffset = crop.offset, srcSize = crop.size, dstOffset = IntOffset.Zero, dstSize = crop.size)
        }
    }
}

@Composable
private fun ArtworkPreview(artwork: PuzzleArtwork, spec: PuzzleSpec, modifier: Modifier) {
    Canvas(modifier) {
        val width = minOf(size.width, size.height * spec.columns / spec.rows)
        val height = width * spec.rows / spec.columns
        translate((size.width-width)/2, (size.height-height)/2) {
            drawArtwork(artwork, spec.columns, spec.rows, width, height)
        }
    }
}

private fun PieceGeometry.path(): Path = Path().apply {
    val curves = outline
    moveTo(curves.first().start.x, curves.first().start.y)
    curves.forEach { cubicTo(it.control1.x,it.control1.y,it.control2.x,it.control2.y,it.end.x,it.end.y) }
    close()
}

@Composable
private fun PuzzleBoard(session: PuzzleSession, artwork: PuzzleArtwork, update: (PuzzleSession) -> Unit, modifier: Modifier, select: (Int) -> Unit = {}, thumbnail: Boolean = false) {
    val current by rememberUpdatedState(session)
    val onUpdate by rememberUpdatedState(update)
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    val spec = session.geometry.spec
    var dragging by remember { mutableStateOf(false) }
    val boundsSession = remember(dragging, if (dragging) null else session) { session }
    val minX = minOf(if (thumbnail) Float.MAX_VALUE else 0f, boundsSession.groups.minOfOrNull { g -> g.pieces.minOf { it % spec.columns + g.translation.x } } ?: 0f) - .5f
    val minY = minOf(if (thumbnail) Float.MAX_VALUE else 0f, boundsSession.groups.minOfOrNull { g -> g.pieces.minOf { it / spec.columns + g.translation.y } } ?: 0f) - .5f
    val maxX = maxOf(if (thumbnail) -Float.MAX_VALUE else spec.columns.toFloat(), boundsSession.groups.maxOfOrNull { g -> g.pieces.maxOf { it % spec.columns + g.translation.x + 1 } } ?: 0f) + .5f
    val maxY = maxOf(if (thumbnail) -Float.MAX_VALUE else spec.rows.toFloat(), boundsSession.groups.maxOfOrNull { g -> g.pieces.maxOf { it / spec.columns + g.translation.y + 1 } } ?: 0f) + .5f
    fun scale() = minOf(dimensions.width / (maxX-minX), dimensions.height / (maxY-minY))
    val origin by rememberUpdatedState(Point(minX,minY))
    val cellSize by rememberUpdatedState(scale())
    Canvas(modifier.clipToBounds().onSizeChanged { dimensions = it }.pointerInput(session) {
        detectTapGestures { pos -> if (scale() > 0) current.hitTest(Point(pos.x/scale()+minX,pos.y/scale()+minY))?.let(select) }
    }.pointerInput(spec) {
        var dragged: Int? = null
        detectDragGestures(
            onDragStart = { position ->
                dragging = true
                val cell = cellSize
                if (cell > 0) {
                    dragged = current.hitTest(Point(position.x/cell+origin.x,position.y/cell+origin.y))
                    dragged?.let { select(it); onUpdate(current.raise(it)) }
                }
            },
            onDrag = { change, amount -> dragged?.let {
                change.consume()
                onUpdate(current.move(it,Point(amount.x/cellSize,amount.y/cellSize)))
            } },
            onDragEnd = { dragged?.let { onUpdate(current.snap(it)) }; dragged=null; dragging=false },
            onDragCancel = { dragged=null; dragging=false }
        )
    }) {
        val cell = scale()
        if (cell <= 0) return@Canvas
        withTransform({ translate(-cell*minX,-cell*minY); scale(cell,cell,Offset.Zero) }) {
            drawRect(Ink.copy(alpha=.08f), size=androidx.compose.ui.geometry.Size(spec.columns.toFloat(),spec.rows.toFloat()))
            current.groups.forEach { group ->
                translate(group.translation.x,group.translation.y) {
                    group.pieces.forEach { id ->
                        val path = current.geometry.pieces[id].path()
                        clipPath(path) { drawArtwork(artwork, spec.columns, spec.rows, spec.columns.toFloat(),spec.rows.toFloat()) }
                        drawPath(path,Ink.copy(alpha=.65f),style=Stroke(1f/cell))
                    }
                }
            }
        }
    }
}
