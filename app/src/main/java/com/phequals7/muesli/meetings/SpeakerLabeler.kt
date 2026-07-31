package com.phequals7.muesli.meetings

/**
 * Pure speaker-labeling logic for meeting transcripts: maps decoded text
 * chunks onto diarization segments by maximum time overlap and renders a
 * readable, speaker-grouped transcript. Kept free of Android/sherpa types so
 * it is unit-testable (iOS renders a similar speaker-grouped transcript in
 * the meeting detail view).
 */
object SpeakerLabeler {

    /** One decoded chunk of transcript with its position in the recording. */
    data class TimedText(val startSec: Float, val endSec: Float, val text: String)

    /** One diarization segment: who spoke when (speaker ids count from 0). */
    data class SpeakerSegment(val startSec: Float, val endSec: Float, val speaker: Int)

    /** Speaker with the largest time overlap with [startSec, endSec), or null
     * when there is no overlap at all. */
    fun dominantSpeaker(startSec: Float, endSec: Float, segments: List<SpeakerSegment>): Int? {
        var bestSpeaker: Int? = null
        var bestOverlap = 0f
        for (seg in segments) {
            val overlap = minOf(endSec, seg.endSec) - maxOf(startSec, seg.startSec)
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                bestSpeaker = seg.speaker
            }
        }
        return bestSpeaker
    }

    /**
     * Renders chunks as a speaker-grouped transcript. Consecutive chunks from
     * the same speaker merge under one heading; chunks with no overlapping
     * diarization segment inherit the previous chunk's speaker (or are left
     * unlabeled when first).
     *
     * Example:
     *   Speaker 1 · 0:04
     *   Let's review the launch plan…
     *
     *   Speaker 2 · 0:31
     *   Sounds good, two things…
     */
    fun render(chunks: List<TimedText>, segments: List<SpeakerSegment>): String {
        if (chunks.isEmpty()) return ""
        val out = StringBuilder()
        var currentSpeaker: Int? = null
        var firstBlock = true

        for (chunk in chunks) {
            var speaker = dominantSpeaker(chunk.startSec, chunk.endSec, segments)
            if (speaker == null) speaker = currentSpeaker

            if (speaker != currentSpeaker) {
                if (!firstBlock) out.append("\n\n")
                out.append("Speaker ")
                    .append((speaker ?: 0) + 1)
                    .append(" · ")
                    .append(formatTime(chunk.startSec))
                    .append("\n")
                currentSpeaker = speaker
                firstBlock = false
            } else {
                out.append("\n\n")
            }
            out.append(chunk.text)
        }
        return out.toString()
    }

    /** mm:ss (hours flatten into minutes, meeting-style). */
    fun formatTime(sec: Float): String {
        val total = sec.toInt().coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }
}
