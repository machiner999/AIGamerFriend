package com.example.aigamerfriend.viewmodel

import com.example.aigamerfriend.model.Emotion
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.InlineData
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

            override suspend fun sendVideoRealtime(data: InlineData) {
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

        viewModel = GamerViewModel()
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
    fun `session timer triggers auto reconnect`() =
        viewModelTest {
            viewModel.startSession()
            assertEquals(SessionState.Connected, viewModel.sessionState.value)
            assertEquals(1, connectCallCount)

            // Advance past session duration (110_000ms)
            testScheduler.advanceTimeBy(110_001)

            // Should auto-reconnect and end up Connected again
            assertEquals(SessionState.Connected, viewModel.sessionState.value)
            assertEquals(2, connectCallCount)
        }

    @Test
    fun `initial emotion is NEUTRAL`() {
        assertEquals(Emotion.NEUTRAL, viewModel.currentEmotion.value)
    }

    @Test
    fun `stopSession resets emotion to NEUTRAL`() =
        viewModelTest {
            viewModel.startSession()
            viewModel.handleFunctionCall(
                FunctionCallPart("setEmotion", mapOf("emotion" to JsonPrimitive("HAPPY"))),
            )
            assertEquals(Emotion.HAPPY, viewModel.currentEmotion.value)

            viewModel.stopSession()

            assertEquals(Emotion.NEUTRAL, viewModel.currentEmotion.value)
        }

    @Test
    fun `handleFunctionCall updates emotion`() {
        viewModel.handleFunctionCall(
            FunctionCallPart("setEmotion", mapOf("emotion" to JsonPrimitive("EXCITED"))),
        )
        assertEquals(Emotion.EXCITED, viewModel.currentEmotion.value)

        viewModel.handleFunctionCall(
            FunctionCallPart("setEmotion", mapOf("emotion" to JsonPrimitive("SAD"))),
        )
        assertEquals(Emotion.SAD, viewModel.currentEmotion.value)
    }

    @Test
    fun `handleFunctionCall falls back to NEUTRAL for unknown emotion`() {
        viewModel.handleFunctionCall(
            FunctionCallPart("setEmotion", mapOf("emotion" to JsonPrimitive("ANGRY"))),
        )
        assertEquals(Emotion.NEUTRAL, viewModel.currentEmotion.value)
    }

    @Test
    fun `handleFunctionCall ignores unknown function`() {
        viewModel.handleFunctionCall(
            FunctionCallPart("setEmotion", mapOf("emotion" to JsonPrimitive("HAPPY"))),
        )
        assertEquals(Emotion.HAPPY, viewModel.currentEmotion.value)

        val response = viewModel.handleFunctionCall(
            FunctionCallPart("unknownFunction", mapOf("arg" to JsonPrimitive("value"))),
        )
        // Emotion should not change
        assertEquals(Emotion.HAPPY, viewModel.currentEmotion.value)
        assertEquals("unknownFunction", response.name)
    }
}
