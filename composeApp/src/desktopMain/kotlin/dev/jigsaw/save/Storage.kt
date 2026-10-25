package dev.jigsaw.save
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
private fun location(): Path = Path.of(System.getProperty("user.home"), ".jigsaw", "session-v1.txt")
actual fun readSave(): String? { val path = location(); if (!Files.exists(path)) return null; require(Files.size(path) <= MAX_SAVE_CHARS); return Files.readString(path) }
actual fun writeSave(value: String) {
    val path = location(); Files.createDirectories(path.parent)
    val temporary = Files.createTempFile(path.parent, "session-", ".tmp")
    try { Files.writeString(temporary,value); Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE) }
    finally { Files.deleteIfExists(temporary) }
}
