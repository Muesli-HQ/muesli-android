package com.phequals7.muesli.model

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Pure tar.bz2 extraction for models distributed as archives (Parakeet
 * 110M). Kept free of Android types so it is unit-testable on the JVM.
 */
internal object ModelArchive {

    /**
     * Extracts the entries named in [wanted] (matched by basename) from
     * [archive] into [outDir], then deletes the archive. Throws when any
     * wanted entry is missing from the archive.
     */
    fun extractTarBz2(archive: File, outDir: File, wanted: Set<String>, onEntry: (String) -> Unit = {}) {
        require(archive.exists()) { "Missing archive ${archive.name}" }
        outDir.mkdirs()
        val remaining = wanted.toMutableSet()
        TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive)))
        ).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (name !in remaining) continue
                onEntry(name)
                FileOutputStream(File(outDir, name)).use { output ->
                    tar.copyTo(output, bufferSize = 256 * 1024)
                }
                remaining.remove(name)
            }
        }
        check(remaining.isEmpty()) { "Archive is missing: ${remaining.joinToString()}" }
        archive.delete()
    }
}
