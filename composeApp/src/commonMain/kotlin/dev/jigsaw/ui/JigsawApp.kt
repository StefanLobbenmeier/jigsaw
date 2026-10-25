package dev.jigsaw.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

private val Ink = Color(0xFF243E36)
private val Paper = Color(0xFFF6F3EB)

@Composable
fun JigsawApp() {
    var selected by remember { mutableStateOf(starterPuzzles.first()) }
    var preview by remember { mutableStateOf(false) }
    var session by remember(selected) { mutableStateOf(PuzzleSession.scattered(selected)) }
    var artwork by remember { mutableStateOf(PuzzleArtwork(originalArtwork, null)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reference by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    fun changeArtwork(credit: ArtworkCredit? = null) {
        scope.launch {
            loading = true
            error = null
            try {
                val next = if (credit != null) loadArtwork(credit) else {
                    val picked = pickLocalImage() ?: return@launch
                    decodeArtwork(ArtworkCredit("upload", picked.name, "Your local image", "Private local file · not uploaded"), picked.bytes)
                }
                artwork = next
                session = PuzzleSession.scattered(selected)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Could not open this image. Try a PNG or JPEG."
            } finally { loading = false }
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, background = Paper, surface = Paper)) {
        Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 920.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("jigsaw", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("TAKE YOUR TIME", fontSize = 11.sp, letterSpacing = 2.sp, color = Ink)
                }
                Spacer(Modifier.height(8.dp))
                Text(if (preview) "Your moment of calm." else "A little piece of calm.", fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("Slow down. Find a corner. Let the bigger picture come together.", fontSize = 17.sp, color = Ink.copy(alpha = .7f))
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFE4E9DF)) {
                    if (preview) PuzzleBoard(session, artwork, { session = it }, Modifier.fillMaxWidth().height(480.dp))
                    else ArtworkPreview(artwork, selected, Modifier.fillMaxWidth().height(280.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(artwork.credit.title, fontSize = 12.sp, letterSpacing = 2.sp, color = Ink)
                        Text(artwork.credit.author, color = Ink.copy(alpha = .65f), fontSize = 14.sp)
                    }
                }
                Text(artwork.credit.license, color = Ink, fontSize = 13.sp)
                artwork.credit.source?.let { source ->
                    TextButton(onClick = { uriHandler.openUri(source) }) { Text("Source and image rights ↗") }
                }
                Text("Choose an image", fontWeight = FontWeight.SemiBold, color = Ink)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    artworkLibrary.forEach { credit ->
                        FilterChip(selected = artwork.credit.id == credit.id, enabled = !loading,
                            onClick = { changeArtwork(credit) }, label = { Text(credit.title) })
                    }
                    OutlinedButton(enabled = !loading, onClick = { changeArtwork() }) { Text("Open local image…") }
                    Text("PNG or JPEG · up to 12 MiB / 16 MP · center cropped", fontSize = 12.sp, color = Ink)
                }
                if (loading) Text("Opening image…", color = Ink)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Choose your pace", fontWeight = FontWeight.SemiBold, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    starterPuzzles.forEach { spec ->
                        FilterChip(selected = selected.columns == spec.columns && selected.rows == spec.rows, onClick = { selected = spec }, label = { Text("${spec.pieceCount} pieces") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(enabled = !loading, onClick = { preview = !preview }, contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp)) {
                        Text(if (preview) "Back to collection" else "Start puzzle")
                    }
                    Text(if (preview) "${selected.pieceCount} pieces · seed ${selected.seed}" else "A small beginning", color = Ink.copy(alpha = .65f), fontSize = 13.sp)
                }
                if (preview) {
                    LinearProgressIndicator(progress = { session.progress }, modifier = Modifier.fillMaxWidth())
                    Text(if (session.complete) "Complete. Take a moment to enjoy it." else "${selected.pieceCount - session.groups.size} of ${selected.pieceCount - 1} connections · Drag matching pieces close to join them.", color = Ink)
                    Row {
                        TextButton(onClick = { selected = selected.copy(seed = selected.seed + 1) }) { Text("New game") }
                        TextButton(onClick = { reference = !reference }) { Text(if (reference) "Hide reference" else "Show reference") }
                    }
                    if (reference) ArtworkPreview(artwork, selected, Modifier.fillMaxWidth().height(240.dp))
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
private fun PuzzleBoard(session: PuzzleSession, artwork: PuzzleArtwork, update: (PuzzleSession) -> Unit, modifier: Modifier) {
    val current by rememberUpdatedState(session)
    val onUpdate by rememberUpdatedState(update)
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    val spec = session.geometry.spec
    fun scale() = minOf(dimensions.width / (spec.columns+4f), dimensions.height / (spec.rows+4f))
    Canvas(modifier.onSizeChanged { dimensions = it }.pointerInput(spec) {
        var dragged: Int? = null
        detectDragGestures(
            onDragStart = { position ->
                val cell = scale()
                if (cell > 0) {
                    dragged = current.hitTest(Point(position.x/cell-2,position.y/cell-2))
                    dragged?.let { onUpdate(current.raise(it)) }
                }
            },
            onDrag = { change, amount -> dragged?.let {
                change.consume()
                onUpdate(current.move(it,Point(amount.x/scale(),amount.y/scale())))
            } },
            onDragEnd = { dragged?.let { onUpdate(current.snap(it)) }; dragged=null },
            onDragCancel = { dragged=null }
        )
    }) {
        val cell = scale()
        if (cell <= 0) return@Canvas
        withTransform({ translate(cell*2,cell*2); scale(cell,cell,Offset.Zero) }) {
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
