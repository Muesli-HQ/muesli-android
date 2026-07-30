package com.phequals7.muesli.utils

import com.phequals7.muesli.data.entity.CustomWord
import org.junit.Assert.assertEquals
import org.junit.Test

class TextProcessingTest {

    @Test
    fun testFillerWordFilter() {
        val rawInput = "Uh hello, um, this is, you know, a simple test."
        val expectedOutput = "Hello, this is, a simple test."
        val processed = FillerWordFilter.apply(rawInput)
        assertEquals(expectedOutput, processed)
    }

    @Test
    fun testFillerWordFilterCapitalization() {
        val rawInput = "um test"
        val expectedOutput = "Test"
        val processed = FillerWordFilter.apply(rawInput)
        assertEquals(expectedOutput, processed)
    }

    @Test
    fun testJaroWinklerSimilarityExact() {
        val s1 = "muesli"
        val s2 = "muesli"
        val sim = CustomWordMatcher.jaroWinklerSimilarity(s1, s2)
        assertEquals(1.0, sim, 0.001)
    }

    @Test
    fun testCustomWordMatcherReplacements() {
        val customWords = listOf(
            CustomWord(word = "muesli", replacement = "MuesliApp", matchingThreshold = 0.85),
            CustomWord(word = "chatgpt", replacement = "ChatGPT", matchingThreshold = 0.8)
        )

        // Exact match replacement
        val input1 = "I love muesli app."
        val expected1 = "I love MuesliApp app."
        assertEquals(expected1, CustomWordMatcher.apply(input1, customWords))

        // Fuzzy match replacement: "musli" is highly similar to "muesli"
        val input2 = "I love musli app."
        val expected2 = "I love MuesliApp app."
        assertEquals(expected2, CustomWordMatcher.apply(input2, customWords))
    }
}
