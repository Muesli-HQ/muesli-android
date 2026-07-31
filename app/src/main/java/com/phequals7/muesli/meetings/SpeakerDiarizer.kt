package com.phequals7.muesli.meetings

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

/**
 * On-device speaker diarization (who spoke when) for finished meetings —
 * the Android counterpart of the FluidAudio diarizer in muesli-ios.
 *
 * Both models are bundled in assets (no download):
 *   - pyannote segmentation 3.0 (int8, ~1.5 MB) finds speech turns
 *   - NeMo TitaNet-S (~40 MB) extracts speaker embeddings for clustering
 *
 * Runs once at meeting stop on the retained WAV. Expected cost is roughly
 * 0.1× the meeting duration on a modern phone (RTF ≈ 0.11 per sherpa-onnx
 * benchmarks), i.e. ~30 s of processing for a 5-minute meeting.
 */
class SpeakerDiarizer(private val context: Context) {

    companion object {
        private const val TAG = "SpeakerDiarizer"
        const val SEGMENTATION_MODEL = "models/pyannote_segmentation_3_0.int8.onnx"
        const val EMBEDDING_MODEL = "models/nemo_en_titanet_small.onnx"
    }

    /** Diarizes 16 kHz mono float PCM into speaker segments. Caller releases
     * via [AutoCloseable] semantics of the underlying recognizer. */
    fun diarize(pcm: FloatArray): List<SpeakerLabeler.SpeakerSegment> {
        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = SEGMENTATION_MODEL),
                numThreads = 2,
                provider = "cpu",
                debug = false,
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = EMBEDDING_MODEL,
                numThreads = 2,
                provider = "cpu",
                debug = false,
            ),
            // Unknown speaker count: let fast-clustering decide by threshold.
            clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f),
            minDurationOn = 0.3f,
            minDurationOff = 0.5f,
        )
        Log.i(TAG, "Diarizing ${"%.1f".format(pcm.size / 16000f)}s of audio")
        val start = System.currentTimeMillis()
        val sd = OfflineSpeakerDiarization(assetManager = context.assets, config = config)
        try {
            val segments = sd.process(pcm)
            Log.i(
                TAG,
                "Diarization produced ${segments.size} segments " +
                    "(${segments.map { it.speaker }.toSet().size} speakers) " +
                    "in ${System.currentTimeMillis() - start} ms"
            )
            return segments.map {
                SpeakerLabeler.SpeakerSegment(startSec = it.start, endSec = it.end, speaker = it.speaker)
            }
        } finally {
            sd.release()
        }
    }
}
