package dev.jigsaw

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.jigsaw.ui.JigsawApp

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Jigsaw", state = rememberWindowState(width = 1100.dp, height = 800.dp)) {
        JigsawApp()
    }
}
