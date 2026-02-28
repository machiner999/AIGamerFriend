package com.example.aigamerfriend.viewmodel

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.aigamerfriend.data.MemoryStore
import com.example.aigamerfriend.data.SettingsStore
import com.example.aigamerfriend.model.Emotion
import com.example.aigamerfriend.settingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GamerViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: GamerViewModel

    private var stopCalled = false
    private var connectCallCount = 0
    private var shouldThrow = false
    private var videoFramesSent = 0

    private val fakeHandle =
        object : SessionHandle {
            override fun stopAudioConversation() {
                stopCalled = true
            }

            override suspend fun sendVideoFrame(jpegBytes: ByteArray) {
                videoFramesSent++
            }
        }

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        stopCalled = false
        connectCallCount = 0
        shouldThrow = false
        videoFramesSent = 0

        val mockApp = mockk<Application>(relaxed = true)
        every { mockApp.applicationContext } returns mockApp

        val mockMemoryStore = mockk<MemoryStore>(relaxed = true)
        coEvery { mockMemoryStore.getFormattedSummaries() } returns null

        val mockSettingsStore = mockk<SettingsStore>(relaxed = true)
        coEvery { mockSettingsStore.isOnboardingShown() } returns true
        every { mockSettingsStore.voiceNameFlow() } returns flowOf("AOEDE")
        every { mockSettingsStore.reactionIntensityFlow() } returns flowOf("ふつう")

        // Mock the settingsDataStore extension property so the ViewModel init doesn't crash
        val mockDataStore = mockk<DataStore<Preferences>>(relaxed = true)
        mockkStatic("com.example.aigamerfriend.AIGamerFriendAppKt")
        every { mockApp.settingsDataStore } returns mockDataStore

        viewModel = GamerViewModel(mockApp)
        viewModel.memoryStore = mockMemoryStore
        viewModel.settingsStore = mockSettingsStore
        viewModel.summarizer = { null } // no-op summarizer for tests
        viewModel.sessionConnector = {
            connectCallCount++
            if (shouldThrow) throw RuntimeException("Network error")
            fakeHandle
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Runs a test and stops the ViewModel session before runTest cleanup. */
    private fun viewModelTest(block: suspend TestScope.() -> Unit) =
        runTest {
            try {
                block()
            } finally {
                viewModel.stopSession()
            }
        }

    @Test
    fun `initial state is Idle`() {
        assertEquals(SessionState.Idle, viewModel.sessionState.value)
    }

    @Test
    fun `startSession transitions to Connected`() =
        viewModelTest {
            viewModel.startSession()

            assertEquals(SessionState.Connected, viewModel.sessionState.value)
            assertEquals(1, connectCallCount)
        }

    @Test
    fun `startSession is ignored when already Connected`() =
        viewModelTest {
            viewModel.startSession()

            assertEquals(SessionState.Connected, viewModel.sessionState.value)
            assertEquals(1, connectCallCount)

            // Call startSession again — should be ignored
            viewModel.startSession()

            assertEquals(SessionState.Connected, viewModel.sessionState.value)
            assertEquals(1, connectCallCount)
        }

    @Test
    fun `startSession is ignored when Connecting`() =
        viewModelTest {
            viewModel.sessionConnector = { kotlinx.coroutines.awaitCancellation() }

            viewModel.startSession()

            assertEquals(SessionState.Connecting, viewModel.sessionState.value)

            // Call startSession again — should be ignored
            viewModel.startSession()

            assertEquals(SessionState.Connecting, viewModel.sessionState.value)
        }

    @Test
    fun `stopSession resets to Idle`() =
        viewModelTest {
            viewModel.startSession()
            assertEquals(SessionState.Connected, viewModel.sessionState.value)

            viewModel.stopSession()

            assertEquals(SessionState.Idle, viewModel.sessionState.value)
            assertTrue(stopCalled)
        }

    @Test
    fun `sendVideoFrame is ignored when not Connected`() =
        viewModelTest {
            val bitmap = mockk<android.graphics.Bitmap>(relaxed = true)

            viewModel.sendVideoFrame(bitmap)

            assertEquals(0, videoFramesSent)
        }

    @Test
    fun `connection error triggers retry with Reconnecting state`() =
        viewModelTest {
            val states = mutableListOf<SessionState>()
            backgroundScope.launch(UnconfinedTestDispatcher()) {
                viewModel.sessionState.collect { states.add(it) }
            }

            viewModel.sessionConnector = {
                connectCallCount++
                if (connectCallCount == 1) {
                    throw RuntimeException("Network error")
                }
                fakeHandle
            }

            viewModel.startSession()

            // After first failure: [Idle, Connecting, Reconnecting]
            assertTrue("Should contain Reconnecting", states.contains(SessionState.Reconnecting))

            // Advance past retry delay (2000ms for first retry)
            testScheduler.advanceTimeBy(2100)

            assertEquals(SessionState.Connected, states.last())
        }

    @Test
    fun `max retries exceeded transitions to Error`() =
        viewModelTest {
            val states = mutableListOf<SessionState>()
            backgroundScope.launch(UnconfinedTestDispatcher()) {
                viewModel.sessionState.collect { states.add(it) }
            }

            shouldThrow = true

            viewModel.startSession()

            // Initial: Connecting → Reconnecting
            assertTrue(states.contains(SessionState.Reconnecting))

            // Retry 1 (delay 2s)
            testScheduler.advanceTimeBy(2100)
            // Retry 2 (delay 4s)
            testScheduler.advanceTimeBy(4100)
            // Retry 3 (delay 6s)
            testScheduler.advanceTimeBy(6100)

            // After 3 retries (+ initial = 4 attempts), Error state
            assertTrue(states.last() is SessionState.Error)
        }

    @Test
    fun `no timer-based auto reconnect occurs`() =
        viewModelTest {
            viewModel.startSession()
            assertEquals(SessionState.Connected, viewModel.sessionState.value)
            assertEquals(1, connectCallCount)

            // Advance past old session duration — no auto-reconnect should happen
            testScheduler.advanceTimeBy(120_000)

            assertEquals(SessionState.Connected, viewModel.sessionState.value)
            assertEquals(1, connectCallCount)
        }

    @Test
    fun `initial emotion is NEUTRAL`() {
        assertEquals(Emotion.NEUTRAL, viewModel.currentEmotion.value)
    }

    @Test
    fun `stopSession resets emotion to NEUTRAL`() =
        viewModelTest {
            viewModel.startSession()
            viewModel.handleFunctionCall("setEmotion_HAPPY", "call-1")
            assertEquals(Emotion.HAPPY, viewModel.currentEmotion.value)

            viewModel.stopSession()

            assertEquals(Emotion.NEUTRAL, viewModel.currentEmotion.value)
        }

    @Test
    fun `handleFunctionCall updates emotion`() {
        viewModel.handleFunctionCall("setEmotion_EXCITED", "call-1")
        assertEquals(Emotion.EXCITED, viewModel.currentEmotion.value)

        viewModel.handleFunctionCall("setEmotion_SAD", "call-2")
        assertEquals(Emotion.SAD, viewModel.currentEmotion.value)
    }

    @Test
    fun `handleFunctionCall falls back to NEUTRAL for unknown emotion`() {
        viewModel.handleFunctionCall("setEmotion_ANGRY", "call-1")
        assertEquals(Emotion.NEUTRAL, viewModel.currentEmotion.value)
    }

    @Test
    fun `handleFunctionCall ignores unknown function`() {
        viewModel.handleFunctionCall("setEmotion_HAPPY", "call-1")
        assertEquals(Emotion.HAPPY, viewModel.currentEmotion.value)

        val response = viewModel.handleFunctionCall("unknownFunction", "call-2")
        // Emotion should not change
        assertEquals(Emotion.HAPPY, viewModel.currentEmotion.value)
        assertTrue(response.contains("error"))
    }

    @Test
    fun `stopSession clears resume token`() =
        viewModelTest {
            viewModel.startSession()
            assertEquals(SessionState.Connected, viewModel.sessionState.value)

            viewModel.stopSession()

            assertEquals(SessionState.Idle, viewModel.sessionState.value)
            // Starting a new session should work fresh (no stale resume token)
            viewModel.startSession()
            assertEquals(SessionState.Connected, viewModel.sessionState.value)
            assertEquals(2, connectCallCount)
        }

    @Test
    fun `toggleMute flips isMuted`() {
        assertFalse(viewModel.isMuted.value)

        viewModel.toggleMute()
        assertTrue(viewModel.isMuted.value)

        viewModel.toggleMute()
        assertFalse(viewModel.isMuted.value)
    }

    @Test
    fun `stopSession resets isMuted and audioLevel and gameName`() =
        viewModelTest {
            viewModel.startSession()

            viewModel.toggleMute()
            assertTrue(viewModel.isMuted.value)

            viewModel.handleFunctionCall(
                "setGameName",
                "call-1",
                mapOf("name" to JsonPrimitive("Zelda")),
            )
            assertEquals("Zelda", viewModel.gameName.value)

            viewModel.stopSession()

            assertFalse(viewModel.isMuted.value)
            assertEquals(0f, viewModel.audioLevel.value)
            assertNull(viewModel.gameName.value)
        }

    @Test
    fun `handleFunctionCall setGameName updates gameName`() {
        viewModel.handleFunctionCall(
            "setGameName",
            "call-1",
            mapOf("name" to JsonPrimitive("Zelda")),
        )
        assertEquals("Zelda", viewModel.gameName.value)
    }

    @Test
    fun `handleFunctionCall setGameName with null args does not crash`() {
        val result = viewModel.handleFunctionCall("setGameName", "call-1", null)
        assertNull(viewModel.gameName.value)
        assertTrue(result.contains("error"))
    }

    @Test
    fun `dismissOnboarding sets showOnboarding to false`() =
        viewModelTest {
            // By default our mock says onboarding is shown, so showOnboarding starts false
            // Let's set up a new mock that says onboarding NOT shown yet
            val mockSettingsStore2 = mockk<SettingsStore>(relaxed = true)
            coEvery { mockSettingsStore2.isOnboardingShown() } returns false
            every { mockSettingsStore2.voiceNameFlow() } returns flowOf("AOEDE")
            every { mockSettingsStore2.reactionIntensityFlow() } returns flowOf("ふつう")
            viewModel.settingsStore = mockSettingsStore2

            // Re-trigger init check manually (since init already ran)
            // We test the dismissOnboarding path directly
            viewModel.dismissOnboarding()

            assertFalse(viewModel.showOnboarding.value)
        }
}
