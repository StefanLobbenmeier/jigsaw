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
import dev.jigsaw.model.starterPuzzles

private val Ink = Color(0xFF243E36)
private val Paper = Color(0xFFF6F3EB)

@Composable
fun JigsawApp() {
    var selected by remember { mutableStateOf(starterPuzzles.first()) }
    var preview by remember { mutableStateOf(false) }
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
                    Landscape(Modifier.fillMaxWidth().height(280.dp), if (preview) selected.columns else 0, if (preview) selected.rows else 0)
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
                        Text(if (preview) "Back to collection" else "Preview puzzle")
                    }
                    Text(if (preview) "${selected.pieceCount} pieces · seed ${selected.seed}" else "A small beginning", color = Ink.copy(alpha = .65f), fontSize = 13.sp)
                }
                Text("${if (preview) "Grid preview only. " else ""}Interlocking pieces and play are coming next.", fontSize = 12.sp, color = Ink.copy(alpha = .6f))
            }
        }
    }
}

/** Original procedural artwork; no external image rights or network dependency. */
@Composable
private fun Landscape(modifier: Modifier, columns: Int, rows: Int) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFFDCE7DE))
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
        if (columns > 0 && rows > 0) {
            for (x in 1 until columns) drawLine(Paper.copy(alpha = .65f), Offset(w * x / columns, 0f), Offset(w * x / columns, h), 1.5f)
            for (y in 1 until rows) drawLine(Paper.copy(alpha = .65f), Offset(0f, h * y / rows), Offset(w, h * y / rows), 1.5f)
        }
    }
}
