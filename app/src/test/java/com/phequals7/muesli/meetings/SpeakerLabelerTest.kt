package com.phequals7.muesli.meetings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.phequals7.muesli.meetings.SpeakerLabeler.SpeakerSegment
import com.phequals7.muesli.meetings.SpeakerLabeler.TimedText

class SpeakerLabelerTest {

    @Test
    fun `dominantSpeaker picks the segment with maximum overlap`() {
        val segments = listOf(
            SpeakerSegment(0f, 10f, speaker = 0),
            SpeakerSegment(10f, 20f, speaker = 1),
        )
        // 12–19 overlaps speaker 1 for 7s, speaker 0 for 0s
        assertEquals(1, SpeakerLabeler.dominantSpeaker(12f, 19f, segments))
        // 8–14 overlaps speaker 0 for 2s and speaker 1 for 4s
        assertEquals(1, SpeakerLabeler.dominantSpeaker(8f, 14f, segments))
        // 8–12 overlaps speaker 0 for 2s and speaker 1 for 2s -> first wins tie
        assertEquals(0, SpeakerLabeler.dominantSpeaker(8f, 12f, segments))
    }

    @Test
    fun `dominantSpeaker returns null when there is no overlap`() {
        val segments = listOf(SpeakerSegment(0f, 5f, speaker = 0))
        assertNull(SpeakerLabeler.dominantSpeaker(10f, 12f, segments))
    }

    @Test
    fun `render groups consecutive chunks by speaker`() {
        val chunks = listOf(
            TimedText(0f, 8f, "hello there"),
            TimedText(9f, 15f, "still me talking"),
            TimedText(16f, 22f, "the other person replies"),
        )
        val segments = listOf(
            SpeakerSegment(0f, 15.5f, speaker = 0),
            SpeakerSegment(15.5f, 25f, speaker = 1),
        )
        val rendered = SpeakerLabeler.render(chunks, segments)
        assertEquals(
            "Speaker 1 · 0:00\nhello there\n\nstill me talking\n\n" +
                "Speaker 2 · 0:16\nthe other person replies",
            rendered
        )
    }

    @Test
    fun `render inherits previous speaker for chunks without overlap`() {
        val chunks = listOf(
            TimedText(0f, 5f, "first"),
            TimedText(30f, 35f, "no segment covers this"),
        )
        val segments = listOf(SpeakerSegment(0f, 6f, speaker = 2))
        val rendered = SpeakerLabeler.render(chunks, segments)
        assertTrue(rendered.startsWith("Speaker 3 · 0:00\nfirst"))
        assertTrue(rendered.contains("no segment covers this"))
        // No new heading for the orphan chunk (inherits speaker 2)
        assertEquals(1, rendered.split("Speaker 3").size - 1)
    }

    @Test
    fun `render handles empty input`() {
        assertEquals("", SpeakerLabeler.render(emptyList(), emptyList()))
    }

    @Test
    fun `formatTime renders m ss`() {
        assertEquals("0:04", SpeakerLabeler.formatTime(4.2f))
        assertEquals("1:05", SpeakerLabeler.formatTime(65f))
        assertEquals("12:00", SpeakerLabeler.formatTime(720f))
    }
}
