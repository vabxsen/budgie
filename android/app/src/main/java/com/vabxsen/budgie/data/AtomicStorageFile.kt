package com.vabxsen.budgie.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Crash-safe whole-file storage: each write goes to a temporary file that atomically replaces the
 * original, so a reader sees either the old or the new contents.
 */
internal class AtomicStorageFile(val baseFile: File) {
    private val newFile = File(baseFile.path + ".new")
    // Left by android.util.AtomicFile on older Android versions when a write was interrupted.
    private val legacyBackup = File(baseFile.path + ".bak")

    fun exists(): Boolean = baseFile.exists() || legacyBackup.exists()

    fun readText(): String {
        if (legacyBackup.exists()) move(legacyBackup, baseFile)
        return baseFile.readText(Charsets.UTF_8)
    }

    fun write(bytes: ByteArray) {
        baseFile.parentFile?.mkdirs()
        try {
            FileOutputStream(newFile).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            move(newFile, baseFile)
        } catch (e: Exception) {
            newFile.delete()
            throw e
        }
    }

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
