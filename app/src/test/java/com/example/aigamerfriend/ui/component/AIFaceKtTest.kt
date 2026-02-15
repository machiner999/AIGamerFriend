package com.example.aigamerfriend.ui.component

import com.example.aigamerfriend.model.Emotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AIFaceKtTest {
    @Test
    fun `all emotions produce distinct FaceParams`() {
        val allParams = Emotion.entries.map { paramsFor(it) }
        assertEquals(Emotion.entries.size, allParams.toSet().size)
    }

    @Test
    fun `HAPPY has positive mouth curve (smile)`() {
        val params = paramsFor(Emotion.HAPPY)
        assertTrue("HAPPY mouthCurve should be positive", params.mouthCurve > 0f)
    }

    @Test
    fun `SAD has negative mouth curve (frown)`() {
        val params = paramsFor(Emotion.SAD)
        assertTrue("SAD mouthCurve should be negative", params.mouthCurve < 0f)
    }

    @Test
    fun `NEUTRAL has zero mouth curve`() {
        val params = paramsFor(Emotion.NEUTRAL)
        assertEquals(0f, params.mouthCurve)
    }

    @Test
    fun `EXCITED has open mouth`() {
        val params = paramsFor(Emotion.EXCITED)
        assertTrue("EXCITED mouthOpenY should be positive", params.mouthOpenY > 0f)
    }

    @Test
    fun `SURPRISED has open mouth`() {
        val params = paramsFor(Emotion.SURPRISED)
        assertTrue("SURPRISED mouthOpenY should be positive", params.mouthOpenY > 0f)
    }

    @Test
    fun `NEUTRAL has closed mouth`() {
        val params = paramsFor(Emotion.NEUTRAL)
        assertEquals(0f, params.mouthOpenY)
    }

    @Test
    fun `SURPRISED has larger eyes than NEUTRAL`() {
        val surprised = paramsFor(Emotion.SURPRISED)
        val neutral = paramsFor(Emotion.NEUTRAL)
        assertTrue(
            "SURPRISED eyeRadiusY should be larger than NEUTRAL",
            surprised.eyeRadiusY > neutral.eyeRadiusY,
        )
    }

    @Test
    fun `WORRIED has negative mouth curve`() {
        val params = paramsFor(Emotion.WORRIED)
        assertTrue("WORRIED mouthCurve should be negative", params.mouthCurve < 0f)
    }

    @Test
    fun `WORRIED has positive brow angle (inner raise)`() {
        val params = paramsFor(Emotion.WORRIED)
        assertTrue("WORRIED browAngle should be positive", params.browAngle > 0f)
    }

    @Test
    fun `HAPPY has negative brow angle (relaxed)`() {
        val params = paramsFor(Emotion.HAPPY)
        assertTrue("HAPPY browAngle should be negative", params.browAngle < 0f)
    }

    @Test
    fun `SAD has highest brow angle among all emotions`() {
        val sadBrowAngle = paramsFor(Emotion.SAD).browAngle
        Emotion.entries.filter { it != Emotion.SAD }.forEach { emotion ->
            assertTrue(
                "SAD browAngle ($sadBrowAngle) should be >= ${emotion.name} browAngle (${paramsFor(emotion).browAngle})",
                sadBrowAngle >= paramsFor(emotion).browAngle,
            )
        }
    }

    @Test
    fun `EXCITED has raised brows (lower browY)`() {
        val excited = paramsFor(Emotion.EXCITED)
        val neutral = paramsFor(Emotion.NEUTRAL)
        assertTrue("EXCITED browY should be lower than NEUTRAL", excited.browY < neutral.browY)
    }
}
