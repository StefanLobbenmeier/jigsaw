package dev.jigsaw

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.jigsaw.ui.JigsawApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { JigsawApp() }
}
