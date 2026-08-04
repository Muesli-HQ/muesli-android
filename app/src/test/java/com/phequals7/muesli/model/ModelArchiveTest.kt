package com.phequals7.muesli.model

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class ModelArchiveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun writeArchive(entries: Map<String, String>): File {
        val archive = tmp.newFile("model.tar.bz2")
        TarArchiveOutputStream(
            BZip2CompressorOutputStream(BufferedOutputStream(FileOutputStream(archive)))
        ).use { tar ->
            for ((name, content) in entries) {
                val bytes = content.toByteArray()
                val entry = TarArchiveEntry(name)
                entry.size = bytes.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return archive
    }

    @Test
    fun `extracts wanted entries by basename and deletes archive`() {
        val archive = writeArchive(
            mapOf(
                "sherpa-onnx-nemo-parakeet_110m/tokens.txt" to "tok-1\ntok-2\n",
                "sherpa-onnx-nemo-parakeet_110m/model.int8.onnx" to "fake-onnx-bytes",
                "sherpa-onnx-nemo-parakeet_110m/test_wavs/0.wav" to "ignored",
            )
        )
        val outDir = tmp.newFolder("out")
        val seen = mutableListOf<String>()

        ModelArchive.extractTarBz2(archive, outDir, setOf("tokens.txt", "model.int8.onnx")) { seen.add(it) }

        assertEquals("tok-1\ntok-2\n", File(outDir, "tokens.txt").readText())
        assertEquals("fake-onnx-bytes", File(outDir, "model.int8.onnx").readText())
        assertFalse(File(outDir, "0.wav").exists())
        assertFalse(archive.exists()) // archive cleaned up
        assertEquals(listOf("tokens.txt", "model.int8.onnx"), seen)
    }

    @Test
    fun `throws when a wanted entry is missing and keeps archive`() {
        val archive = writeArchive(mapOf("tokens.txt" to "x"))
        val outDir = tmp.newFolder("out2")

        assertThrows(IllegalStateException::class.java) {
            ModelArchive.extractTarBz2(archive, outDir, setOf("tokens.txt", "model.int8.onnx"))
        }
        assertTrue(archive.exists()) // failure leaves the archive for retry
    }

    @Test
    fun `throws for a missing archive`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelArchive.extractTarBz2(File(tmp.root, "nope.tar.bz2"), tmp.newFolder("out3"), setOf("a"))
        }
    }
}
