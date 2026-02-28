package com.example.aigamerfriend.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okio.ByteString
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

class GeminiLiveClient(
    private val apiKey: String,
    private val modelName: String,
    private val systemInstruction: String,
    private val tools: List<GeminiSetupMessage.Tool>,
    private val voiceName: String = "AOEDE",
    private val enableCompression: Boolean = true,
    private val resumeHandle: String? = null,
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val BASE_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastMessageTimeMs = MutableStateFlow(0L)
    val lastMessageTimeMs: StateFlow<Long> = _lastMessageTimeMs.asStateFlow()

    val audioDataChannel = Channel<ByteArray>(Channel.UNLIMITED)

    var onFunctionCall: ((name: String, callId: String, args: Map<String, JsonElement>?) -> Unit)? = null
    var onGoAway: (() -> Unit)? = null

    private val _latestResumeToken = MutableStateFlow<String?>(null)
    val latestResumeToken: StateFlow<String?> = _latestResumeToken.asStateFlow()

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var scope: CoroutineScope? = null

    fun connect(scope: CoroutineScope) {
        if (apiKey.isBlank()) {
            Log.e(TAG, "API key is empty")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        this.scope = scope
        _connectionState.value = ConnectionState.CONNECTING

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val url = "$BASE_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message} (response: ${response?.code} ${response?.message})", t)
                _connectionState.value = ConnectionState.ERROR
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        })
    }

    private fun sendSetupMessage() {
        val compression = if (enableCompression) {
            GeminiSetupMessage.ContextWindowCompression(
                slidingWindow = GeminiSetupMessage.SlidingWindow(),
            )
        } else {
            null
        }

        val sessionResumption = if (enableCompression) {
            GeminiSetupMessage.SessionResumption(handle = resumeHandle)
        } else {
            null
        }

        val setup = GeminiSetupMessage(
            setup = GeminiSetupMessage.Setup(
                model = "models/$modelName",
                generationConfig = GeminiSetupMessage.GenerationConfig(
                    responseModalities = listOf("AUDIO"),
                    speechConfig = GeminiSetupMessage.SpeechConfig(
                        voiceConfig = GeminiSetupMessage.VoiceConfig(
                            prebuiltVoiceConfig = GeminiSetupMessage.PrebuiltVoiceConfig(
                                voiceName = voiceName,
                            ),
                        ),
                    ),
                ),
                systemInstruction = GeminiSetupMessage.SystemInstruction(
                    parts = listOf(GeminiSetupMessage.Part(text = systemInstruction)),
                ),
                tools = tools.ifEmpty { null },
                contextWindowCompression = compression,
                sessionResumption = sessionResumption,
            ),
        )

        val message = json.encodeToString(setup)
        Log.d(TAG, "Setup message: $message")
        webSocket?.send(message)
        Log.d(TAG, "Setup message sent")
    }

    private fun handleMessage(text: String) {
        try {
            _lastMessageTimeMs.value = System.currentTimeMillis()

            // Log first 500 chars to see what server sends
            Log.d(TAG, "Server message: ${text.take(500)}")

            val msg = json.decodeFromString<GeminiServerMessage>(text)

            when {
                msg.setupComplete != null -> {
                    Log.d(TAG, "Setup complete")
                    _connectionState.value = ConnectionState.CONNECTED
                }

                msg.toolCall != null -> {
                    msg.toolCall.functionCalls?.forEach { call ->
                        scope?.launch(Dispatchers.Main) {
                            onFunctionCall?.invoke(call.name, call.id, call.args)
                        }
                    }
                }

                msg.sessionResumptionUpdate != null -> {
                    val update = msg.sessionResumptionUpdate
                    Log.d(TAG, "Session resumption update: handle=${update.newHandle?.take(20)}, resumable=${update.resumable}")
                    if (update.newHandle != null) {
                        _latestResumeToken.value = update.newHandle
                    }
                }

                msg.goAway != null -> {
                    Log.d(TAG, "GoAway received: timeLeft=${msg.goAway.timeLeft}")
                    scope?.launch(Dispatchers.Main) {
                        onGoAway?.invoke()
                    }
                }

                msg.serverContent != null -> {
                    msg.serverContent.modelTurn?.parts?.forEach { part ->
                        part.inlineData?.let { inlineData ->
                            if (inlineData.mimeType.startsWith("audio/")) {
                                val audioBytes = Base64.decode(inlineData.data, Base64.NO_WRAP)
                                audioDataChannel.trySend(audioBytes)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing server message", e)
        }
    }

    fun sendAudioChunk(audioData: ByteArray) {
        if (_connectionState.value != ConnectionState.CONNECTED) return

        val base64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val message = GeminiRealtimeInputMessage(
            realtimeInput = GeminiRealtimeInputMessage.RealtimeInput(
                mediaChunks = listOf(
                    GeminiRealtimeInputMessage.MediaChunk(
                        mimeType = "audio/pcm;rate=16000",
                        data = base64,
                    ),
                ),
            ),
        )

        val sent = webSocket?.send(json.encodeToString(message)) ?: false
        Log.d(TAG, "Sent audio chunk: ${audioData.size} bytes, success=$sent")
    }

    fun sendVideoFrame(jpegBytes: ByteArray) {
        if (_connectionState.value != ConnectionState.CONNECTED) return

        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val message = GeminiRealtimeInputMessage(
            realtimeInput = GeminiRealtimeInputMessage.RealtimeInput(
                mediaChunks = listOf(
                    GeminiRealtimeInputMessage.MediaChunk(
                        mimeType = "image/jpeg",
                        data = base64,
                    ),
                ),
            ),
        )

        val sent = webSocket?.send(json.encodeToString(message)) ?: false
        Log.d(TAG, "Sent video frame: ${jpegBytes.size} bytes, success=$sent")
    }

    fun sendToolResponse(callId: String, name: String, result: Map<String, JsonElement>) {
        val message = GeminiToolResponseMessage(
            toolResponse = GeminiToolResponseMessage.ToolResponse(
                functionResponses = listOf(
                    GeminiToolResponseMessage.FunctionResponse(
                        id = callId,
                        name = name,
                        response = result,
                    ),
                ),
            ),
        )

        val jsonStr = json.encodeToString(message)
        val sent = webSocket?.send(jsonStr) ?: false
        Log.d(TAG, "Sent tool response: callId=$callId, name=$name, success=$sent")
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        scope = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
