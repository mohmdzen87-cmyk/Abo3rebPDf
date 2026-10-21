package com.abo3reb.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextProcessorTest {
    @Test
    fun normalizeJoinsPdfLineWrapsAndKeepsArabicPunctuation() {
        val actual = SpeechTextProcessor.normalize("مرحباً بالعالم،\nهذا نص عربي.")
        assertEquals("مرحباً بالعالم، هذا نص عربي.", actual)
    }

    @Test
    fun detectsArabicAndLatinDominantText() {
        assertEquals("ar", SpeechTextProcessor.detectLanguageCode("هذا كتاب عربي للاستخدام"))
        assertEquals("en", SpeechTextProcessor.detectLanguageCode("This is a book about reading PDFs"))
    }

    @Test
    fun chunksStayWithinLimitAndKeepSentenceEndings() {
        val chunks = SpeechTextProcessor.splitIntoChunks(
            "أول جملة. ثاني جملة. ثالث جملة.",
            maxLength = 20
        )
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.length <= 20 })
        assertTrue(chunks.first().endsWith("."))
    }

    @Test
    fun longTextIsSplitIntoNonEmptyChunksWithinLimit() {
        val chunks = SpeechTextProcessor.splitIntoChunks("كلمة ".repeat(30), maxLength = 18)
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.length <= 18 })
        assertTrue(chunks.all { it.isNotBlank() })
    }
}
