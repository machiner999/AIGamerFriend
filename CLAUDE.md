# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 概要

デバイスの背面カメラでゲーム画面を見て、カジュアルなゲーム友達として音声でリアクションするAndroidアプリ。OkHttp WebSocketでGemini Multimodal Live APIに直接接続し、リアルタイム双方向の音声+映像ストリーミングを行う。Function Calling（感情関数 + Google検索 + ゲーム名検出）を通じてAIの感情を反映するアニメーション顔を表示する。Firebase AI Logic SDKは要約生成（non-live `GenerativeModel`）にのみ使用。ミュート機能、音声レベルインジケーター、初回オンボーディング、ゲーム名表示、設定画面（声・リアクション強度・記憶クリア）を搭載。

## ビルドコマンド

```bash
./gradlew assembleDebug          # デバッグビルド
./gradlew assembleRelease        # リリースビルド
./gradlew clean                  # ビルド成果物のクリーン
./gradlew ktlintCheck            # Lintチェック（jlleitschuhプラグイン経由のktlint）
./gradlew ktlintFormat           # Lint自動修正
./gradlew testDebugUnitTest      # 全ユニットテスト実行
```

単一テストクラスの実行:
```bash
./gradlew testDebugUnitTest --tests "com.example.aigamerfriend.viewmodel.GamerViewModelTest"
```

## 前提条件

Firebase Consoleから取得した実際の `app/google-services.json` が必要（リポジトリ内のプレースホルダーではGeminiに接続できない）。gitignore済み。CIではビルド検証用にダミーを生成する。

リリースビルドには、プロジェクトルートに `keystore.properties`（`storeFile`、`storePassword`、`keyAlias`、`keyPassword`）を作成する。`keystore.properties` と `*.jks` はどちらもgitignore済み。ファイルがなければ `assembleRelease` は署名なしAPKをビルドする（CIはこのパスを使用）。

Gemini Live APIに接続するには、`local.properties` に `GEMINI_API_KEY=your_api_key` を設定する。このファイルはデフォルトで `.gitignore` 済み。CIでは空キーが設定される（ビルドは通るが接続はしない）。

## アーキテクチャ

単一画面・単一ViewModelのアプリ。ナビゲーション、データベース、リポジトリ層なし（DataStoreはセッション記憶とアプリ設定に使用）。

**データフロー**: `CameraPreview` が1FPSでフレームをキャプチャし、短辺512pxにダウンスケール → `GamerViewModel.sendVideoFrame()` がJPEG（quality 60）に圧縮 → `GeminiLiveClient.sendVideoFrame()` がBase64エンコードして `realtimeInput.mediaChunks` でWebSocket送信 → Geminiが音声で応答（`serverContent.modelTurn.parts[].inlineData`）→ `AudioManager` がPCMデータをスピーカーで再生。マイク入力は `AudioManager` が16kHz PCMでキャプチャし `GeminiLiveClient.sendAudioChunk()` で送信。Function Call（感情変更、Google検索、ゲーム名検出）は `toolCall` メッセージで受信し、`GamerViewModel.handleFunctionCall()` で処理後 `toolResponse` を返送。

**セッションライフサイクル** (`GamerViewModel`): コンテキストウィンドウ圧縮（`contextWindowCompression: { slidingWindow: {} }`）を有効にすることで、従来の2分セッション制限を撤廃。WebSocket接続自体は~10分で切れるため、セッション再開（`sessionResumption`）機能を併用する。サーバーは切断前に**GoAway**メッセージを送信し、`GeminiLiveClient.onGoAway`コールバックで`reconnect()`を呼び出す。再接続時は`lastResumeToken`を使って文脈を維持したまま透明に再接続する。WebSocketが予期せず切断された場合も、resumeトークンがあればreconnect、なければ`handleConnectionError()`にフォールバック。再接続はConnect-Before-Disconnectパターン: 新セッション接続完了後、`SessionState.Connected` を設定する**前に**旧セッションを切断する（`reconnect()` → `connectSession(previousHandle)` → `openSession()` 成功 → `previousHandle.stopAudioConversation()` → `_sessionState = Connected`）。この順序は旧WebSocketの切断コールバックが新セッションのエラーハンドリングを誤発火させる競合状態を防ぐために重要。ネットワークエラー時は線形バックオフで最大3回リトライ（`RETRY_BASE_DELAY_MS * retryCount`、2秒から開始）。

**記憶システム** (`MemoryStore` + `GamerViewModel`): resumeトークンが使えない場合（初回接続、トークン期限切れ2時間）のフォールバックとして機能する。`generateAndStoreSummary()`は存在するが、通常のreconnectからは呼ばれない（コンテキストはサーバー側で維持される）。要約はDataStore PreferencesにJSON配列として保存（最大10件、各200文字で切り詰め）。セッション接続時、保存済み要約をシステムプロンプトの `## これまでの記憶` セクションに追加する。記憶関連の操作はすべて非致命的 — 失敗時はログに記録して静かにスキップする。`MemoryStore` は `@VisibleForTesting internal var memoryStore` で注入可能、要約生成は `@VisibleForTesting internal var summarizer` ラムダで注入可能。

**ステートマシン（2層構造）**: ViewModel層の `SessionState`（`Idle → Connecting → Connected → Reconnecting → Connected` または `→ Error`）とWebSocket層の `ConnectionState`（`DISCONNECTED → CONNECTING → CONNECTED` または `→ ERROR`）がある。`GamerViewModel` は `SessionState` をUIに公開し、`GeminiLiveClient` の `ConnectionState` を内部で監視して `SessionState` に変換する。映像フレームは `SessionState.Connected` でのみ送信され、他の状態中のフレームは静かに破棄される。

**UIレイアウト**: フルスクリーンカメラプレビューにオーバーレイを重ねる構成。`StatusOverlay`（左上、グラスモーフィズム）が接続状態を表示し、その下にゲーム名ラベル（NeonBlue pill）を `AnimatedVisibility` で表示。`AIFace`（下部中央、コントロールの上）がConnected時に表示。`GlassControlPanel`（下部固定の横並び行）にステータステキスト＋音声レベルインジケーター＋ミュートボタン＋開始/停止ボタン＋設定ボタンを配置し、`WindowInsets.safeDrawing` に対応。全オーバーレイは半透明黒背景に控えめな白ボーダー。初回起動時は `OnboardingOverlay`（フルスクリーン半透明黒 + グラスモーフィズムカード）が表示され、タップで閉じる。設定は `ModalBottomSheet` で声の種類・リアクション強度・記憶クリアを提供。

**感情システム + ゲーム名検出**: Geminiがパラメータなし関数 `setEmotion_HAPPY`、`setEmotion_SAD` 等をFunction Callingで呼び出し、AIの表情を更新する。加えて `setGameName(name: String)` でプレイ中のゲーム名を検出・表示する。`toolDeclarations`（感情ごとに1つの `FunctionDeclaration` + `setGameName`）として定義。`GamerViewModel` の `handleFunctionCall(name, callId, args)` が関数名から処理を分岐し、感情名を抽出して `_currentEmotion` を更新、またはゲーム名を `_gameName` に設定後、`sendToolResponse` で結果を返送。`AIFace` コンポーザブルがスプリング物理でステート間をアニメーションする。Canvasにネオングリーンの目/眉/口を半透明黒円の上に描画し、ネオングローのハロー付き。Connected時のみ表示。`AIFace` は呼吸アニメーション（`rememberInfiniteTransition` による微妙なスケール振動）と感情変化時のバウンスエフェクト（`Animatable` スプリング）を持つ。7つの感情: NEUTRAL, HAPPY, EXCITED, SURPRISED, THINKING, WORRIED, SAD。

**触覚フィードバック**: `LaunchedEffect` が `sessionState` と `currentEmotion` の変化を監視。`hapticForSessionTransition()` は接続時にCONFIRM、エラー時にREJECTを返す。`hapticForEmotionChange()` は感情変化時にTICKを返す。API 30+の触覚定数を使用し、古いデバイスにはフォールバック。

**WebSocket通信層**（`data/` パッケージ）: Firebase AI Logic SDKの `parameters_json_schema` シリアライズ問題を回避するため、Live APIセッションはOkHttp WebSocketで直接接続する。3つのクラスで構成:
- `GeminiLiveModels.kt` — `@Serializable` データクラス群（`GeminiSetupMessage`、`GeminiRealtimeInputMessage`、`GeminiServerMessage`、`GeminiToolResponseMessage`）。セットアップ、音声/映像送信、サーバー応答、ツール応答のJSON構造を定義。
- `GeminiLiveClient.kt` — OkHttp WebSocketクライアント。`wss://generativelanguage.googleapis.com/ws/...v1alpha...BidiGenerateContent?key=` に接続。`connect()`/`disconnect()`/`sendVideoFrame()`/`sendAudioChunk()`/`sendToolResponse()` を公開。`enableCompression`（コンテキストウィンドウ圧縮）と`resumeHandle`（セッション再開トークン）パラメータをサポート。`onFunctionCall` コールバックでFunction Callを通知。`onGoAway` コールバックでGoAway受信を通知。`latestResumeToken: StateFlow<String?>` でサーバーから受信した最新のresumeトークンを公開。`audioDataChannel: Channel<ByteArray>` で受信音声を流す。JSONシリアライズは `encodeDefaults = false` — `@Serializable` データクラスにデフォルト値を付けるとJSONに出力されないため、API必須フィールドにはデフォルト値を使わないこと。
- `AudioManager.kt` — マイク録音（`VOICE_COMMUNICATION`ソース、16kHz mono PCM 16bit、チャンク2048B、`Dispatchers.IO`）とスピーカー再生（24kHz mono PCM 16bit、バッファ4倍）。`AcousticEchoCanceler`（AEC）、AGC、NoiseSuppressorをハードウェアレベルで有効化。`isMuted` フラグでマイク送信を抑制（録音自体は継続）。`onAudioLevelUpdate` コールバックでRMS音声レベル（0.0-1.0）を通知。`gainFactor`（デフォルト5.0f）でPCM 16bitサンプルをソフトウェア増幅し、クリッピング対応済み。増幅はAGC/NoiseSuppressor/AECの後段で適用される。
- `SettingsStore.kt` — DataStore Preferencesによるアプリ設定永続化（オンボーディングフラグ、声の種類、リアクション強度）。

`GamerViewModel.openSession()` がこれらを組み立て、`SessionHandle` インターフェース（`stopAudioConversation()` + `sendVideoFrame(ByteArray)`）で抽象化して返す。テストでは `viewModel.sessionConnector` ラムダに偽の `SessionHandle` を注入することで、WebSocket/AudioManagerを一切使わずにセッションライフサイクルをテストできる。

Firebase AI Logic SDKは要約生成用の non-live `GenerativeModel`（`gemini-2.5-flash`）にのみ使用。`@OptIn(PublicPreviewAPI::class)` は要約モデル用に残る。`kotlinx-serialization-json` はWebSocketプロトコルモデルとFirebase AI SDKの両方で必要。

**ツールはOkHttp WebSocket直接接続で有効**: 感情関数（`setEmotion_*`、`parameters = null`）、ゲーム名検出（`setGameName`、`parameters` = OBJECT with `name: STRING`）、Google検索（`"google_search": {}`）を `tools` リストに格納し、セットアップメッセージで送信。firebase-aiはBOM管理（要約モデル用のみ使用、Live APIはOkHttp直接接続のためピン不要）。

**Firebase AI SDK復帰メモ**: Firebase AI SDKが `parameters_json_schema` 問題を修正した場合、`GeminiLiveClient`/`AudioManager`/`GeminiLiveModels`を削除し、`openSession()`でFirebase `liveModel`に戻すことで復帰可能。`SessionHandle`インターフェースを`sendVideoRealtime(InlineData)`に戻し、`handleFunctionCall`を`FunctionCallPart`/`FunctionResponsePart`型に戻す。テストの`fakeHandle`も合わせて更新。

**ビルドツールチェーン**: AGP 9.0.1 / Gradle 9.3.1、Kotlin内蔵（`org.jetbrains.kotlin.android` プラグインなし）。KotlinコンパイルはAGPが直接処理する。Composeコンパイラプラグイン（`org.jetbrains.kotlin.plugin.compose` v2.2.10）とKotlin Serializationプラグイン（`org.jetbrains.kotlin.plugin.serialization` v2.2.10）は別途適用。Geminiモデルは `gemini-2.5-flash-native-audio-preview-12-2025`（プレビュー版 — 更新が必要になる可能性あり）。APIキーは `local.properties` の `GEMINI_API_KEY` から `BuildConfig.GEMINI_API_KEY` に注入。リリースビルドは `isMinifyEnabled = true` + `isShrinkResources = true` でR8難読化・リソース圧縮を適用。`proguard-rules.pro` にkotlinx-serialization（`data/**` パッケージのシリアライザ保持）、OkHttp、Firebaseのkeepルールあり。新しい `@Serializable` クラスを `data/` パッケージに追加した場合は既存ルールでカバーされるが、別パッケージに追加する場合はkeepルールの追加が必要。

## テスト

テストは `SessionHandle` インターフェース + `sessionConnector` ラムダでWebSocketクライアントをスタブ化する。`GamerViewModel.openSession()` は最初に `sessionConnector?.let { return it() }` をチェックするため、テストでは `viewModel.sessionConnector = { fakeHandle }` を設定するだけで `GeminiLiveClient`/`AudioManager` を完全にバイパスできる。`SessionHandle`（`stopAudioConversation()` + `sendVideoFrame(ByteArray)`）と `SessionState` sealed interfaceはどちらも `GamerViewModel.kt` に定義。パターンは `GamerViewModelTest.kt` を参照。

テストファイル: `GamerViewModelTest.kt`（セッションライフサイクル、リトライ、感情処理、resumeトークン、ミュート、ゲーム名、オンボーディング）、`GeminiLiveModelsTest.kt`（シリアライズ検証 — `parameters` vs `parameters_json_schema`、圧縮・セッション再開・GoAway、デシリアライズ）、`GeminiLiveClientTest.kt`（接続状態遷移、空APIキー、resumeトークン初期値）、`GamerScreenKtTest.kt`（触覚ロジック、状態ヘルパー）、`AIFaceKtTest.kt`（感情パラメータマッピング）、`StatusOverlayKtTest.kt`（オーバーレイ状態マッピング）、`CameraPreviewKtTest.kt`（フレームスロットルタイミング）、`EmotionTest.kt`（enumパース）、`PermissionHelperTest.kt`（パーミッションチェック）、`MemoryStoreTest.kt`（追加/取得/クリア/容量制限/切り詰め）、`SettingsStoreTest.kt`（オンボーディングフラグ、声・リアクション強度の読み書き）。

いくつかのソース関数は `@VisibleForTesting internal` マークされており、本来privateなロジックのユニットテストを可能にしている（例: AIFaceの `paramsFor()`、StatusOverlayの `statusOverlayInfo()`、CameraPreviewの `shouldCaptureFrame()` / `downscaleBitmap()`、GamerScreenの `isSessionActive()` / `hapticForSessionTransition()` / `hapticForEmotionChange()` / `HapticType`）。`testDebugUnitTest` のみ使用 — releaseユニットテストバリアントは存在しない。

テスト依存: JUnit 4、MockK、kotlinx-coroutines-test。`testOptions.unitTests.isReturnDefaultValues = true` フラグが設定されており、Androidフレームワーククラス（`Log` 等）がユニットテストでデフォルト値を返す。

## CI

GitHub Actions（`.github/workflows/ci.yml`）が `main` へのpush/PRで実行: ktlintCheck → testDebugUnitTest → assembleRelease。Google Servicesプラグインが失敗しないよう、ダミーの `google-services.json` を生成する。

## セキュリティ

- `android:allowBackup="false"` — ADB backup経由のデータ抽出を防止
- `network_security_config.xml` で `cleartextTrafficPermitted="false"` — 全通信HTTPS強制
- `GeminiLiveClient.kt` のセンシティブなログ出力（セットアップメッセージ全文、サーバーメッセージ、resumeトークン、例外スタックトレース）は `if (BuildConfig.DEBUG)` でガード済み。新しいログ追加時は同様にガードすること
- ユーザー向けエラーメッセージに例外の詳細（`e.message`）を含めないこと。`handleConnectionError()` パターン参照: 技術詳細はDEBUGログに出力し、UIには `"接続に失敗しました。通信環境を確認してください。"` のような汎用メッセージを表示

## 主な制約

- minSdk 26 (Android 8.0) / compileSdk 36 / targetSdk 35 (Android 15) — 横画面固定
- コンテキストウィンドウ圧縮により2分制限は撤廃。WebSocket切断（~10分）はGoAway + sessionResumptionで透明に再接続
- CameraX 1.5.1（stable） — `setTargetResolution` は非推奨だが動作する
- フレームレートは `SnapshotFrameAnalyzer.captureIntervalMs` でスロットル — 1000ms以上を維持
- システムプロンプトは日本語（AIキャラクターは日本語で話す）
- `app/google-services.json` は絶対にコミットしないこと（APIキーを含む）。正しいパッケージ名（`com.example.aigamerfriend`）のFirebase Consoleからダウンロード必須 — JSON内のパッケージ名を手動編集しないこと
- Google Cloud APIキーにはGenerative Language APIの有効化が必要。APIキーのAndroidアプリケーション制限にリリースAPKのSHA-1フィンガープリントを含めるか、テスト用に「なし」に設定
- AGP 9.0は内蔵Kotlinを使用 — `org.jetbrains.kotlin.android` プラグインを追加しないこと（AGP 9.0の内部クラスと非互換）
- `kotlinx-serialization-json` は明示的な依存として残す必要がある — WebSocketプロトコルモデル（`GeminiLiveModels.kt`）とFirebase AI SDKの両方で使用

## 規約

- Claude Codeの応答は常に日本語で行うこと
- UI文字列は日本語でハードコード（現時点ではstring resourcesなし）
- ComposeではStateFlowを `collectAsStateWithLifecycle` で収集
- JVMツールチェーン 17
- ktlintは `.editorconfig` で強制: 最大行長120、ワイルドカードインポート許可、Composable関数の命名規則を免除
- 注意: AGP 9の内蔵Kotlinがktlintプラグインにソースセットを登録しないため、`ktlintCheck` は `.kts` ファイルのみリントする。ソース/テストのKotlinファイルは現在CIでリントされていない
