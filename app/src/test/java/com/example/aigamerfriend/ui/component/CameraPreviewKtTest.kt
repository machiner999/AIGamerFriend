package com.example.aigamerfriend.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPreviewKtTest {
    private val interval = 1000L

    @Test
    fun `returns true when interval has elapsed`() {
        assertTrue(shouldCaptureFrame(now = 2000L, lastCaptureTimeMs = 500L, captureIntervalMs = interval))
    }

    @Test
    fun `returns false when within interval`() {
        assertFalse(shouldCaptureFrame(now = 1500L, lastCaptureTimeMs = 1000L, captureIntervalMs = interval))
    }

    @Test
    fun `returns true at exact boundary`() {
        assertTrue(shouldCaptureFrame(now = 2000L, lastCaptureTimeMs = 1000L, captureIntervalMs = interval))
    }

    @Test
    fun `returns false when 1ms before boundary`() {
        assertFalse(shouldCaptureFrame(now = 1999L, lastCaptureTimeMs = 1000L, captureIntervalMs = interval))
    }

    @Test
    fun `returns true on first frame when lastCaptureTimeMs is zero`() {
        assertTrue(shouldCaptureFrame(now = 1000L, lastCaptureTimeMs = 0L, captureIntervalMs = interval))
    }
}
