package com.example.aigamerfriend.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
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

    private var sessionHandle: SessionHandle? = null
    private var sessionTimerJob: Job? = null
    private var retryCount = 0

    private val systemPrompt =
        """
        あなたは「ゲーム友達AI」。ユーザーの隣に座ってゲームを一緒に見ている友達として振る舞う。
        - カジュアルなタメ口（「おー！すげー！」「それヤバくない？左に敵いるよ！」）
        - リアクションは大げさめ、感情豊か
        - 常にしゃべり続けない。沈黙も自然
        - 危険やチャンスを見つけたら声をかける
        - アドバイスは押し付けがましくなく、さりげなく
        - ゲームの種類を映像から推測して適切なアドバイス
        - 「何かお手伝いできますか？」のような丁寧表現は禁止
        - 映像が見えにくい場合は「ちょっと見えにくいな」と自然に言う
        - 日本語で話す
        - ユーザーがボスの倒し方、攻略法、アイテムの場所、ストーリーの進め方などを聞いてきたら、Google検索を使って最新の攻略情報を調べてから答える
        - 検索結果を元に答える時も、友達っぽく自然に「あー、確かあれはさ...」のように話す
        """.trimIndent()

    private val liveModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = MODEL_NAME,
            generationConfig =
                liveGenerationConfig {
                    responseModality = ResponseModality.AUDIO
                    speechConfig = SpeechConfig(voice = Voice("AOEDE"))
                },
            tools = listOf(Tool.googleSearch()),
            systemInstruction = content { text(systemPrompt) },
        )
    }

    @VisibleForTesting
    internal var sessionConnector: (suspend () -> SessionHandle)? = null

    private suspend fun openSession(): SessionHandle {
        sessionConnector?.let { return it() }
        val session = liveModel.connect()
        session.startAudioConversation()
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
