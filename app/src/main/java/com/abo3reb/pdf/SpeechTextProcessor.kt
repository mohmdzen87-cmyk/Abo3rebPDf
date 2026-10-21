package com.abo3reb.pdf

/**
 * Cleans layout artifacts from PDF extraction and creates speech-sized chunks
 * without cutting sentences unless a single sentence exceeds the TTS limit.
 */
object SpeechTextProcessor {
    private val sentenceBoundary = Regex("(?<=[.!?؟۔؛;])\\s+|\\n{2,}")
    private val beforePunctuation = Regex("[ \\t]+([،؛:,.!?؟۔])")

    fun normalize(rawText: String): String {
        return rawText
            .replace(0x00A0.toChar(), ' ')
            .replace(0x00AD.toChar().toString(), "")
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("[ \\t]*\\r?\\n[ \\t]*\\r?\\n+[ \\t]*"), "\n\n")
            .replace(Regex("(?<!\\n)\\r?\\n(?!\\n)"), " ")
            .replace(beforePunctuation, "$1")
            .trim()
    }

    /**
     * Returns "ar" for Arabic-dominant text and "en" for Latin-dominant text.
     * The user can override this simple heuristic in the reader settings.
     */
    fun detectLanguageCode(text: String): String {
        var arabicLetters = 0
        var latinLetters = 0
        text.forEach { character ->
            if (Character.isLetter(character)) {
                when (Character.UnicodeScript.of(character.code)) {
                    Character.UnicodeScript.ARABIC -> arabicLetters++
                    Character.UnicodeScript.LATIN -> latinLetters++
                    else -> Unit
                }
            }
        }
        if (arabicLetters == 0 && latinLetters == 0) return "ar"
        val letterCount = arabicLetters + latinLetters
        return if (arabicLetters > 0 && arabicLetters.toFloat() / letterCount >= 0.18f) "ar" else "en"
    }

    fun splitIntoChunks(rawText: String, maxLength: Int): List<String> {
        val text = normalize(rawText)
        if (text.isBlank() || maxLength <= 0) return emptyList()

        val limit = maxLength
        val chunks = mutableListOf<String>()
        var current = ""

        fun flushCurrent() {
            if (current.isNotBlank()) chunks.add(current.trim())
            current = ""
        }

        fun appendLongSegment(segment: String) {
            var remaining = segment.trim()
            while (remaining.length > limit) {
                var splitAt = remaining.lastIndexOf(' ', limit - 1)
                if (splitAt < limit / 2) splitAt = limit
                if (splitAt > 0 && splitAt < remaining.length &&
                    remaining[splitAt - 1].isHighSurrogate()
                ) {
                    splitAt--
                }
                val piece = remaining.substring(0, splitAt).trim()
                if (piece.isNotEmpty()) chunks.add(piece)
                remaining = remaining.substring(splitAt).trimStart()
            }
            if (remaining.isNotBlank()) chunks.add(remaining)
        }

        val sentences = text.split(sentenceBoundary)
        for (rawSentence in sentences) {
            val sentence = rawSentence.trim()
            if (sentence.isEmpty()) continue
            if (sentence.length > limit) {
                flushCurrent()
                appendLongSegment(sentence)
                continue
            }
            if (current.isEmpty()) {
                current = sentence
            } else if (current.length + 1 + sentence.length <= limit) {
                current += " $sentence"
            } else {
                flushCurrent()
                current = sentence
            }
        }
        flushCurrent()
        return chunks
    }
}
