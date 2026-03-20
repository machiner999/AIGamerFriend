package com.example.aigamerfriend.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiLiveClientTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty API key transitions to ERROR state`() = runTest {
        val client = createClient(apiKey = "")

        client.connect(this)

        assertEquals(ConnectionState.ERROR, client.connectionState.value)
    }

    @Test
    fun `initial state is DISCONNECTED`() {
        val client = createClient()

        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun `processServerMessage setupComplete transitions to CONNECTED`() {
        val client = createClient()

        client.processServerMessage("""{"setupComplete":{}}""")

        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
        assertTrue(client.lastMessageTimeMs.value > 0L)
    }

    @Test
    fun `processServerMessage toolCall invokes callback with args`() = runTest {
        val client = createClient()
        var capturedName: String? = null
        var capturedCallId: String? = null
        var capturedArgs = emptyMap<String, kotlinx.serialization.json.JsonElement>()

        client.setScopeForTest(this)
        client.onFunctionCall = { name, callId, args ->
            capturedName = name
            capturedCallId = callId
            capturedArgs = args ?: emptyMap()
        }

        client.processServerMessage(
            """
            {
              "toolCall": {
                "functionCalls": [
                  {
                    "id": "call-1",
                    "name": "setGameName",
                    "args": {
                      "name": "Zelda"
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        runCurrent()

        assertEquals("setGameName", capturedName)
        assertEquals("call-1", capturedCallId)
        assertEquals(JsonPrimitive("Zelda"), capturedArgs["name"])
    }

    @Test
    fun `processServerMessage sessionResumptionUpdate stores latest token`() {
        val client = createClient()

        client.processServerMessage(
            """
            {
              "sessionResumptionUpdate": {
                "newHandle": "resume-1",
                "resumable": true
              }
            }
            """.trimIndent(),
        )

        assertEquals("resume-1", client.latestResumeToken.value)
    }

    @Test
    fun `processServerMessage goAway invokes callback`() = runTest {
        val client = createClient()
        var goAwayCalled = false

        client.setScopeForTest(this)
        client.onGoAway = { goAwayCalled = true }

        client.processServerMessage("""{"goAway":{"timeLeft":"5s"}}""")
        runCurrent()

        assertTrue(goAwayCalled)
    }

    @Test
    fun `processServerMessage audio content is forwarded to audioDataChannel`() = runTest {
        val client = createClient()

        client.processServerMessage(
            """
            {
              "serverContent": {
                "modelTurn": {
                  "parts": [
                    {
                      "inlineData": {
                        "mimeType": "audio/pcm",
                        "data": "AQID"
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
        )

        val audioBytes = client.audioDataChannel.tryReceive().getOrNull()

        assertNotNull(audioBytes)
        assertArrayEquals(byteArrayOf(1, 2, 3), audioBytes)
    }

    @Test
    fun `sendVideoFrame is ignored when not connected`() {
        val client = createClient()

        client.sendVideoFrame(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `sendAudioChunk is ignored when not connected`() {
        val client = createClient()

        client.sendAudioChunk(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `latestResumeToken is initially null`() {
        val client = createClient()

        assertEquals(null, client.latestResumeToken.value)
    }

    @Test
    fun `handleSocketFailure sets state to ERROR`() {
        val client = createClient()

        client.handleSocketFailure(RuntimeException("boom"), null)

        assertEquals(ConnectionState.ERROR, client.connectionState.value)
    }

    @Test
    fun `handleSocketClosed after connected sets state to DISCONNECTED`() {
        val client = createClient()
        client.processServerMessage("""{"setupComplete":{}}""")

        client.handleSocketClosed(1000, "Normal closure")

        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun `disconnect sets state to DISCONNECTED`() {
        val client = createClient()

        client.disconnect()

        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    private fun createClient(apiKey: String = "test-key") =
        GeminiLiveClient(
            apiKey = apiKey,
            modelName = "test-model",
            systemInstruction = "test",
            tools = emptyList(),
        )
}
