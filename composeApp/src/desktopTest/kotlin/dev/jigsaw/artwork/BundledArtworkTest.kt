package dev.jigsaw.artwork

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class BundledArtworkTest {
    @Test fun bundledImagesDecodeAndExposeColors() = runBlocking {
        for (credit in artworkLibrary.filter { it.file != null }) {
            val artwork=loadArtwork(credit)
            assertEquals(1280,artwork.bitmap!!.width)
            val color=artwork.averagePieceColor(0,0,6,4)
            assertNotNull(color)
            assertEquals(1f,color.alpha)
            assertNotEquals(color,artwork.averagePieceColor(5,3,6,4))
        }
        assertNull(loadArtwork(originalArtwork).averagePieceColor(0,0,6,4))
    }
    @Test fun truncatedImageFailsDecode() = runBlocking {
        assertFails { decodeArtwork(originalArtwork,ByteArray(24)) }
        Unit
    }
}
