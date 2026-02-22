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

For release builds, create `keystore.properties` in the project root with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Both `keystore.properties` and `*.jks` are gitignored. If the file is absent, `assembleRelease` builds an unsigned APK (CI uses this path).

## Architecture

Single-screen app with one ViewModel. No navigation, no database, no repository layer.

**Data flow**: `CameraPreview` captures frames at 1FPS, downscales to 512px short side → `GamerViewModel.sendVideoFrame()` compresses to JPEG (quality 60) and sends via `LiveSession.sendVideoRealtime()` → Gemini responds with audio played back through `startAudioConversation(::handleFunctionCall)` which handles mic input, speaker output, and function calls.

**Session lifecycle** (`GamerViewModel`): The Gemini Live API has a hard 2-minute session limit. `GamerViewModel` runs a timer and proactively reconnects at 1:50 (see `SESSION_DURATION_MS = 110_000L`). On reconnect, the system prompt is re-sent so character is preserved, but conversation context is lost. On network errors, retries up to 3 times with linear backoff (`RETRY_BASE_DELAY_MS * retryCount`, starting at 2s).

**State machine** (`SessionState`): `Idle → Connecting → Connected → Reconnecting → Connected` (normal loop) or `→ Error` (after max retries). Video frames are only sent in `Connected` state; frames during other states are silently dropped.

**UI layout**: Full-screen camera preview with overlays. `StatusOverlay` (top-left, glass morphism) shows connection state. `AIFace` (bottom-center, above controls) shows when Connected. `GlassControlPanel` (bottom-anchored row) has status text + start/stop button with `WindowInsets.safeDrawing` support. All overlays use semi-transparent black backgrounds with subtle white borders.

**Emotion system**: Gemini calls parameterless functions like `setEmotion_HAPPY`, `setEmotion_SAD`, etc. via function calling to update the AI's facial expression. These are defined as `emotionFunctions` (one `FunctionDeclaration` per emotion, all with `parameters = emptyMap()`). The `handleFunctionCall()` method in `GamerViewModel` extracts the emotion name from the function name prefix (`setEmotion_`), updates `_currentEmotion: StateFlow<Emotion>`, and `AIFace` composable animates between states using spring physics. The face renders on a Canvas with neon-green eyes/eyebrows/mouth on a semi-transparent black circle with a neon glow halo, shown only when Connected. `AIFace` has a breathing animation (subtle scale oscillation via `rememberInfiniteTransition`) and a bounce effect on emotion changes (via `Animatable` spring). Seven emotions: NEUTRAL, HAPPY, EXCITED, SURPRISED, THINKING, WORRIED, SAD. Note: emotion functions are currently disabled (see "Tools are currently disabled" below).

**Haptic feedback**: `LaunchedEffect` monitors `sessionState` and `currentEmotion` changes. `hapticForSessionTransition()` returns CONFIRM on connection, REJECT on error. `hapticForEmotionChange()` returns TICK on any emotion change. Uses API 30+ haptic constants with fallback for older devices.

**Firebase AI Logic SDK specifics**: All Live API types require `@OptIn(PublicPreviewAPI::class)`. The `liveModel` is initialized lazily via `Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(...)`. Audio I/O is fully managed by `startAudioConversation()` / `stopAudioConversation()`. The voice is `Voice("AOEDE")`. Function calling types (`FunctionCallPart`, `FunctionResponsePart`, `JsonObject`) come from `kotlinx-serialization-json`, which must be an explicit dependency since Firebase AI exposes these types but doesn't transitively export the library.

**Tools are currently disabled** (commented out in `liveModel` config): Firebase AI Logic SDK serializes `FunctionDeclaration.parameters` as `parameters_json_schema`, which the Gemini Live API rejects (`"parameters_json_schema must not be set"`). This affects ALL tool types including `Tool.googleSearch()` and `Tool.functionDeclarations()`. Both firebase-ai 17.8.0 and 17.9.0 (BOM 34.9.0) have this issue. The Firebase docs state Live API tool support is "coming soon" (https://firebase.google.com/docs/ai-logic/live-api). The emotion function definitions (`emotionFunctions`) and handler (`handleFunctionCall`) are still in the code, ready to be re-enabled. To restore tools, uncomment the `tools = listOf(...)` line in `liveModel` initialization. The Gemini Live API itself does support function calling — the BrewingCoffe project (same author) works around this by using raw WebSocket (OkHttp) instead of the Firebase SDK, sending `parameters` directly in the correct format. firebase-ai is pinned to `17.8.0!!` (strict) to avoid BOM override; 17.9.0 breaks even without tools in some configurations.

**Build toolchain**: AGP 9.0.1 / Gradle 9.3.1 with built-in Kotlin (no `org.jetbrains.kotlin.android` plugin). Kotlin compilation is handled by AGP directly. The Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose` v2.2.10) is still applied separately. The Gemini model is `gemini-2.5-flash-native-audio-preview-12-2025` (preview — may need updating).

## Testing

Tests use `SessionHandle` interface + `sessionConnector` lambda to stub the Firebase Live API without mocking the SDK. Both `SessionHandle` (`@VisibleForTesting internal interface`) and `SessionState` sealed interface are defined in `GamerViewModel.kt`. Set `viewModel.sessionConnector` to a lambda returning a fake `SessionHandle` in tests. See `GamerViewModelTest.kt` for the pattern.

Test files: `GamerViewModelTest.kt` (session lifecycle, retries, emotion handling), `GamerScreenKtTest.kt` (haptic logic, state helpers), `AIFaceKtTest.kt` (emotion param mapping), `StatusOverlayKtTest.kt` (overlay state mapping), `CameraPreviewKtTest.kt` (frame throttle timing), `EmotionTest.kt` (enum parsing), `PermissionHelperTest.kt` (permission checks).

Several source functions are marked `@VisibleForTesting internal` to enable unit testing of otherwise private logic (e.g. `paramsFor()` in AIFace, `statusOverlayInfo()` in StatusOverlay, `shouldCaptureFrame()` / `downscaleBitmap()` in CameraPreview, `isSessionActive()` / `hapticForSessionTransition()` / `hapticForEmotionChange()` / `HapticType` in GamerScreen). Only use `testDebugUnitTest` — no release unit test variant exists.

Test dependencies: JUnit 4, MockK, kotlinx-coroutines-test. The `testOptions.unitTests.isReturnDefaultValues = true` flag is set so Android framework classes (like `Log`) return defaults in unit tests.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs on push/PR to `main`: ktlintCheck → testDebugUnitTest → assembleRelease. It generates a dummy `google-services.json` so the Google Services plugin doesn't fail.

## Key Constraints

- minSdk 26 (Android 8.0) / targetSdk 35 (Android 15) — landscape orientation only
- Session auto-reconnects at 1:50 — do not extend `SESSION_DURATION_MS` beyond 120_000
- CameraX dependency is alpha (`1.5.0-alpha06`) — may receive breaking API changes on update
- Frame rate is throttled in `SnapshotFrameAnalyzer.captureIntervalMs` — keep at 1000ms or higher
- System prompt is in Japanese (the AI character speaks Japanese)
- `app/google-services.json` must never be committed (contains API keys). Must be downloaded from Firebase Console for the correct package name (`com.example.aigamerfriend`) — do not manually edit the package name in the JSON
- The Google Cloud API key must have the Generative Language API enabled. Android application restrictions on the API key must include the release APK's SHA-1 fingerprint, or be set to "None" for testing
- AGP 9.0 uses built-in Kotlin — do not add `org.jetbrains.kotlin.android` plugin (it is incompatible with AGP 9.0's internal classes)
- `kotlinx-serialization-json` must remain an explicit dependency — Firebase AI SDK uses these types in its public API but does not export them transitively

## Conventions

- UI strings are hardcoded in Japanese (no string resources for now)
- StateFlow collected with `collectAsStateWithLifecycle` in Compose
- JVM toolchain 17
- ktlint enforced via `.editorconfig`: max line length 120, wildcard imports allowed, Composable function naming exempt
- Note: `ktlintCheck` only lints `.kts` files due to AGP 9 built-in Kotlin not registering source sets with the ktlint plugin. Source/test Kotlin files are not currently linted by CI.
