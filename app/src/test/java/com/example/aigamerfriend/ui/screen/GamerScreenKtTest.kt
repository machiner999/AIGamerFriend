package com.example.aigamerfriend.ui.screen

import com.example.aigamerfriend.model.Emotion
import com.example.aigamerfriend.viewmodel.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamerScreenKtTest {
    @Test
    fun `Connected is active`() {
        assertTrue(isSessionActive(SessionState.Connected))
    }

    @Test
    fun `Connecting is active`() {
        assertTrue(isSessionActive(SessionState.Connecting))
    }

    @Test
    fun `Reconnecting is active`() {
        assertTrue(isSessionActive(SessionState.Reconnecting))
    }

    @Test
    fun `Idle is not active`() {
        assertFalse(isSessionActive(SessionState.Idle))
    }

    @Test
    fun `Error is not active`() {
        assertFalse(isSessionActive(SessionState.Error("test error")))
    }

    // Haptic feedback tests

    @Test
    fun `session transition to Connected returns CONFIRM`() {
        assertEquals(
            HapticType.CONFIRM,
            hapticForSessionTransition(SessionState.Connecting, SessionState.Connected),
        )
    }

    @Test
    fun `session transition Reconnecting to Connected returns CONFIRM`() {
        assertEquals(
            HapticType.CONFIRM,
            hapticForSessionTransition(SessionState.Reconnecting, SessionState.Connected),
        )
    }

    @Test
    fun `session transition Connected to Connected returns null`() {
        assertNull(hapticForSessionTransition(SessionState.Connected, SessionState.Connected))
    }

    @Test
    fun `session transition to Error returns REJECT`() {
        assertEquals(
            HapticType.REJECT,
            hapticForSessionTransition(SessionState.Connected, SessionState.Error("fail")),
        )
    }

    @Test
    fun `session transition Idle to Connecting returns null`() {
        assertNull(hapticForSessionTransition(SessionState.Idle, SessionState.Connecting))
    }

    @Test
    fun `emotion change returns TICK`() {
        assertEquals(
            HapticType.TICK,
            hapticForEmotionChange(Emotion.NEUTRAL, Emotion.HAPPY),
        )
    }

    @Test
    fun `same emotion returns null`() {
        assertNull(hapticForEmotionChange(Emotion.HAPPY, Emotion.HAPPY))
    }
}
