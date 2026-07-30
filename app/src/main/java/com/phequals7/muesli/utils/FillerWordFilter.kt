package com.phequals7.muesli.utils

import java.util.Locale

object FillerWordFilter {
    private val fillers: Set<String> = setOf(
        "uh", "um", "uh,", "um,", "uhh", "umm",
        "er", "err", "ah", "ahh",
        "hmm", "hm", "mm", "mmm",
        "like,",
        "you know,"
    )

    private val fillerPhrases = listOf(
        "you know,?" to "",
        "i mean,?" to "",
        "sort of,?" to "",
        "kind of,?" to ""
    )

    fun apply(text: String): String {
        if (text.isEmpty()) return text

        var result = text

        for ((pattern, replacement) in fillerPhrases) {
            // Case-insensitive regex replacement for phrase patterns
            // Trailing \b would fail when the phrase ends with a comma (non-word char
            // followed by a space is not a word boundary); use a lookahead instead.
            val regex = Regex("(?i)\\b$pattern(?=\\s|$|[,.!?])")
            result = result.replace(regex, replacement)
        }

        // Clean up individual filler words
        val words = result.split(" ")
        result = words
            .filter { word -> !fillers.contains(word.lowercase(Locale.getDefault())) }
            .joinToString(" ")

        // Remove extra spaces
        while (result.contains("  ")) {
            result = result.replace("  ", " ")
        }

        result = result.trim()

        // Capitalize the first letter if it is lowercase
        if (result.isNotEmpty() && result[0].isLowerCase()) {
            result = result.substring(0, 1).uppercase(Locale.getDefault()) + result.substring(1)
        }

        return result
    }
}
