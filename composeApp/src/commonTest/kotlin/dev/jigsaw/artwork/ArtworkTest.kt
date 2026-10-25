package dev.jigsaw.artwork

import kotlin.test.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

class ArtworkTest {
    private fun png(width: Int, height: Int): ByteArray = ByteArray(24).also { b ->
        byteArrayOf(137.toByte(),80,78,71,13,10,26,10).copyInto(b)
        "IHDR".encodeToByteArray().copyInto(b,12)
        for (i in 0..3) { b[16+i]=(width ushr (24-i*8)).toByte(); b[20+i]=(height ushr (24-i*8)).toByte() }
    }
    @Test fun rejectsLargeDimensionsBeforeDecode() {
        assertFailsWith<IllegalArgumentException> { validateImage(png(8193,1)) }
        assertFailsWith<IllegalArgumentException> { validateImage(png(5000,5000)) }
        assertFailsWith<IllegalArgumentException> { validateImage(png(0,100)) }
        assertEquals(IntSize(4000,4000),validateImage(png(4000,4000)))
    }
    @Test fun rejectsInvalidAndOversizeFiles() {
        assertFailsWith<IllegalArgumentException> { validateImage(ByteArray(24)) }
        assertFailsWith<IllegalArgumentException> { validateImage(ByteArray(MAX_IMAGE_BYTES+1)) }
        assertFailsWith<IllegalArgumentException> { validateImage(byteArrayOf(255.toByte(),216.toByte())) }
    }
    @Test fun cropCentersWithoutStretching() {
        assertEquals(ImageCrop(IntOffset(0,250),IntSize(1000,500)),centerCrop(1000,1000,2,1))
        assertEquals(ImageCrop(IntOffset(250,0),IntSize(500,1000)),centerCrop(1000,1000,1,2))
        val crop=centerCrop(1280,903,6,4)
        assertEquals(IntSize(1280,853),crop.size)
        assertEquals(IntOffset(0,25),crop.offset)
    }
}
