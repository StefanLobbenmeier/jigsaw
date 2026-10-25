@file:Suppress("UnsafeCastFromDynamic")
package dev.jigsaw.artwork

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.Int8Array
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun pickLocalImage(): PickedImage? = suspendCancellableCoroutine { continuation ->
    val input=document.createElement("input") as HTMLInputElement
    input.type="file"
    input.accept="image/png,image/jpeg"
    input.style.display="none"
    document.body!!.appendChild(input)
    var reader: FileReader?=null
    fun cleanup() { input.remove() }
    fun finish(value: PickedImage?) { cleanup(); if (continuation.isActive) continuation.resume(value) }
    fun fail(message: String) { cleanup(); if (continuation.isActive) continuation.resumeWithException(IllegalArgumentException(message)) }
    input.addEventListener("cancel", { finish(null) })
    input.onchange = {
        val file=input.files?.item(0)
        if (file==null) finish(null)
        else if (file.size.toDouble()>MAX_IMAGE_BYTES) fail("Choose a PNG or JPEG up to 12 MiB.")
        else {
            reader=FileReader().also { r ->
                r.onload = {
                    val array=Int8Array(r.result as ArrayBuffer)
                    finish(PickedImage(file.name,ByteArray(array.length) { array[it] }))
                }
                r.onerror = { fail("Could not read this image. Try another file.") }
                r.onabort = { finish(null) }
                r.readAsArrayBuffer(file)
            }
        }
    }
    continuation.invokeOnCancellation { reader?.abort(); cleanup() }
    input.click()
}
