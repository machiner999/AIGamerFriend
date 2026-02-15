package com.example.aigamerfriend.ui.screen

import com.example.aigamerfriend.viewmodel.SessionState
import org.junit.Assert.assertFalse
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
}
