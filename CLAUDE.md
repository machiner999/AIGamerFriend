# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Android app that watches game screens via the device's back camera and reacts like a casual gaming friend using voice. Uses Gemini Multimodal Live API through Firebase AI Logic SDK for real-time bidirectional audio+video streaming. Displays an animated face that reflects the AI's emotions via function calling.

## Build Commands

```bash
./gradlew assembleDebug          # Debug build
./gradlew assembleRelease        # Release build
./gradlew clean                  # Clean build artifacts
./gradlew ktlintCheck            # Lint check (ktlint via jlleitschuh plugin)
./gradlew ktlintFormat           # Lint auto-fix
./gradlew testDebugUnitTest      # Run all unit tests
```

Run a single test class:
```bash
./gradlew testDebugUnitTest --tests "com.example.aigamerfriend.viewmodel.GamerViewModelTest"
```

## Prerequisites

A real `app/google-services.json` from Firebase Console is required (the placeholder in the repo won't connect to Gemini). It is gitignored. CI generates a dummy one for build verification.

## Architecture

Single-screen app with one ViewModel. No navigation, no database, no repository layer.

**Data flow**: `CameraPreview` captures frames at 1FPS → `GamerViewModel.sendVideoFrame()` compresses to JPEG and sends via `LiveSession.sendVideoRealtime()` → Gemini responds with audio played back through `startAudioConversation(::handleFunctionCall)` which handles mic input, speaker output, and function calls.

**Session lifecycle** (`GamerViewModel`): The Gemini Live API has a hard 2-minute session limit. `GamerViewModel` runs a timer and proactively reconnects at 1:50 (see `SESSION_DURATION_MS = 110_000L`). On reconnect, the system prompt is re-sent so character is preserved, but conversation context is lost. On network errors, retries up to 3 times with linear backoff.

**State machine** (`SessionState`): `Idle → Connecting → Connected → Reconnecting → Connected` (normal loop) or `→ Error` (after max retries). Video frames are only sent in `Connected` state; frames during other states are silently dropped.

**Emotion system**: Gemini calls `setEmotion(emotion)` via function calling to update the AI's facial expression. The `handleFunctionCall()` method in `GamerViewModel` parses the emotion string, updates `_currentEmotion: StateFlow<Emotion>`, and `AIFace` composable animates between states using spring physics. The face renders on a Canvas with neon-green eyes/eyebrows/mouth on a semi-transparent black circle, shown only when Connected. Seven emotions: NEUTRAL, HAPPY, EXCITED, SURPRISED, THINKING, WORRIED, SAD.

**Firebase AI Logic SDK specifics**: All Live API types require `@OptIn(PublicPreviewAPI::class)`. The `liveModel` is initialized lazily via `Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(...)`. Audio I/O is fully managed by `startAudioConversation()` / `stopAudioConversation()`. The model has two tools: `Tool.googleSearch()` for game walkthrough queries (server-side, no handler needed) and `Tool.functionDeclarations(listOf(setEmotionFunction))` for emotion control (client-side, handled by `::handleFunctionCall`). Function calling types (`FunctionCallPart`, `FunctionResponsePart`, `JsonObject`) come from `kotlinx-serialization-json`, which must be an explicit dependency since Firebase AI exposes these types but doesn't transitively export the library.

**Build toolchain**: AGP 9.0.1 with built-in Kotlin (no `org.jetbrains.kotlin.android` plugin). Kotlin compilation is handled by AGP directly. The Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is still applied separately.

## Testing

Tests use `SessionHandle` interface + `sessionConnector` lambda to stub the Firebase Live API without mocking the SDK. Set `viewModel.sessionConnector` to a lambda returning a fake `SessionHandle` in tests. See `GamerViewModelTest.kt` for the pattern.

Test dependencies: JUnit 4, MockK, kotlinx-coroutines-test. The `testOptions.unitTests.isReturnDefaultValues = true` flag is set so Android framework classes (like `Log`) return defaults in unit tests.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs on push/PR to `main`: ktlintCheck → testDebugUnitTest → assembleRelease. It generates a dummy `google-services.json` so the Google Services plugin doesn't fail.

## Key Constraints

- Session auto-reconnects at 1:50 — do not extend `SESSION_DURATION_MS` beyond 120_000
- Frame rate is throttled in `SnapshotFrameAnalyzer.captureIntervalMs` — keep at 1000ms or higher
- System prompt is in Japanese (the AI character speaks Japanese)
- `app/google-services.json` must never be committed (contains API keys)
- AGP 9.0 uses built-in Kotlin — do not add `org.jetbrains.kotlin.android` plugin (it is incompatible with AGP 9.0's internal classes)
- `kotlinx-serialization-json` must remain an explicit dependency — Firebase AI SDK uses these types in its public API but does not export them transitively

## Conventions

- UI strings are hardcoded in Japanese (no string resources for now)
- StateFlow collected with `collectAsStateWithLifecycle` in Compose
- JVM toolchain 17
- ktlint enforced via `.editorconfig`: max line length 120, wildcard imports allowed, Composable function naming exempt
