package com.phequals7.muesli.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechModelsTest {

    @Test
    fun `catalog has unique ids`() {
        val ids = SpeechModels.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `catalog contains v3 transducer and 110m ctc`() {
        val v3 = SpeechModels.PARAKEET_V3
        assertEquals(SpeechModelKind.NEMO_TRANSDUCER, v3.kind)
        assertTrue(v3.files.any { it.name == "encoder.int8.onnx" })
        assertTrue(v3.files.all { it.url.startsWith("https://") })

        val m110 = SpeechModels.PARAKEET_110M
        assertEquals(SpeechModelKind.NEMO_CTC, m110.kind)
        assertTrue(m110.packagedAsTarBz2)
        assertEquals(listOf("tokens.txt", "model.int8.onnx"), m110.requiredOutputs)
    }

    @Test
    fun `expectedFiles are downloads for plain models and outputs for archives`() {
        assertEquals(
            SpeechModels.PARAKEET_V3.files.map { it.name },
            SpeechModels.PARAKEET_V3.expectedFiles,
        )
        assertEquals(
            SpeechModels.PARAKEET_110M.requiredOutputs,
            SpeechModels.PARAKEET_110M.expectedFiles,
        )
    }

    @Test
    fun `byId falls back to v3 for unknown or empty ids`() {
        assertEquals(SpeechModels.PARAKEET_V3, SpeechModels.byId(null))
        assertEquals(SpeechModels.PARAKEET_V3, SpeechModels.byId(""))
        assertEquals(SpeechModels.PARAKEET_V3, SpeechModels.byId("no-such-model"))
        assertEquals(SpeechModels.PARAKEET_110M, SpeechModels.byId(SpeechModels.PARAKEET_110M.id))
    }

    @Test
    fun `default id resolves to a catalog entry`() {
        assertTrue(SpeechModels.all.any { it.id == SpeechModels.DEFAULT_ID })
    }
}
