package dev.jigsaw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.drawscope.*

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
    var fitRequest by remember { mutableStateOf(0) }
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
                artwork = next; workspace = Workspace.fresh(selected.copy(cutVersion = 2))
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    starterPuzzles.forEach { spec -> FilterChip(selected = selected.columns == spec.columns && selected.rows == spec.rows, enabled = !loading, onClick = { workspace = Workspace.fresh(spec) }, label = { Text("${spec.pieceCount}") }) }
                }
                Text(artwork.credit.title + " · " + artwork.credit.author + "\n" + artwork.credit.license, fontSize = 12.sp)
                artwork.credit.source?.let { source -> TextButton(onClick = { uriHandler.openUri(source) }) { Text("Image source & rights ↗") } }
                Button(onClick = { playing = true }, enabled = !loading) { Text("Continue puzzle") }
            } else {
                LinearProgressIndicator(progress = { session.progress }, modifier = Modifier.fillMaxWidth())
                Text("${selected.pieceCount-session.groups.size} / ${selected.pieceCount-1} joins · ${workspace.stash.size} stashed groups", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { reference = !reference }) { Text("Reference") }
                    TextButton(onClick = { fitRequest++ }) { Text("Fit all") }
                    TextButton(onClick = { edges = !edges }) { Text(if (edges) "All pieces" else "Edges first") }
                }
                if (reference) ArtworkPreview(artwork, selected, Modifier.fillMaxWidth().height(180.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StashSort.entries.forEach { mode -> FilterChip(selected = workspace.sort == mode, onClick = { workspace = workspace.sorted(mode, colors) }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    PlaySurface(workspace, artwork, edges, fitRequest, { workspace = it }, Modifier.fillMaxWidth().height(if (maxWidth < 600.dp) 420.dp else 520.dp))
                }
                Text("Drag pieces between the board and tray. Swipe sideways in the tray to browse. Pinch to zoom; drag empty board space to pan. Fit all brings every board piece into view. Keyboard: focus the board, N selects the next group, arrows move it, Enter transfers, J joins.", fontSize = 12.sp, color = Ink)
                Row {
                    TextButton(onClick = { workspace = Workspace.fresh(selected) }) { Text("Reset") }
                    TextButton(onClick = { workspace = Workspace.fresh(selected.copy(seed = selected.seed+1, cutVersion = 2)) }) { Text("New game") }
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
internal fun DrawScope.drawArtwork(artwork: PuzzleArtwork, columns: Int, rows: Int, width: Float, height: Float) {
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

internal fun PieceGeometry.path(): Path = Path().apply {
    val curves = outline
    moveTo(curves.first().start.x, curves.first().start.y)
    curves.forEach { cubicTo(it.control1.x,it.control1.y,it.control2.x,it.control2.y,it.end.x,it.end.y) }
    close()
}
