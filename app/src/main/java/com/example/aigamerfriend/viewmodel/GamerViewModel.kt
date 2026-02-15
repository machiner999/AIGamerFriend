package com.example.aigamerfriend.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aigamerfriend.model.Emotion
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
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

    suspend fun sendVideoRealtime(data: InlineData)
}

@OptIn(PublicPreviewAPI::class)
class GamerViewModel : ViewModel() {
    companion object {
        private const val TAG = "GamerViewModel"
        private const val MODEL_NAME = "gemini-2.5-flash-native-audio-preview-12-2025"
        private const val SESSION_DURATION_MS = 110_000L // 1min 50sec (10sec before 2min limit)
        private const val MAX_RETRIES = 3
        private const val RETRY_BASE_DELAY_MS = 2000L
    }

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _currentEmotion = MutableStateFlow(Emotion.NEUTRAL)
    val currentEmotion: StateFlow<Emotion> = _currentEmotion.asStateFlow()

    private var sessionHandle: SessionHandle? = null
    private var sessionTimerJob: Job? = null
    private var retryCount = 0

    private val setEmotionFunction = FunctionDeclaration(
        name = "setEmotion",
        description = "AIの表情を変更する。発言内容や状況に合わせて適切な感情を設定する。",
        parameters = mapOf(
            "emotion" to Schema.enumeration(
                Emotion.entries.map { it.name },
                "設定する感情の種類",
            ),
        ),
    )

    private val systemPrompt =
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
        setEmotion関数を発言の感情に合わせて呼び出せ。感情が変わるたびに更新しろ。
        HAPPY:褒める・楽しい / EXCITED:すごいプレイ・勝利 / SURPRISED:予想外の展開 / THINKING:考え中・悩み中 / WORRIED:ピンチ・危険 / SAD:ゲームオーバー・残念 / NEUTRAL:落ち着いている
        """.trimIndent()

    private val liveModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = MODEL_NAME,
            generationConfig =
                liveGenerationConfig {
                    responseModality = ResponseModality.AUDIO
                    speechConfig = SpeechConfig(voice = Voice("AOEDE"))
                },
            tools = listOf(Tool.googleSearch(), Tool.functionDeclarations(listOf(setEmotionFunction))),
            systemInstruction = content { text(systemPrompt) },
        )
    }

    @VisibleForTesting
    internal var sessionConnector: (suspend () -> SessionHandle)? = null

    @VisibleForTesting
    internal fun handleFunctionCall(functionCall: FunctionCallPart): FunctionResponsePart {
        return when (functionCall.name) {
            "setEmotion" -> {
                val emotionStr = functionCall.args["emotion"]?.jsonPrimitive?.content ?: "NEUTRAL"
                _currentEmotion.value = Emotion.fromString(emotionStr)
                FunctionResponsePart(
                    functionCall.name,
                    JsonObject(mapOf("success" to JsonPrimitive(true))),
                )
            }
            else -> FunctionResponsePart(
                functionCall.name,
                JsonObject(mapOf("error" to JsonPrimitive("Unknown function: ${functionCall.name}"))),
            )
        }
    }

    private suspend fun openSession(): SessionHandle {
        sessionConnector?.let { return it() }
        val session = liveModel.connect()
        session.startAudioConversation(::handleFunctionCall)
        return object : SessionHandle {
            override fun stopAudioConversation() = session.stopAudioConversation()

            override suspend fun sendVideoRealtime(data: InlineData) = session.sendVideoRealtime(data)
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
        _currentEmotion.value = Emotion.NEUTRAL
        _sessionState.value = SessionState.Idle
    }

    fun sendVideoFrame(frame: Bitmap) {
        val state = _sessionState.value
        if (state !is SessionState.Connected) return

        val handle = sessionHandle ?: return

        viewModelScope.launch {
            try {
                val stream = ByteArrayOutputStream()
                frame.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val jpegBytes = stream.toByteArray()
                handle.sendVideoRealtime(InlineData(jpegBytes, "image/jpeg"))
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
            val handle = openSession()
            sessionHandle = handle
            _sessionState.value = SessionState.Connected
            retryCount = 0
            startSessionTimer()
            Log.d(TAG, "Session connected")
        } catch (e: CancellationException) {
            throw e
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

    private suspend fun reconnect() {
        _sessionState.value = SessionState.Reconnecting
        try {
            sessionHandle?.stopAudioConversation()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping previous session", e)
        }
        sessionHandle = null
        sessionTimerJob?.cancel()

        try {
            connectSession()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconnect", e)
            handleConnectionError(e)
        }
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
            _sessionState.value = SessionState.Error("接続に失敗しました。ネットワークを確認してください。")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}
