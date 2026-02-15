package com.example.aigamerfriend.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EmotionTest {
    @Test
    fun `fromString parses all valid emotion names`() {
        Emotion.entries.forEach { emotion ->
            assertEquals(emotion, Emotion.fromString(emotion.name))
        }
    }

    @Test
    fun `fromString is case insensitive`() {
        assertEquals(Emotion.HAPPY, Emotion.fromString("happy"))
        assertEquals(Emotion.HAPPY, Emotion.fromString("Happy"))
        assertEquals(Emotion.HAPPY, Emotion.fromString("hApPy"))
        assertEquals(Emotion.EXCITED, Emotion.fromString("excited"))
        assertEquals(Emotion.SAD, Emotion.fromString("Sad"))
    }

    @Test
    fun `fromString returns NEUTRAL for unknown string`() {
        assertEquals(Emotion.NEUTRAL, Emotion.fromString("ANGRY"))
        assertEquals(Emotion.NEUTRAL, Emotion.fromString("unknown"))
        assertEquals(Emotion.NEUTRAL, Emotion.fromString("foo"))
    }

    @Test
    fun `fromString returns NEUTRAL for empty string`() {
        assertEquals(Emotion.NEUTRAL, Emotion.fromString(""))
    }

    @Test
    fun `enum contains exactly 7 entries`() {
        assertEquals(7, Emotion.entries.size)
    }
}
