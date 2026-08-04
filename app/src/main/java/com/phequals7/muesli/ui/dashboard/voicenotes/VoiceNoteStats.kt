package com.phequals7.muesli.ui.dashboard.voicenotes

import com.phequals7.muesli.data.entity.DictationResult
import com.phequals7.muesli.data.entity.RecordingSession
import kotlin.math.roundToInt

/** Dashboard stats for the Voice Notes home (pure logic, unit-tested). */
internal data class VoiceNoteStats(
    val streakDays: Int,
    val totalWords: Int,
    val wpm: Int,
    val meetingCount: Int,
)

internal fun computeStats(
    dictations: List<DictationResult>,
    meetings: List<RecordingSession>,
    nowMs: Long = System.currentTimeMillis(),
): VoiceNoteStats {
    val totalWords = dictations.sumOf { wordCount(it.text) }

    val timedWords = dictations.filter { it.durationMs > 0 }.sumOf { wordCount(it.text) }
    val timedMinutes = dictations.sumOf { it.durationMs } / 60_000.0
    val wpm = if (timedMinutes > 0) (timedWords / timedMinutes).roundToInt() else 0

    // Streak: consecutive day buckets with >= 1 note, ending today or yesterday
    val dayMs = 86_400_000L
    val days = dictations.map { it.createdAt / dayMs }.toSet()
    var streak = 0
    var cursor = nowMs / dayMs
    if (cursor !in days) cursor-- // allow "yesterday" as streak anchor
    while (cursor in days) {
        streak++
        cursor--
    }

    return VoiceNoteStats(
        streakDays = streak,
        totalWords = totalWords,
        wpm = wpm,
        meetingCount = meetings.count { it.kind == "meeting" },
    )
}

internal fun wordCount(text: String): Int =
    text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

internal fun formatWordCount(words: Int): String = when {
    words >= 1_000_000 -> "%.1fM".format(words / 1_000_000.0)
    words >= 10_000 -> "%.1fk".format(words / 1_000.0)
    words >= 1_000 -> "%,d".format(words)
    else -> "$words"
}
