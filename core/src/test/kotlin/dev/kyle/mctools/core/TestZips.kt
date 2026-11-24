package dev.kyle.mctools.core

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun makeZip(file: File, entries: Map<String, ByteArray>): File {
    ZipOutputStream(file.outputStream()).use { z ->
        for ((name, bytes) in entries) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
    }
    return file
}

fun String.b() = toByteArray()
