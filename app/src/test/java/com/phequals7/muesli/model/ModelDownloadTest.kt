package com.phequals7.muesli.model

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Download/resume behavior of ModelManager against a fake HTTP server.
 * Uses the test-only root-dir constructor so no Android Context is needed.
 */
class ModelDownloadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun modelWith(url: String, size: Long) = SpeechModel(
        id = "test-model",
        displayName = "Test",
        shortName = "Test",
        capabilityLabel = "",
        detail = "",
        kind = SpeechModelKind.NEMO_CTC,
        files = listOf(ModelManager.ModelFile("model.bin", url, size)),
    )

    @Test
    fun `downloads a file and marks the model downloaded`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello-model"))
        server.start()

        val model = modelWith(server.url("/model.bin").toString(), 11)
        val manager = ModelManager(model, tmp.newFolder("model"))
        manager.download { }

        assertTrue(manager.isDownloaded())
        assertEquals("hello-model", manager.modelFile("model.bin").readText())
        assertEquals(1, server.requestCount)
        server.shutdown()
    }

    @Test
    fun `resumes a partial file with a Range request`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(206).setBody("model"))
        server.start()

        val model = modelWith(server.url("/model.bin").toString(), 11)
        val manager = ModelManager(model, tmp.newFolder("model"))
        // Seed a .part file with the first 6 bytes of "hello-model"
        manager.modelDir.mkdirs()
        manager.modelFile("model.bin.part").writeText("hello-")

        manager.download { }

        val recorded = server.takeRequest()
        assertEquals("bytes=6-", recorded.getHeader("Range"))
        assertEquals("hello-model", manager.modelFile("model.bin").readText())
        server.shutdown()
    }

    @Test
    fun `restarts the file when the server ignores Range`() = runTest {
        val server = MockWebServer()
        // Server answers 200 (full body) despite the Range header
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello-model"))
        server.start()

        val model = modelWith(server.url("/model.bin").toString(), 11)
        val manager = ModelManager(model, tmp.newFolder("model"))
        manager.modelDir.mkdirs()
        manager.modelFile("model.bin.part").writeText("hello-")

        manager.download { }

        assertEquals("hello-model", manager.modelFile("model.bin").readText())
        server.shutdown()
    }

    @Test
    fun `skips files that are already complete`() = runTest {
        val server = MockWebServer()
        server.start()

        val model = modelWith(server.url("/model.bin").toString(), 11)
        val manager = ModelManager(model, tmp.newFolder("model"))
        manager.modelDir.mkdirs()
        manager.modelFile("model.bin").writeText("hello-model")

        manager.download { }

        assertEquals(0, server.requestCount)
        assertTrue(manager.isDownloaded())
        server.shutdown()
    }
}
