package dev.jigsaw.save
import kotlinx.browser.window
actual fun readSave(): String? = window.localStorage.getItem("jigsaw.session.v1")
actual fun writeSave(value: String) { window.localStorage.setItem("jigsaw.session.v1", value) }
