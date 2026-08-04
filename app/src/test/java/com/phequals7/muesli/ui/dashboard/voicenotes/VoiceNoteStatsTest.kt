package com.phequals7.muesli.ui.dashboard.voicenotes

import com.phequals7.muesli.data.entity.DictationResult
import com.phequals7.muesli.data.entity.RecordingSession
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNoteStatsTest {

    private val dayMs = 86_400_000L
    private val now = 200_000L * dayMs // arbitrary fixed "now" for determinism

    private fun note(text: String, daysAgo: Long, durationMs: Long = 0) = DictationResult(
        text = text,
        createdAt = now - daysAgo * dayMs,
        engineIdentifier = "parakeet-tdt-ctc-110m-en-36000-int8",
        durationMs = durationMs,
    )

    @Test
    fun `streak counts consecutive days ending today`() {
        val stats = computeStats(
            dictations = listOf(note("a", 0), note("b", 1), note("c", 2), note("d", 4)),
            meetings = emptyList(),
            nowMs = now,
        )
        assertEquals(3, stats.streakDays)
    }

    @Test
    fun `streak anchors on yesterday when today has no note`() {
        val stats = computeStats(
            dictations = listOf(note("a", 1), note("b", 2)),
            meetings = emptyList(),
            nowMs = now,
        )
        assertEquals(2, stats.streakDays)
    }

    @Test
    fun `streak is zero when the latest note is older than yesterday`() {
        val stats = computeStats(listOf(note("a", 3), note("b", 4)), emptyList(), now)
        assertEquals(0, stats.streakDays)
    }

    @Test
    fun `wpm uses only timed notes and rounds`() {
        val stats = computeStats(
            dictations = listOf(
                note("one two three four five", 0, durationMs = 30_000), // 5 words in 0.5 min = 10 wpm
                note("untimed words do not count", 0, durationMs = 0),
            ),
            meetings = emptyList(),
            nowMs = now,
        )
        assertEquals(10, stats.wpm)
        assertEquals(10, stats.totalWords)
    }

    @Test
    fun `meeting count only counts meeting sessions`() {
        val stats = computeStats(
            dictations = emptyList(),
            meetings = listOf(
                RecordingSession(kind = "meeting", title = "a"),
                RecordingSession(kind = "quickDictation", title = "b"),
                RecordingSession(kind = "meeting", title = "c"),
            ),
            nowMs = now,
        )
        assertEquals(2, stats.meetingCount)
    }

    @Test
    fun `wordCount handles blank and repeated whitespace`() {
        assertEquals(0, wordCount("   "))
        assertEquals(1, wordCount("hello"))
        assertEquals(3, wordCount("  one   two\tthree  "))
    }

    @Test
    fun `formatWordCount formats magnitudes`() {
        assertEquals("999", formatWordCount(999))
        assertEquals("1,234", formatWordCount(1234))
        assertEquals("12.3k", formatWordCount(12345))
        assertEquals("1.2M", formatWordCount(1_234_567))
    }
}
