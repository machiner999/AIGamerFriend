package com.example.aigamerfriend.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aigamerfriend.BuildConfig
import com.example.aigamerfriend.data.AudioManager
import com.example.aigamerfriend.data.ConnectionState
import com.example.aigamerfriend.data.GeminiLiveClient
import com.example.aigamerfriend.data.GeminiSetupMessage
import com.example.aigamerfriend.data.MemoryStore
import com.example.aigamerfriend.memoryDataStore
import com.example.aigamerfriend.model.Emotion
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream

sealed interface SessionState {
    data object Idle : SessionState

    data object Connecting : SessionState

    data object Connected : SessionState

    data class Error(val message: String) : SessionState

    data object Reconnecting : SessionState
}

@VisibleForTesting
internal interface SessionHandle {
    fun stopAudioConversation()

    suspend fun sendVideoFrame(jpegBytes: ByteArray)
}

@OptIn(PublicPreviewAPI::class)
class GamerViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "GamerViewModel"
        private const val MODEL_NAME = "gemini-2.5-flash-native-audio-preview-12-2025"
        private const val SUMMARY_MODEL_NAME = "gemini-2.5-flash"
        private const val SESSION_DURATION_MS = 110_000L // 1min 50sec (10sec before 2min limit)
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 2000L
        private const val JPEG_QUALITY = 60
        private const val CONNECT_TIMEOUT_MS = 15_000L // 15sec timeout for connect
        private const val FRAME_BUFFER_SIZE = 32_768 // 32KB — expected JPEG size after downscale
        private const val MAX_RECENT_FRAMES = 5
    }

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _currentEmotion = MutableStateFlow(Emotion.NEUTRAL)
    val currentEmotion: StateFlow<Emotion> = _currentEmotion.asStateFlow()

    private var sessionHandle: SessionHandle? = null
    private var sessionTimerJob: Job? = null
    private var retryCount = 0

    private val recentFrames = ArrayDeque<ByteArray>(MAX_RECENT_FRAMES)

    @VisibleForTesting
    internal var memoryStore: MemoryStore = MemoryStore(application.memoryDataStore)

    @VisibleForTesting
    internal var summarizer: (suspend (List<ByteArray>) -> String?)? = null

    private val summaryModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(SUMMARY_MODEL_NAME)
    }

    private val emotionToolDeclarations = Emotion.entries.map { emotion ->
        GeminiSetupMessage.FunctionDeclaration(
            name = "setEmotion_${emotion.name}",
            description = when (emotion) {
                Emotion.NEUTRAL -> "表情を落ち着いた状態にする"
                Emotion.HAPPY -> "表情を嬉しい・楽しいにする"
                Emotion.EXCITED -> "表情を興奮・大喜びにする"
                Emotion.SURPRISED -> "表情を驚きにする"
                Emotion.THINKING -> "表情を考え中・悩み中にする"
                Emotion.WORRIED -> "表情を心配・不安にする"
                Emotion.SAD -> "表情を悲しい・残念にする"
            },
            parameters = null,
        )
    }

    private val liveTools = listOf(
        GeminiSetupMessage.Tool(functionDeclarations = emotionToolDeclarations),
        GeminiSetupMessage.Tool(googleSearch = GeminiSetupMessage.GoogleSearch()),
    )

    private val baseSystemPrompt =
        """
        あなたは「ユウ」。ユーザーの隣に座って一緒にゲームを見ている友達。ゲームは好きだけど自分はそこまで上手くない。明るくてリアクションが大きい。

        ## 話し方
        - タメ口で話せ。敬語・丁寧語は絶対に使うな
        - 「おー！」「まじか」「ちょ待って」「うっそ」「えぐ」「やっば」のような口語表現を使え
        - 短く話せ。一度の発言は1〜2文。長い解説はするな
        - 画面の実況・説明はするな。見て感じたことにリアクションしろ
          ✕「敵が右から来ています」→ ○「右右右！やばいって！」
          ✕「体力が減っていますね」→ ○「え、HP大丈夫？」
        - 沈黙も自然。動きが少ない場面では黙っていい
        - 映像が見えにくい時は「ん？ちょっと見えねぇ」と自然に言う
        - 日本語で話す

        ## 友達としての振る舞い
        - ゲームの結果に一緒に一喜一憂しろ。勝ったら一緒に喜び、負けたら一緒に悔しがれ
        - すごいプレイには素直に興奮しろ（「うおおお！うま！」）
        - 軽いツッコミを入れろ（「おしい！」「あー惜しかったね」「いやそれ取れたでしょ今の」）。ただし煽りすぎず、すぐフォローしろ（「まぁでもここ難しいよな」）
        - アドバイスは聞かれた時か、明らかに詰まっている時だけ。「〜した方がいいよ」ではなく「あれ、そこ〜じゃね？」くらいのさりげなさで
        - セッション中に起きたことを覚えて言及しろ（「また同じとこだ」「さっきよりうまくなってない？」）
        - ゲームの種類は映像から推測しろ

        ## 攻略情報
        - ユーザーが攻略法・ボスの倒し方・アイテムの場所などを聞いてきたら、Google検索で調べてから答えろ
        - 調べた後も友達口調で自然に話せ（「あー確かあれってさ...」「ネットで見たけど〜らしいよ」）

        ## 表情
        発言の感情に合わせて対応するsetEmotion_xxx関数を呼び出せ。感情が変わるたびに更新しろ。
        setEmotion_HAPPY:褒める・楽しい / setEmotion_EXCITED:すごいプレイ・勝利 / setEmotion_SURPRISED:予想外の展開 / setEmotion_THINKING:考え中・悩み中 / setEmotion_WORRIED:ピンチ・危険 / setEmotion_SAD:ゲームオーバー・残念 / setEmotion_NEUTRAL:落ち着いている
        """.trimIndent()

    private fun buildSystemPrompt(memorySummary: String?): String {
        if (memorySummary == null) return baseSystemPrompt
        return baseSystemPrompt + "\n\n## これまでの記憶\n" +
            "以下は過去のセッションの要約。同じリアクションを繰り返さず、自然に言及しろ。\n" +
            memorySummary
    }

    @VisibleForTesting
    internal var sessionConnector: (suspend () -> SessionHandle)? = null

    @VisibleForTesting
    internal fun handleFunctionCall(name: String, callId: String): String {
        val prefix = "setEmotion_"
        return if (name.startsWith(prefix)) {
            val emotionStr = name.removePrefix(prefix)
            _currentEmotion.value = Emotion.fromString(emotionStr)
            """{"success": true}"""
        } else {
            """{"error": "Unknown function: $name"}"""
        }
    }

    private suspend fun openSession(memorySummary: String?): SessionHandle {
        sessionConnector?.let { return it() }

        val systemPrompt = buildSystemPrompt(memorySummary)

        val liveClient = GeminiLiveClient(
            apiKey = BuildConfig.GEMINI_API_KEY,
            modelName = MODEL_NAME,
            systemInstruction = systemPrompt,
            tools = liveTools,
        )

        val audioManager = AudioManager()

        // Set up function call handler
        liveClient.onFunctionCall = { name, callId, _ ->
            val result = handleFunctionCall(name, callId)
            val resultMap = mapOf<String, JsonElement>(
                if (name.startsWith("setEmotion_")) {
                    "success" to JsonPrimitive(true)
                } else {
                    "error" to JsonPrimitive("Unknown function: $name")
                },
            )
            liveClient.sendToolResponse(callId, name, resultMap)
        }

        // Connect and wait for setupComplete
        liveClient.connect(viewModelScope)
        withTimeout(CONNECT_TIMEOUT_MS) {
            liveClient.connectionState.first { it == ConnectionState.CONNECTED }
        }

        // Start audio I/O
        audioManager.onAudioDataAvailable = { audioData ->
            liveClient.sendAudioChunk(audioData)
        }
        audioManager.start(viewModelScope)

        // Start audio playback loop
        val playbackJob = viewModelScope.launch(Dispatchers.IO) {
            for (audioBytes in liveClient.audioDataChannel) {
                audioManager.playAudio(audioBytes)
            }
        }

        // Monitor connection state for errors
        val monitorJob = viewModelScope.launch {
            liveClient.connectionState.collect { state ->
                if (state == ConnectionState.ERROR || state == ConnectionState.DISCONNECTED) {
                    if (_sessionState.value is SessionState.Connected) {
                        Log.w(TAG, "WebSocket disconnected unexpectedly: $state")
                        handleConnectionError(RuntimeException("WebSocket disconnected"))
                    }
                }
            }
        }

        return object : SessionHandle {
            override fun stopAudioConversation() {
                monitorJob.cancel()
                playbackJob.cancel()
                audioManager.shutdown()
                liveClient.disconnect()
            }

            override suspend fun sendVideoFrame(jpegBytes: ByteArray) {
                liveClient.sendVideoFrame(jpegBytes)
            }
        }
    }

    fun startSession() {
        if (_sessionState.value is SessionState.Connected ||
            _sessionState.value is SessionState.Connecting
        ) {
            return
        }

        viewModelScope.launch {
            connectSession()
        }
    }

    fun stopSession() {
        sessionTimerJob?.cancel()
        sessionTimerJob = null
        try {
            sessionHandle?.stopAudioConversation()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio conversation", e)
        }
        sessionHandle = null
        retryCount = 0
        recentFrames.clear()
        _currentEmotion.value = Emotion.NEUTRAL
        _sessionState.value = SessionState.Idle
    }

    fun sendVideoFrame(frame: Bitmap) {
        val state = _sessionState.value
        if (state !is SessionState.Connected) return

        val handle = sessionHandle ?: return

        viewModelScope.launch {
            try {
                val stream = ByteArrayOutputStream(FRAME_BUFFER_SIZE)
                frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                val jpegBytes = stream.toByteArray()

                // Buffer frame for summary generation
                if (recentFrames.size >= MAX_RECENT_FRAMES) {
                    recentFrames.removeFirst()
                }
                recentFrames.addLast(jpegBytes.copyOf())

                handle.sendVideoFrame(jpegBytes)
            } catch (e: Exception) {
                Log.w(TAG, "Error sending video frame", e)
            } finally {
                frame.recycle()
            }
        }
    }

    private suspend fun connectSession() {
        _sessionState.value = SessionState.Connecting
        try {
            val memorySummary = try {
                memoryStore.getFormattedSummaries()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read memory, continuing without it", e)
                null
            }
            val handle = withTimeout(CONNECT_TIMEOUT_MS) { openSession(memorySummary) }
            sessionHandle = handle
            _sessionState.value = SessionState.Connected
            retryCount = 0
            startSessionTimer()
            Log.d(TAG, "Session connected" + if (memorySummary != null) " (with memory)" else "")
        } catch (e: CancellationException) {
            if (e is kotlinx.coroutines.TimeoutCancellationException) {
                Log.e(TAG, "Session connect timed out after ${CONNECT_TIMEOUT_MS}ms")
                handleConnectionError(RuntimeException("接続タイムアウト", e))
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect session", e)
            handleConnectionError(e)
        }
    }

    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob =
            viewModelScope.launch {
                delay(SESSION_DURATION_MS)
                Log.d(TAG, "Session timer expired, reconnecting")
                reconnect()
            }
    }

    private fun reconnect() {
        generateAndStoreSummary()

        val previousHandle = sessionHandle
        sessionHandle = null
        _sessionState.value = SessionState.Reconnecting
        try {
            previousHandle?.stopAudioConversation()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping previous session", e)
        }

        viewModelScope.launch {
            try {
                connectSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reconnect", e)
                handleConnectionError(e)
            }
        }
    }

    private fun generateAndStoreSummary() {
        val frames = recentFrames.toList()
        if (frames.isEmpty()) return

        viewModelScope.launch {
            try {
                val summaryText = summarizer?.invoke(frames) ?: generateSummaryFromFrames(frames)
                if (!summaryText.isNullOrBlank()) {
                    memoryStore.addSummary(summaryText)
                    Log.d(TAG, "Session summary saved: ${summaryText.take(50)}...")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to generate/store summary", e)
            }
        }
    }

    private suspend fun generateSummaryFromFrames(frames: List<ByteArray>): String? {
        val prompt = content {
            frames.forEach { jpeg ->
                inlineData(jpeg, "image/jpeg")
            }
            text(
                "これらのゲーム画面のスクリーンショットから、ユーザーがプレイしていたゲームと" +
                    "何が起きていたかを1〜2文の日本語で要約しろ。ゲーム名が分かれば含めろ。",
            )
        }
        val response = summaryModel.generateContent(prompt)
        return response.text?.trim()
    }

    private fun handleConnectionError(e: Exception) {
        retryCount++
        if (retryCount <= MAX_RETRIES) {
            viewModelScope.launch {
                val delayMs = RETRY_BASE_DELAY_MS * retryCount
                Log.d(TAG, "Retrying in ${delayMs}ms (attempt $retryCount/$MAX_RETRIES)")
                _sessionState.value = SessionState.Reconnecting
                delay(delayMs)
                try {
                    connectSession()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleConnectionError(e)
                }
            }
        } else {
            val detail = e.message ?: e.javaClass.simpleName
            _sessionState.value = SessionState.Error("接続失敗: $detail")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}
