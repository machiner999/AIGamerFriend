package com.example.aigamerfriend.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveModelsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun `setup message uses parameters not parameters_json_schema`() {
        val setup = GeminiSetupMessage(
            setup = GeminiSetupMessage.Setup(
                model = "models/test-model",
                generationConfig = GeminiSetupMessage.GenerationConfig(
                    responseModalities = listOf("AUDIO"),
                ),
                tools = listOf(
                    GeminiSetupMessage.Tool(
                        functionDeclarations = listOf(
                            GeminiSetupMessage.FunctionDeclaration(
                                name = "testFunc",
                                description = "A test function",
                                parameters = GeminiSetupMessage.FunctionParameters(
                                    type = "OBJECT",
                                    properties = mapOf(
                                        "arg1" to GeminiSetupMessage.PropertySchema(
                                            type = "STRING",
                                            description = "An argument",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(setup)
        assertTrue("Should contain 'parameters'", encoded.contains("\"parameters\""))
        assertFalse(
            "Should NOT contain 'parameters_json_schema'",
            encoded.contains("parameters_json_schema"),
        )
    }

    @Test
    fun `null parameters are omitted from JSON`() {
        val decl = GeminiSetupMessage.FunctionDeclaration(
            name = "setEmotion_HAPPY",
            description = "Set happy emotion",
            parameters = null,
        )

        val encoded = json.encodeToString(decl)
        assertFalse("Null parameters should be omitted", encoded.contains("parameters"))
    }

    @Test
    fun `google search tool serializes as empty object`() {
        val tool = GeminiSetupMessage.Tool(
            googleSearch = GeminiSetupMessage.GoogleSearch(),
        )

        val encoded = json.encodeToString(tool)
        assertTrue("Should contain google_search", encoded.contains("\"google_search\""))
        assertTrue("Should contain empty object", encoded.contains("{}"))
    }

    @Test
    fun `toolCall response is deserialized correctly`() {
        val serverJson = """
            {
                "toolCall": {
                    "functionCalls": [
                        {
                            "id": "call-123",
                            "name": "setEmotion_HAPPY",
                            "args": null
                        }
                    ]
                }
            }
        """.trimIndent()

        val msg = json.decodeFromString<GeminiServerMessage>(serverJson)
        assertNotNull(msg.toolCall)
        assertEquals(1, msg.toolCall!!.functionCalls!!.size)
        assertEquals("call-123", msg.toolCall.functionCalls!![0].id)
        assertEquals("setEmotion_HAPPY", msg.toolCall.functionCalls!![0].name)
    }

    @Test
    fun `setupComplete message is deserialized correctly`() {
        val serverJson = """{"setupComplete": {}}"""

        val msg = json.decodeFromString<GeminiServerMessage>(serverJson)
        assertNotNull(msg.setupComplete)
        assertNull(msg.serverContent)
        assertNull(msg.toolCall)
    }

    @Test
    fun `setup message with context window compression serializes correctly`() {
        val setup = GeminiSetupMessage(
            setup = GeminiSetupMessage.Setup(
                model = "models/test-model",
                generationConfig = GeminiSetupMessage.GenerationConfig(
                    responseModalities = listOf("AUDIO"),
                ),
                contextWindowCompression = GeminiSetupMessage.ContextWindowCompression(
                    slidingWindow = GeminiSetupMessage.SlidingWindow(),
                ),
                sessionResumption = GeminiSetupMessage.SessionResumption(),
            ),
        )

        val encoded = json.encodeToString(setup)
        assertTrue("Should contain contextWindowCompression", encoded.contains("\"contextWindowCompression\""))
        assertTrue("Should contain slidingWindow", encoded.contains("\"slidingWindow\""))
        assertTrue("Should contain sessionResumption", encoded.contains("\"sessionResumption\""))
        // targetTokens is null and should be omitted (encodeDefaults=false)
        assertFalse("Null targetTokens should be omitted", encoded.contains("targetTokens"))
        // handle is null and should be omitted
        assertFalse("Null handle should be omitted", encoded.contains("handle"))
    }

    @Test
    fun `setup message with session resumption handle serializes correctly`() {
        val setup = GeminiSetupMessage(
            setup = GeminiSetupMessage.Setup(
                model = "models/test-model",
                generationConfig = GeminiSetupMessage.GenerationConfig(
                    responseModalities = listOf("AUDIO"),
                ),
                sessionResumption = GeminiSetupMessage.SessionResumption(handle = "resume-token-abc"),
            ),
        )

        val encoded = json.encodeToString(setup)
        assertTrue("Should contain handle", encoded.contains("\"handle\""))
        assertTrue("Should contain token value", encoded.contains("resume-token-abc"))
    }

    @Test
    fun `sessionResumptionUpdate is deserialized correctly`() {
        val serverJson = """
            {
                "sessionResumptionUpdate": {
                    "newHandle": "token-xyz-123",
                    "resumable": true
                }
            }
        """.trimIndent()

        val msg = json.decodeFromString<GeminiServerMessage>(serverJson)
        assertNotNull(msg.sessionResumptionUpdate)
        assertEquals("token-xyz-123", msg.sessionResumptionUpdate!!.newHandle)
        assertEquals(true, msg.sessionResumptionUpdate!!.resumable)
    }

    @Test
    fun `goAway is deserialized correctly`() {
        val serverJson = """
            {
                "goAway": {
                    "timeLeft": "30s"
                }
            }
        """.trimIndent()

        val msg = json.decodeFromString<GeminiServerMessage>(serverJson)
        assertNotNull(msg.goAway)
        assertEquals("30s", msg.goAway!!.timeLeft)
    }

    @Test
    fun `serverContent with audio is deserialized correctly`() {
        val serverJson = """
            {
                "serverContent": {
                    "modelTurn": {
                        "parts": [
                            {
                                "inlineData": {
                                    "mimeType": "audio/pcm;rate=24000",
                                    "data": "AQID"
                                }
                            }
                        ]
                    }
                }
            }
        """.trimIndent()

        val msg = json.decodeFromString<GeminiServerMessage>(serverJson)
        assertNotNull(msg.serverContent)
        val parts = msg.serverContent!!.modelTurn!!.parts!!
        assertEquals(1, parts.size)
        assertEquals("audio/pcm;rate=24000", parts[0].inlineData!!.mimeType)
        assertEquals("AQID", parts[0].inlineData!!.data)
    }
}
