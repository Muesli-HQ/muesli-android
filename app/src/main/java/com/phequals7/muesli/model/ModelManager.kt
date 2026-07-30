package com.phequals7.muesli.model

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and manages on-device sherpa-onnx ASR models.
 *
 * Reference model: NVIDIA Parakeet TDT 0.6B v3 (multilingual, int8),
 * matching the Parakeet v3 model used by muesli-ios via FluidAudio.
 * Files are fetched individually from HuggingFace (no archive extraction).
 */
class ModelManager(private val context: Context) {

    data class ModelFile(val name: String, val url: String, val sizeBytes: Long)

    data class DownloadProgress(
        val currentFile: String,
        val fileBytesDone: Long,
        val totalBytesDone: Long,
        val totalBytes: Long,
    ) {
        val fraction: Float get() = if (totalBytes > 0) totalBytesDone.toFloat() / totalBytes else 0f
    }

    companion object {
        const val MODEL_ID = "parakeet-tdt-0.6b-v3-int8"
        const val DISPLAY_NAME = "Parakeet V3 (multilingual, on-device)"

        private const val HF_BASE =
            "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main"

        val REQUIRED_FILES = listOf(
            ModelFile("tokens.txt", "$HF_BASE/tokens.txt", 103_936L),
            ModelFile("joiner.int8.onnx", "$HF_BASE/joiner.int8.onnx", 6_000_000L),
            ModelFile("decoder.int8.onnx", "$HF_BASE/decoder.int8.onnx", 11_000_000L),
            ModelFile("encoder.int8.onnx", "$HF_BASE/encoder.int8.onnx", 651_000_000L),
        )

        val TOTAL_SIZE_BYTES: Long get() = REQUIRED_FILES.sumOf { it.sizeBytes }
    }

    val modelDir: File
        get() = File(context.filesDir, "models/$MODEL_ID")

    fun modelFile(name: String): File = File(modelDir, name)

    fun isDownloaded(): Boolean =
        REQUIRED_FILES.all { modelFile(it.name).let { f -> f.exists() && f.length() > 0 } }

    /** True if any partial download state exists (for "resume/reset" UI). */
    fun hasPartialDownload(): Boolean =
        modelDir.listFiles()?.any { it.name.endsWith(".part") } == true

    fun downloadedBytes(): Long =
        REQUIRED_FILES.sumOf { f ->
            val done = modelFile(f.name)
            val part = File(modelDir, "${f.name}.part")
            when {
                done.exists() -> done.length()
                part.exists() -> part.length()
                else -> 0L
            }
        }

    /**
     * Downloads all required model files sequentially. Resumable: completed
     * files are skipped and a partially downloaded file resumes from its
     * `.part` marker via an HTTP Range request.
     */
    suspend fun download(
        onProgress: (DownloadProgress) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        modelDir.mkdirs()
        val totalBytes = TOTAL_SIZE_BYTES
        var totalDone = REQUIRED_FILES.filter { modelFile(it.name).exists() }.sumOf { it.sizeBytes }

        for (file in REQUIRED_FILES) {
            ensureActive()
            val dest = modelFile(file.name)
            if (dest.exists() && dest.length() > 0) continue

            val part = File(modelDir, "${file.name}.part")
            var existing = if (part.exists()) part.length() else 0L

            val conn = URL(file.url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = true
                if (existing > 0) {
                    conn.setRequestProperty("Range", "bytes=$existing-")
                }
                conn.connect()

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
