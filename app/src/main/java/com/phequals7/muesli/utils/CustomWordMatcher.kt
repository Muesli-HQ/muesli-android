package com.phequals7.muesli.utils

import com.phequals7.muesli.data.entity.CustomWord
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object CustomWordMatcher {

    private class Entry(
        val replacement: String,
        val matchingThreshold: Double,
        val tokens: List<String>
    ) {
        val normalizedPhrase: String = tokens.joinToString(" ")

        companion object {
            fun create(word: String, replacement: String, matchingThreshold: Double): Entry? {
                val tokens = normalizedTokens(word)
                if (tokens.isEmpty()) return null
                return Entry(replacement, matchingThreshold, tokens)
            }

            private fun normalizedTokens(text: String): List<String> {
                return text.split(" ")
                    .mapNotNull { tokenParts(it)?.core?.lowercase(Locale.getDefault()) }
            }
        }
    }

    private data class TokenParts(
        val prefix: String,
        val core: String,
        val suffix: String
    )

    private data class MatchResult(
        val text: String,
        val consumed: Int
    )

    private val boundaryPunctuation = ".,!?;:\"'()[]{}".toSet()

    fun apply(text: String, customWords: List<CustomWord>): String {
        if (text.isEmpty() || customWords.isEmpty()) return text

        val entries = customWords
            .filter { it.isEnabled }
            .mapNotNull {
                Entry.create(
                    word = it.word,
                    replacement = it.targetWord,
                    matchingThreshold = it.matchingThreshold
                )
            }
        if (entries.isEmpty()) return text

        val entriesByTokenCount = entries.groupBy { it.tokens.count() }
        val tokenCounts = entriesByTokenCount.keys.sortedDescending()
        val words = text.split(" ")
        val result = mutableListOf<String>()
        var index = 0

        while (index < words.size) {
            val match = findBestMatch(
                words = words,
                startingAt = index,
                tokenCounts = tokenCounts,
                entriesByTokenCount = entriesByTokenCount
            )

            if (match == null) {
                result.add(words[index])
                index += 1
                continue
            }

            result.add(match.text)
            index += match.consumed
        }

        return result.joinToString(" ")
    }

    private fun findBestMatch(
        words: List<String>,
        startingAt: Int,
        tokenCounts: List<Int>,
        entriesByTokenCount: Map<Int, List<Entry>>
    ): MatchResult? {
        for (count in tokenCounts) {
            if (count <= 0 || startingAt + count > words.size) continue
            val entries = entriesByTokenCount[count] ?: continue

            val window = words.subList(startingAt, startingAt + count)
            val parts = window.mapNotNull { tokenParts(it) }
            if (parts.size != count) continue
            if (!preservesPhraseBoundaryPunctuation(parts)) continue

            val candidateTokens = parts.map { it.core.lowercase(Locale.getDefault()) }
            val candidate = candidateTokens.joinToString(" ")

            // Check exact match
            val exact = entries.firstOrNull { it.normalizedPhrase == candidate }
            if (exact != null) {
                return MatchResult(
                    text = parts[0].prefix + exact.replacement + parts[count - 1].suffix,
                    consumed = count
                )
            }

            // Check fuzzy similarity match
            var bestEntry: Entry? = null
            var bestScore = 0.0
            for (entry in entries) {
                val score = calculateSimilarity(candidateTokens, entry) ?: continue
                if (score > bestScore) {
                    bestScore = score
                    bestEntry = entry
                }
            }

            if (bestEntry != null) {
                return MatchResult(
                    text = parts[0].prefix + bestEntry.replacement + parts[count - 1].suffix,
                    consumed = count
                )
            }
        }
        return null
    }

    private fun preservesPhraseBoundaryPunctuation(parts: List<TokenParts>): Boolean {
        if (parts.size <= 1) return true

        for (i in parts.indices) {
            if (i > 0 && parts[i].prefix.isNotEmpty()) return false
            if (i < parts.size - 1 && parts[i].suffix.isNotEmpty()) return false
        }
        return true
    }

    private fun calculateSimilarity(candidateTokens: List<String>, entry: Entry): Double? {
        if (candidateTokens.size != entry.tokens.size) return null

        if (entry.tokens.size == 1) {
            val score = jaroWinklerSimilarity(candidateTokens[0], entry.tokens[0])
            return if (score >= entry.matchingThreshold) score else null
        }

        val tokenScores = candidateTokens.zip(entry.tokens).map { (c, e) ->
            jaroWinklerSimilarity(c, e)
        }
        if (!tokenScores.all { it >= entry.matchingThreshold }) return null
        return tokenScores.average()
    }

    private fun tokenParts(token: String): TokenParts? {
        var start = 0
        var end = token.length

        while (start < end && isBoundaryPunctuation(token[start])) {
            start++
        }

        while (start < end && isBoundaryPunctuation(token[end - 1])) {
            end--
        }

        val core = token.substring(start, end)
        if (core.isEmpty()) return null

        return TokenParts(
            prefix = token.substring(0, start),
            core = core,
            suffix = token.substring(end)
        )
    }

    private fun isBoundaryPunctuation(character: Char): Boolean {
        return boundaryPunctuation.contains(character)
    }

    fun jaroWinklerSimilarity(s1: String, s2: String): Double {
        val jaro = jaroSimilarity(s1, s2)
        if (jaro <= 0) return 0.0

        val chars1 = s1.toCharArray()
        val chars2 = s2.toCharArray()
        val prefixLength = min(4, min(chars1.size, chars2.size))
        var commonPrefix = 0
        for (index in 0 until prefixLength) {
            if (chars1[index] == chars2[index]) {
                commonPrefix++
            } else {
                break
            }
        }

        return jaro + commonPrefix * 0.1 * (1.0 - jaro)
    }

    private fun jaroSimilarity(s1: String, s2: String): Double {
        val chars1 = s1.toCharArray()
        val chars2 = s2.toCharArray()

        if (chars1.isEmpty() && chars2.isEmpty()) return 1.0
        if (chars1.isEmpty() || chars2.isEmpty()) return 0.0
        if (chars1.contentEquals(chars2)) return 1.0

        val matchWindow = max(chars1.size, chars2.size) / 2 - 1
        if (matchWindow < 0) return 0.0

        val s1Matches = BooleanArray(chars1.size)
        val s2Matches = BooleanArray(chars2.size)
        var matches = 0.0
        var transpositions = 0.0

        for (i in chars1.indices) {
            val start = max(0, i - matchWindow)
            val end = min(chars2.size - 1, i + matchWindow)
            if (start > end) continue

            for (j in start..end) {
                if (s2Matches[j] || chars1[i] != chars2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0.0) return 0.0

        var k = 0
        for (i in chars1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) {
                k++
            }
            if (chars1[i] != chars2[k]) {
                transpositions++
            }
            k++
        }

        val m = matches
        val t = transpositions / 2.0
        return (m / chars1.size + m / chars2.size + (m - t) / m) / 3.0
    }
}
