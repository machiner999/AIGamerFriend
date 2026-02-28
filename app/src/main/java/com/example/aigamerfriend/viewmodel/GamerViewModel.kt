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
import com.example.aigamerfriend.data.SettingsStore
import com.example.aigamerfriend.memoryDataStore
import com.example.aigamerfriend.settingsDataStore
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
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 2000L
        private const val JPEG_QUALITY = 60
        private const val CONNECT_TIMEOUT_MS = 15_000L // 15sec timeout for connect
        private const val FRAME_BUFFER_SIZE = 32_768 // 32KB — expected JPEG size after downscale
        private const val MAX_RECENT_FRAMES = 5
        private const val RESPONSE_DELAY_THRESHOLD_MS = 5000L
        private const val DELAY_CHECK_INTERVAL_MS = 1000L
    }

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _currentEmotion = MutableStateFlow(Emotion.NEUTRAL)
    val currentEmotion: StateFlow<Emotion> = _currentEmotion.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _gameName = MutableStateFlow<String?>(null)
    val gameName: StateFlow<String?> = _gameName.asStateFlow()

    private val _isResponseDelayed = MutableStateFlow(false)
    val isResponseDelayed: StateFlow<Boolean> = _isResponseDelayed.asStateFlow()

    private var audioManager: AudioManager? = null
    private var sessionHandle: SessionHandle? = null
    private var lastResumeToken: String? = null
    private var retryCount = 0

    private val recentFrames = ArrayDeque<ByteArray>(MAX_RECENT_FRAMES)

    @VisibleForTesting
    internal var memoryStore: MemoryStore = MemoryStore(application.memoryDataStore)

    @VisibleForTesting
    internal var settingsStore: SettingsStore = SettingsStore(application.settingsDataStore)
        set(value) {
            settingsLoadJob?.cancel()
            field = value
            loadSettings()
        }

    @VisibleForTesting
    internal var summarizer: (suspend (List<ByteArray>) -> String?)? = null

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    private val _voiceName = MutableStateFlow("AOEDE")
    val voiceName: StateFlow<String> = _voiceName.asStateFlow()

    private val _reactionIntensity = MutableStateFlow("ふつう")
    val reactionIntensity: StateFlow<String> = _reactionIntensity.asStateFlow()

    private val _autoStart = MutableStateFlow(false)
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private var settingsLoadJob: Job? = null

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val store = settingsStore
        settingsLoadJob = viewModelScope.launch {
            launch {
                try {
                    _showOnboarding.value = !store.isOnboardingShown()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to check onboarding status", e)
                }
            }
            launch {
                try {
                    store.voiceNameFlow().collect { _voiceName.value = it }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read voice name", e)
                }
            }
            launch {
                try {
                    store.reactionIntensityFlow().collect { _reactionIntensity.value = it }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read reaction intensity", e)
                }
            }
            launch {
                try {
                    store.autoStartFlow().collect { _autoStart.value = it }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read auto start", e)
                }
            }
        }
    }

    fun setVoiceName(name: String) {
        viewModelScope.launch { settingsStore.setVoiceName(name) }
    }

    fun setReactionIntensity(intensity: String) {
        viewModelScope.launch { settingsStore.setReactionIntensity(intensity) }
    }

    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoStart(enabled) }
    }

    fun clearMemory() {
        viewModelScope.launch { memoryStore.clear() }
    }

    fun dismissOnboarding() {
        _showOnboarding.value = false
        viewModelScope.launch {
            try {
                settingsStore.setOnboardingShown()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save onboarding status", e)
            }
        }
    }

    private val summaryModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(SUMMARY_MODEL_NAME)
    }

    private val toolDeclarations = Emotion.entries.map { emotion ->
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
    } + listOf(
        GeminiSetupMessage.FunctionDeclaration(
            name = "setGameName",
            description = "プレイしているゲームのタイトル名を設定する",
            parameters = GeminiSetupMessage.FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "name" to GeminiSetupMessage.PropertySchema(
                        type = "STRING",
                        description = "ゲームのタイトル名",
                    ),
                ),
                required = listOf("name"),
            ),
        ),
        GeminiSetupMessage.FunctionDeclaration(
            name = "stopSession",
            description = "セッションを終了する。ユーザーが「終わり」「やめる」「ストップ」「終了して」「もういい」などと言った時に呼び出す",
            parameters = null,
        ),
        GeminiSetupMessage.FunctionDeclaration(
            name = "toggleMute",
            description = "マイクのミュートを切り替える。ユーザーが「ミュート」「黙って」「静かにして」などと言った時に呼び出す",
            parameters = null,
        ),
    )

    private val liveTools = listOf(
        GeminiSetupMessage.Tool(functionDeclarations = toolDeclarations),
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

        ## ゲーム認識
        プレイしているゲームが分かったら setGameName 関数でゲーム名を設定しろ。ゲームが変わったら再度呼び出せ。

        ## 音声コマンド
        ユーザーが以下のような発言をしたら、対応する関数を呼び出せ：
        - 「終わり」「やめる」「ストップ」「終了して」「もういい」→ stopSession
        - 「ミュート」「黙って」「静かにして」→ toggleMute
        ゲームの文脈での「終わり」（例：「このステージ終わりだ」）には反応するな。
        """.trimIndent()

    private fun buildSystemPrompt(memorySummary: String?): String {
        val parts = mutableListOf(baseSystemPrompt)

        when (reactionIntensity.value) {
            "おとなしめ" -> parts.add("\n\n## リアクションの強さ\nリアクションは控えめにしろ。静かに見守る感じで。")
            "テンション高め" -> parts.add("\n\n## リアクションの強さ\nテンション高めで！大げさにリアクションしろ！")
        }

        if (memorySummary != null) {
            parts.add(
                "\n\n## これまでの記憶\n" +
                    "以下は過去のセッションの要約。同じリアクションを繰り返さず、自然に言及しろ。\n" +
                    memorySummary,
            )
        }

        return parts.joinToString("")
    }

    @VisibleForTesting
    internal var sessionConnector: (suspend () -> SessionHandle)? = null

    @VisibleForTesting
    internal fun handleFunctionCall(
        name: String,
        callId: String,
        args: Map<String, JsonElement>? = null,
    ): String {
        val prefix = "setEmotion_"
        return when {
            name.startsWith(prefix) -> {
                val emotionStr = name.removePrefix(prefix)
                _currentEmotion.value = Emotion.fromString(emotionStr)
                """{"success": true}"""
            }
            name == "setGameName" -> {
                val gameName = args?.get("name")?.let { element ->
                    (element as? JsonPrimitive)?.content
                }
                if (gameName != null) {
                    _gameName.value = gameName
                    """{"success": true}"""
                } else {
                    """{"error": "Missing name argument"}"""
                }
            }
            name == "stopSession" -> {
                viewModelScope.launch { stopSession() }
                """{"success": true}"""
            }
            name == "toggleMute" -> {
                toggleMute()
                """{"success": true}"""
            }
            else -> {
                """{"error": "Unknown function: $name"}"""
            }
        }
    }

    fun toggleMute() {
        val newValue = !_isMuted.value
        _isMuted.value = newValue
        audioManager?.isMuted = newValue
    }

    private suspend fun openSession(memorySummary: String?, resumeToken: String? = null): SessionHandle {
        sessionConnector?.let { return it() }

        val systemPrompt = buildSystemPrompt(memorySummary)

        val liveClient = GeminiLiveClient(
            apiKey = BuildConfig.GEMINI_API_KEY,
            modelName = MODEL_NAME,
            systemInstruction = systemPrompt,
            tools = liveTools,
            voiceName = voiceName.value,
            enableCompression = true,
            resumeHandle = resumeToken,
        )

        val audio = AudioManager()
        audio.isMuted = _isMuted.value
        audio.onAudioLevelUpdate = { level -> _audioLevel.value = level }
        this.audioManager = audio

        // Set up function call handler
        liveClient.onFunctionCall = { name, callId, args ->
            val result = handleFunctionCall(name, callId, args)
            val resultMap = if (result.contains("success")) {
                mapOf<String, JsonElement>("success" to JsonPrimitive(true))
            } else {
                mapOf<String, JsonElement>("error" to JsonPrimitive("Unknown function: $name"))
            }
            liveClient.sendToolResponse(callId, name, resultMap)
        }

        // Set up GoAway handler — server signals imminent disconnect (~10min)
        liveClient.onGoAway = {
            Log.d(TAG, "GoAway received, initiating reconnect with resume token")
            lastResumeToken = liveClient.latestResumeToken.value
            reconnect()
        }

        // Connect and wait for setupComplete
        liveClient.connect(viewModelScope)
        withTimeout(CONNECT_TIMEOUT_MS) {
            liveClient.connectionState.first { it == ConnectionState.CONNECTED }
        }

        // Start audio I/O
        audio.onAudioDataAvailable = { audioData ->
            liveClient.sendAudioChunk(audioData)
        }
        audio.start(viewModelScope)

        // Start audio playback loop
        val playbackJob = viewModelScope.launch(Dispatchers.IO) {
            for (audioBytes in liveClient.audioDataChannel) {
                audio.playAudio(audioBytes)
            }
        }

        // Monitor connection state for errors
        val monitorJob = viewModelScope.launch {
            liveClient.connectionState.collect { state ->
                if (state == ConnectionState.ERROR || state == ConnectionState.DISCONNECTED) {
                    if (_sessionState.value is SessionState.Connected) {
                        val token = liveClient.latestResumeToken.value
                        if (token != null) {
                            Log.d(TAG, "WebSocket disconnected, resuming with token")
                            lastResumeToken = token
                            reconnect()
                        } else {
                            Log.w(TAG, "WebSocket disconnected unexpectedly: $state")
                            handleConnectionError(RuntimeException("WebSocket disconnected"))
                        }
                    }
                }
            }
        }

        // Monitor response delay
        val delayMonitorJob = viewModelScope.launch {
            while (true) {
                delay(DELAY_CHECK_INTERVAL_MS)
                val lastTime = liveClient.lastMessageTimeMs.value
                _isResponseDelayed.value = _sessionState.value is SessionState.Connected &&
                    lastTime > 0L &&
                    System.currentTimeMillis() - lastTime >= RESPONSE_DELAY_THRESHOLD_MS
            }
        }

        return object : SessionHandle {
            override fun stopAudioConversation() {
                delayMonitorJob.cancel()
                _isResponseDelayed.value = false
                monitorJob.cancel()
                playbackJob.cancel()
                audio.shutdown()
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
        try {
            sessionHandle?.stopAudioConversation()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio conversation", e)
        }
        sessionHandle = null
        audioManager = null
        lastResumeToken = null
        retryCount = 0
        recentFrames.clear()
        _currentEmotion.value = Emotion.NEUTRAL
        _isMuted.value = false
        _audioLevel.value = 0f
        _gameName.value = null
        _isResponseDelayed.value = false
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

    private suspend fun connectSession(previousHandle: SessionHandle? = null) {
        if (previousHandle == null) {
            _sessionState.value = SessionState.Connecting
        }
        try {
            val memorySummary = try {
                memoryStore.getFormattedSummaries()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read memory, continuing without it", e)
                null
            }
            val handle = withTimeout(CONNECT_TIMEOUT_MS) { openSession(memorySummary, lastResumeToken) }
            // Stop previous session before setting Connected state to avoid race condition:
            // old monitorJob detecting DISCONNECTED while _sessionState is already Connected
            if (previousHandle != null) {
                try {
                    previousHandle.stopAudioConversation()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping previous session", e)
                }
            }
            sessionHandle = handle
            _sessionState.value = SessionState.Connected
            retryCount = 0
            Log.d(TAG, "Session connected" + if (memorySummary != null) " (with memory)" else "")
        } catch (e: CancellationException) {
            previousHandle?.safeStop()
            if (e is kotlinx.coroutines.TimeoutCancellationException) {
                Log.e(TAG, "Session connect timed out after ${CONNECT_TIMEOUT_MS}ms")
                handleConnectionError(RuntimeException("接続タイムアウト", e))
            } else {
                throw e
            }
        } catch (e: Exception) {
            previousHandle?.safeStop()
            Log.e(TAG, "Failed to connect session", e)
            handleConnectionError(e)
        }
    }

    private fun SessionHandle.safeStop() {
        try {
            stopAudioConversation()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping previous session", e)
        }
    }

    private fun reconnect() {
        val previousHandle = sessionHandle
        sessionHandle = null
        _sessionState.value = SessionState.Reconnecting

        viewModelScope.launch {
            connectSession(previousHandle)
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
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Connection failed after $MAX_RETRIES retries", e)
            }
            _sessionState.value = SessionState.Error("接続に失敗しました。通信環境を確認してください。")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}
