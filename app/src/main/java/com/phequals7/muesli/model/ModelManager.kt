package com.phequals7.muesli.model

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

/**
 * Downloads and manages one on-device sherpa-onnx ASR model from the
 * [SpeechModels] catalog (Android counterpart of the iOS
 * ModelBackgroundDownloadService).
 *
 * Files are fetched individually over HTTP (no archive extraction) unless the
 * model is only distributed as a tar.bz2 — in that case the archive is
 * downloaded once and extracted ([SpeechModel.packagedAsTarBz2]).
 * Downloads are resumable: completed files are skipped and a partial file
 * resumes from its `.part` marker via an HTTP Range request.
 */
class ModelManager(
    private val context: Context,
    val model: SpeechModel = SpeechModels.selected(context),
) {

    data class ModelFile(val name: String, val url: String, val sizeBytes: Long)

    data class DownloadProgress(
        val currentFile: String,
        val fileBytesDone: Long,
        val totalBytesDone: Long,
        val totalBytes: Long,
        val extracting: Boolean = false,
    ) {
        val fraction: Float get() = if (totalBytes > 0) totalBytesDone.toFloat() / totalBytes else 0f
    }

    val modelDir: File
        get() = File(context.filesDir, "models/${model.id}")

    fun modelFile(name: String): File = File(modelDir, name)

    fun isDownloaded(): Boolean =
        model.expectedFiles.all { modelFile(it).let { f -> f.exists() && f.length() > 0 } }

    /** True if any partial download state exists (for "resume/reset" UI). */
    fun hasPartialDownload(): Boolean =
        !isDownloaded() && modelDir.listFiles()?.any { it.name.endsWith(".part") || it.name == "model.tar.bz2" } == true

    fun downloadedBytes(): Long =
        if (isDownloaded()) {
            model.expectedFiles.sumOf { modelFile(it).length() }
        } else {
            model.files.sumOf { f ->
                val done = modelFile(f.name)
                val part = File(modelDir, "${f.name}.part")
                when {
                    done.exists() -> done.length()
                    part.exists() -> part.length()
                    else -> 0L
                }
            }
        }

    /**
     * Downloads all required model files sequentially, then extracts the
     * archive when the model is packaged as tar.bz2. Resumable.
     */
    suspend fun download(
        onProgress: (DownloadProgress) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        modelDir.mkdirs()
        val totalBytes = model.totalSizeBytes
        var totalDone = model.files.filter { modelFile(it.name).exists() }.sumOf { it.sizeBytes }

        for (file in model.files) {
            ensureActive()
            val dest = modelFile(file.name)
            if (dest.exists() && dest.length() > 0) continue

            val part = File(modelDir, "${file.name}.part")
            var existing = if (part.exists()) part.length() else 0L

            val conn = openWithFallback(file.url, rangeFrom = existing)
            try {
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_OK && existing > 0) {
                    // Server ignored the Range request; restart this file.
                    part.delete()
                    existing = 0L
                } else if (code !in intArrayOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                    throw RuntimeException("Download of ${file.name} failed: HTTP $code")
                }

                val append = existing > 0
                var fileDone = existing
                conn.inputStream.use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            fileDone += read
                            onProgress(
                                DownloadProgress(
                                    currentFile = file.name,
                                    fileBytesDone = fileDone,
                                    totalBytesDone = totalDone + fileDone,
                                    totalBytes = totalBytes,
                                )
                            )
                        }
                        output.flush()
                    }
                }
                part.renameTo(dest)
                totalDone += file.sizeBytes
            } finally {
                conn.disconnect()
            }
        }

        if (model.packagedAsTarBz2) {
            extractTarBz2(modelFile(model.files.single().name)) { current ->
                onProgress(
                    DownloadProgress(
                        currentFile = current,
                        fileBytesDone = totalBytes,
                        totalBytesDone = totalBytes,
                        totalBytes = totalBytes,
                        extracting = true,
                    )
                )
            }
        }
    }

    /**
     * Extracts the files listed in [SpeechModel.requiredOutputs] from a
     * tar.bz2 archive into [modelDir], then deletes the archive.
     */
    private fun extractTarBz2(archive: File, onEntry: (String) -> Unit) {
        check(archive.exists()) { "Missing archive ${archive.name}" }
        val wanted = model.requiredOutputs.toMutableSet()
        TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive)))
        ).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (name !in wanted) continue
                onEntry(name)
                val out = modelFile(name)
                FileOutputStream(out).use { output ->
                    tar.copyTo(output, bufferSize = 256 * 1024)
                }
                wanted.remove(name)
            }
        }
        check(wanted.isEmpty()) { "Archive is missing: ${wanted.joinToString()}" }
        archive.delete()
    }

    /**
     * Opens a connection, retrying once against hf-mirror.com when
     * huggingface.co cannot be resolved — some ISPs/regions block or
     * hijack its DNS, which otherwise presents as a stalled download.
     */
    private fun openWithFallback(url: String, rangeFrom: Long): HttpURLConnection {
        fun open(u: String): HttpURLConnection {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.instanceFollowRedirects = true
            if (rangeFrom > 0) c.setRequestProperty("Range", "bytes=$rangeFrom-")
            return c
        }
        return try {
            open(url).also { it.connect() }
        } catch (e: UnknownHostException) {
            val mirror = url.replace("huggingface.co", "hf-mirror.com")
            if (mirror == url) throw e
            open(mirror).also { it.connect() }
        }
    }

    fun deleteModel() {
        modelDir.deleteRecursively()
    }

    /** Formats bytes as a human readable string, e.g. "638 MB". */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
        else -> "%.0f KB".format(bytes / 1_000.0)
    }
}
