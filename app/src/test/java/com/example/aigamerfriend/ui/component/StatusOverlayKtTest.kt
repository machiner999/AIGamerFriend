package com.example.aigamerfriend.ui.component

import androidx.compose.ui.graphics.Color
import com.example.aigamerfriend.viewmodel.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusOverlayKtTest {
    @Test
    fun `Idle returns null`() {
        assertNull(statusOverlayInfo(SessionState.Idle))
    }

    @Test
    fun `Connected returns green and LIVE`() {
        val (color, label) = statusOverlayInfo(SessionState.Connected)!!
        assertEquals(Color(0xFF00E676), color)
        assertEquals("LIVE", label)
    }

    @Test
    fun `Connecting returns yellow and 接続中`() {
        val (color, label) = statusOverlayInfo(SessionState.Connecting)!!
        assertEquals(Color(0xFFFFD600), color)
        assertEquals("接続中", label)
    }

    @Test
    fun `Reconnecting returns yellow and 再接続中`() {
        val (color, label) = statusOverlayInfo(SessionState.Reconnecting)!!
        assertEquals(Color(0xFFFFD600), color)
        assertEquals("再接続中", label)
    }

    @Test
    fun `Error returns red and エラー`() {
        val (color, label) = statusOverlayInfo(SessionState.Error("some error"))!!
        assertEquals(Color(0xFFFF1744), color)
        assertEquals("エラー", label)
    }
}
