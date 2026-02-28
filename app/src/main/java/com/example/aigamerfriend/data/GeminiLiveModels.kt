package com.example.aigamerfriend.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// --- Client → Server messages ---

@Serializable
data class GeminiSetupMessage(
    val setup: Setup,
) {
    @Serializable
    data class Setup(
        val model: String,
        @SerialName("generation_config")
        val generationConfig: GenerationConfig,
        @SerialName("system_instruction")
        val systemInstruction: SystemInstruction? = null,
        val tools: List<Tool>? = null,
        @SerialName("contextWindowCompression")
        val contextWindowCompression: ContextWindowCompression? = null,
        @SerialName("sessionResumption")
        val sessionResumption: SessionResumption? = null,
    )

    @Serializable
    data class ContextWindowCompression(
        val slidingWindow: SlidingWindow,
    )

    @Serializable
    data class SlidingWindow(
        val targetTokens: Long? = null,
    )

    @Serializable
    data class SessionResumption(
        val handle: String? = null,
    )

    @Serializable
    data class GenerationConfig(
        @SerialName("response_modalities")
        val responseModalities: List<String>,
        @SerialName("speech_config")
        val speechConfig: SpeechConfig? = null,
    )

    @Serializable
    data class SpeechConfig(
        @SerialName("voice_config")
        val voiceConfig: VoiceConfig,
    )

    @Serializable
    data class VoiceConfig(
        @SerialName("prebuilt_voice_config")
        val prebuiltVoiceConfig: PrebuiltVoiceConfig,
    )

    @Serializable
    data class PrebuiltVoiceConfig(
        @SerialName("voice_name")
        val voiceName: String,
    )

    @Serializable
    data class SystemInstruction(
        val parts: List<Part>,
    )

    @Serializable
    data class Part(
        val text: String,
    )

    @Serializable
    data class Tool(
        @SerialName("functionDeclarations")
        val functionDeclarations: List<FunctionDeclaration>? = null,
        @SerialName("google_search")
        val googleSearch: GoogleSearch? = null,
    )

    @Serializable
    class GoogleSearch

    @Serializable
    data class FunctionDeclaration(
        val name: String,
        val description: String,
        val parameters: FunctionParameters? = null,
    )

    @Serializable
    data class FunctionParameters(
        val type: String,
        val properties: Map<String, PropertySchema>? = null,
        val required: List<String>? = null,
    )

    @Serializable
    data class PropertySchema(
        val type: String,
        val description: String? = null,
    )
}

@Serializable
data class GeminiRealtimeInputMessage(
    @SerialName("realtime_input")
    val realtimeInput: RealtimeInput,
) {
    @Serializable
    data class RealtimeInput(
        @SerialName("media_chunks")
        val mediaChunks: List<MediaChunk>,
    )

    @Serializable
    data class MediaChunk(
        @SerialName("mime_type")
        val mimeType: String,
        val data: String, // Base64-encoded
    )
}

@Serializable
data class GeminiToolResponseMessage(
    @SerialName("tool_response")
    val toolResponse: ToolResponse,
) {
    @Serializable
    data class ToolResponse(
        @SerialName("function_responses")
        val functionResponses: List<FunctionResponse>,
    )

    @Serializable
    data class FunctionResponse(
        val id: String,
        val name: String,
        val response: Map<String, JsonElement>,
    )
}

// --- Server → Client messages (parsed from JSON) ---

@Serializable
data class GeminiServerMessage(
    @SerialName("setupComplete")
    val setupComplete: SetupComplete? = null,
    @SerialName("serverContent")
    val serverContent: ServerContent? = null,
    @SerialName("toolCall")
    val toolCall: ToolCall? = null,
    @SerialName("sessionResumptionUpdate")
    val sessionResumptionUpdate: SessionResumptionUpdate? = null,
    @SerialName("goAway")
    val goAway: GoAway? = null,
) {
    @Serializable
    class SetupComplete

    @Serializable
    data class ServerContent(
        @SerialName("modelTurn")
        val modelTurn: ModelTurn? = null,
        val interrupted: Boolean? = null,
    )

    @Serializable
    data class ModelTurn(
        val parts: List<ServerPart>? = null,
    )

    @Serializable
    data class ServerPart(
        @SerialName("inlineData")
        val inlineData: InlineDataPart? = null,
        val text: String? = null,
    )

    @Serializable
    data class InlineDataPart(
        @SerialName("mimeType")
        val mimeType: String,
        val data: String, // Base64-encoded
    )

    @Serializable
    data class ToolCall(
        @SerialName("functionCalls")
        val functionCalls: List<FunctionCall>? = null,
    )

    @Serializable
    data class FunctionCall(
        val id: String,
        val name: String,
        val args: Map<String, JsonElement>? = null,
    )

    @Serializable
    data class SessionResumptionUpdate(
        val newHandle: String? = null,
        val resumable: Boolean? = null,
    )

    @Serializable
    data class GoAway(
        val timeLeft: String? = null,
    )
}
