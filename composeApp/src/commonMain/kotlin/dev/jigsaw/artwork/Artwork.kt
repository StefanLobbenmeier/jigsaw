package dev.jigsaw.artwork

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.jigsaw.resources.Res
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stable bundled identity; uploads need their bytes persisted by a later persistence adapter. */
data class ArtworkCredit(val id: String, val title: String, val author: String, val license: String, val source: String? = null, val file: String? = null)
val originalArtwork = ArtworkCredit("original", "The quiet hills", "Jigsaw original artwork", "Original procedural artwork")
val artworkLibrary = listOf(
    originalArtwork,
    ArtworkCredit("starry-night", "The Starry Night", "Vincent van Gogh · Google Art Project scan", "Public domain · PD-Art / Public Domain Mark 1.0", "https://commons.wikimedia.org/wiki/File:Van_Gogh_-_Starry_Night_-_Google_Art_Project.jpg", "starry-night.jpg"),
    ArtworkCredit("great-wave", "The Great Wave", "Anonymous reprint after Katsushika Hokusai · Library of Congress", "Public domain · PD-Art / Public Domain Mark 1.0", "https://commons.wikimedia.org/wiki/File:Great_Wave_off_Kanagawa.jpg", "great-wave.jpg")
)
const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
const val MAX_IMAGE_PIXELS = 16_000_000L
const val MAX_IMAGE_EDGE = 8192

data class PickedImage(val name: String, val bytes: ByteArray)
/** Null means cancelled. Errors leave the current artwork and session untouched. */
expect suspend fun pickLocalImage(): PickedImage?

data class ImageCrop(val offset: IntOffset, val size: IntSize)
fun centerCrop(width: Int, height: Int, columns: Int, rows: Int): ImageCrop {
    require(width > 0 && height > 0 && columns > 0 && rows > 0)
    val ratio = columns.toDouble() / rows
    val w = minOf(width, (height * ratio).toInt()).coerceAtLeast(1)
    val h = minOf(height, (width / ratio).toInt()).coerceAtLeast(1)
    return ImageCrop(IntOffset((width-w)/2, (height-h)/2), IntSize(w,h))
}

/** Header validation happens before native decoding, including for highly compressed images. */
fun validateImage(bytes: ByteArray): IntSize {
    require(bytes.size in 24..MAX_IMAGE_BYTES) { "Choose a PNG or JPEG up to 12 MiB." }
    fun u(i: Int) = bytes[i].toInt() and 255
    fun be16(i: Int) = (u(i) shl 8) or u(i+1)
    fun be32(i: Int): Long = (0..3).fold(0L) { n, j -> (n shl 8) or u(i+j).toLong() }
    val dimensions = if (bytes.take(8) == listOf(137,80,78,71,13,10,26,10).map { it.toByte() }) {
        require(bytes.copyOfRange(12,16).decodeToString() == "IHDR") { "Invalid PNG header." }
        be32(16) to be32(20)
    } else {
        require(u(0)==255 && u(1)==216) { "Only PNG and JPEG images are supported." }
        var p=2
        var found: Pair<Long,Long>? = null
        while (p+3 < bytes.size) {
            require(u(p)==255) { "Invalid JPEG header." }
            while (p < bytes.size && u(p)==255) p++
            require(p+2 < bytes.size) { "Truncated JPEG." }
            val marker=u(p++)
            if (marker==0xD9 || marker==0xDA) break
            if (marker==0x01 || marker in 0xD0..0xD7) continue
            val length=be16(p)
            require(length>=2 && p+length<=bytes.size) { "Truncated JPEG." }
            if (marker in listOf(0xC0,0xC1,0xC2)) {
                require(length>=8) { "Invalid JPEG dimensions." }
                found=be16(p+5).toLong() to be16(p+3).toLong()
                break
            }
            p+=length
        }
        requireNotNull(found) { "Unsupported JPEG encoding." }
    }
    val (w,h)=dimensions
    require(w in 1..MAX_IMAGE_EDGE.toLong() && h in 1..MAX_IMAGE_EDGE.toLong() && w*h<=MAX_IMAGE_PIXELS) {
        "Image is too large: maximum 8192 pixels per side and 16 megapixels. Resize it first."
    }
    return IntSize(w.toInt(),h.toInt())
}

class PuzzleArtwork(
    val credit: ArtworkCredit,
    val bitmap: ImageBitmap?,
    /** Keep bounded upload bytes available to a later persistence adapter; never serialize bitmap. */
    val encodedBytes: ByteArray? = null
) {
    private val pixels by lazy { bitmap?.toPixelMap() }

    /** Crop-aligned pixel access for stash sorting; sample outside the hot draw loop. */
    fun averagePieceColor(column: Int, row: Int, columns: Int, rows: Int): Color? {
        val image=bitmap ?: return null
        require(column in 0 until columns && row in 0 until rows)
        val crop=centerCrop(image.width,image.height,columns,rows)
        val pixels=checkNotNull(pixels)
        var r=0f; var g=0f; var b=0f
        for (y in 0 until 8) for (x in 0 until 8) {
            val px=crop.offset.x+((column+(x+.5f)/8)*crop.size.width/columns).toInt()
            val py=crop.offset.y+((row+(y+.5f)/8)*crop.size.height/rows).toInt()
            val c=pixels[px.coerceIn(0,image.width-1),py.coerceIn(0,image.height-1)]
            r+=c.red; g+=c.green; b+=c.blue
        }
        return Color(r/64,g/64,b/64)
    }
}
suspend fun decodeArtwork(credit: ArtworkCredit, bytes: ByteArray): PuzzleArtwork = withContext(Dispatchers.Default) {
    val dimensions=validateImage(bytes)
    val bitmap=bytes.decodeToImageBitmap()
    require(bitmap.width==dimensions.width && bitmap.height==dimensions.height) { "Image dimensions do not match its header." }
    PuzzleArtwork(credit,bitmap,bytes)
}
suspend fun loadArtwork(credit: ArtworkCredit): PuzzleArtwork = credit.file?.let {
    decodeArtwork(credit,Res.readBytes("files/$it"))
} ?: PuzzleArtwork(credit,null)
