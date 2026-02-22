package com.example.aigamerfriend.data

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiLiveClientTest {

    @Test
    fun `empty API key transitions to ERROR state`() = runTest {
        val client = GeminiLiveClient(
            apiKey = "",
            modelName = "test-model",
            systemInstruction = "test",
            tools = emptyList(),
        )

        client.connect(this)

        assertEquals(ConnectionState.ERROR, client.connectionState.value)
    }

    @Test
    fun `initial state is DISCONNECTED`() {
        val client = GeminiLiveClient(
            apiKey = "test-key",
            modelName = "test-model",
            systemInstruction = "test",
            tools = emptyList(),
        )

        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }

    @Test
    fun `sendVideoFrame is ignored when not connected`() {
        val client = GeminiLiveClient(
            apiKey = "test-key",
            modelName = "test-model",
            systemInstruction = "test",
            tools = emptyList(),
        )

        // Should not throw
        client.sendVideoFrame(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `sendAudioChunk is ignored when not connected`() {
        val client = GeminiLiveClient(
            apiKey = "test-key",
            modelName = "test-model",
            systemInstruction = "test",
            tools = emptyList(),
        )

        // Should not throw
        client.sendAudioChunk(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `disconnect sets state to DISCONNECTED`() = runTest {
        val client = GeminiLiveClient(
            apiKey = "test-key",
            modelName = "test-model",
            systemInstruction = "test",
            tools = emptyList(),
        )

        client.disconnect()

        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
    }
}
