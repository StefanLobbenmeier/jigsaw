package dev.jigsaw.artwork

import java.awt.FileDialog
import java.awt.Frame
import java.awt.EventQueue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.File

actual suspend fun pickLocalImage(): PickedImage? {
    val file = suspendCancellableCoroutine<File?> { continuation ->
        EventQueue.invokeLater {
            val dialog=FileDialog(null as Frame?, "Choose a PNG or JPEG", FileDialog.LOAD)
            continuation.invokeOnCancellation { EventQueue.invokeLater { dialog.dispose() } }
            try {
                if (continuation.isActive) dialog.isVisible=true
                val selected=dialog.file?.let { File(dialog.directory,it) }
                if (continuation.isActive) continuation.resume(selected)
            } finally { dialog.dispose() }
        }
    } ?: return null
    return withContext(Dispatchers.IO) {
        require(file.length() in 1..MAX_IMAGE_BYTES.toLong()) { "Choose a PNG or JPEG up to 12 MiB." }
        // Bounded read also handles a file growing between the size check and read.
        val bytes=file.inputStream().use { it.readNBytes(MAX_IMAGE_BYTES+1) }
        require(bytes.size<=MAX_IMAGE_BYTES) { "Choose a PNG or JPEG up to 12 MiB." }
        PickedImage(file.name,bytes)
    }
}
