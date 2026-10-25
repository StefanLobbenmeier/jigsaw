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
                    if (preview) PuzzleBoard(session, { session = it }, Modifier.fillMaxWidth().height(480.dp))
                    else Landscape(Modifier.fillMaxWidth().height(280.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("THE QUIET HILLS", fontSize = 12.sp, letterSpacing = 2.sp, color = Ink)
                        Text("An original landscape, made for a slower day.", color = Ink.copy(alpha = .65f), fontSize = 14.sp)
                    }
                }
                Text("Choose your pace", fontWeight = FontWeight.SemiBold, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    starterPuzzles.forEach { spec ->
                        FilterChip(selected = selected == spec, onClick = { selected = spec }, label = { Text("${spec.pieceCount} pieces") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { preview = !preview }, contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp)) {
                        Text(if (preview) "Back to collection" else "Start puzzle")
                    }
                    Text(if (preview) "${selected.pieceCount} pieces · seed ${selected.seed}" else "A small beginning", color = Ink.copy(alpha = .65f), fontSize = 13.sp)
                }
                if (preview) {
                    LinearProgressIndicator(progress = { session.progress }, modifier = Modifier.fillMaxWidth())
                    Text(if (session.complete) "Complete. Take a moment to enjoy it." else "${selected.pieceCount - session.groups.size} of ${selected.pieceCount - 1} connections · Drag matching pieces close to join them.", color = Ink)
                    TextButton(onClick = { session = PuzzleSession.scattered(selected) }) { Text("Scatter again") }
                }
            }
        }
    }
}

/** Artwork interface for the next stage: replace this DrawScope renderer with a decoded image. */
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

@Composable
private fun Landscape(modifier: Modifier) {
    Canvas(modifier) { drawOriginalArtwork(size.width, size.height) }
}

private fun PieceGeometry.path(): Path = Path().apply {
    val curves = outline
    moveTo(curves.first().start.x, curves.first().start.y)
    curves.forEach { cubicTo(it.control1.x,it.control1.y,it.control2.x,it.control2.y,it.end.x,it.end.y) }
    close()
}

@Composable
private fun PuzzleBoard(session: PuzzleSession, update: (PuzzleSession) -> Unit, modifier: Modifier) {
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
                        clipPath(path) { drawOriginalArtwork(spec.columns.toFloat(),spec.rows.toFloat()) }
                        drawPath(path,Ink.copy(alpha=.65f),style=Stroke(1f/cell))
                    }
                }
            }
        }
    }
}
