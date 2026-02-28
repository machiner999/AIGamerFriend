# AI Gamer Friend

Android端末の背面カメラでゲーム画面を映すと、カジュアルなゲーム仲間のようにリアルタイムで音声リアクションしてくれるAIアプリ。OkHttp WebSocketでGemini Multimodal Live APIに直接接続し、双方向の音声+映像ストリーミングで動作します。

## 機能

- 背面カメラでゲーム画面を1FPSでキャプチャし、Geminiに送信
- Geminiがゲーム状況を認識し、日本語の音声でリアクション
- AIの感情を反映するアニメーション顔表示（7種類: NEUTRAL, HAPPY, EXCITED, SURPRISED, THINKING, WORRIED, SAD）
- Function Callingによるゲーム名自動検出・表示
- Google検索による攻略情報の検索・回答
- 音声コマンドによるハンズフリー操作（セッション終了・ミュート切替）
- アプリ起動時の自動セッション開始オプション
- コンテキストウィンドウ圧縮による無制限セッション（2分制限を撤廃）
- セッション再開トークンによるWebSocket切断時の透明な再接続（GoAway対応）
- セッション間の会話記憶（要約生成・フォールバック引き継ぎ）
- ミュート機能・音声レベルインジケーター
- マイク音声のソフトウェアゲイン増幅（デフォルト2倍）
- 設定画面（声の種類・リアクション強度・自動スタート・記憶クリア）
- 初回オンボーディング画面
- 触覚フィードバック（接続/エラー/感情変化時）

## 必要環境

- Android 8.0 (API 26) 以上
- JDK 17
- Gemini API キー（Google AI Studio から取得）
- Firebase プロジェクト（要約生成用）

## セットアップ

1. リポジトリをクローン:
   ```bash
   git clone https://github.com/<your-username>/AIGamerFriend.git
   cd AIGamerFriend
   ```

2. `local.properties` に Gemini API キーを設定:
   ```properties
   GEMINI_API_KEY=your_api_key
   ```
   > Google Cloud Console で Generative Language API を有効化し、API キーを取得してください。

3. [Firebase Console](https://console.firebase.google.com/) からプロジェクトを作成し、`google-services.json` をダウンロードして `app/` に配置:
   ```
   app/google-services.json
   ```
   > パッケージ名 `com.example.aigamerfriend` で登録してください。このファイルは `.gitignore` に含まれています。

4. （任意）リリース用の署名キーストアを設定:
   ```bash
   keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias release
   ```
   プロジェクトルートに `keystore.properties` を作成:
   ```properties
   storeFile=../release.jks
   storePassword=<your-store-password>
   keyAlias=release
   keyPassword=<your-key-password>
   ```
   > `keystore.properties` と `*.jks` は `.gitignore` に含まれています。ファイルがなければ署名なしAPKがビルドされます。

5. ビルド:
   ```bash
   ./gradlew assembleDebug
   ```

## ビルドコマンド

```bash
./gradlew assembleDebug          # デバッグビルド
./gradlew assembleRelease        # リリースビルド
./gradlew clean                  # ビルド成果物の削除
./gradlew ktlintCheck            # リントチェック
./gradlew ktlintFormat           # リント自動修正
./gradlew testDebugUnitTest      # ユニットテスト実行
```

## アーキテクチャ

シングルスクリーン・シングルViewModel構成。ナビゲーション、データベース、リポジトリ層はなし（DataStoreはセッション記憶とアプリ設定に使用）。

### データフロー

```
CameraPreview (1FPS, 短辺512px)
  → GamerViewModel.sendVideoFrame() (JPEG quality 60)
    → GeminiLiveClient.sendVideoFrame() (Base64, WebSocket送信)
      → Gemini音声レスポンス → AudioManager (PCM 24kHz再生)

マイク入力 (16kHz PCM) → ソフトウェアゲイン増幅 → GeminiLiveClient.sendAudioChunk()

Function Call (感情/ゲーム名/検索/音声コマンド) → GamerViewModel.handleFunctionCall() → toolResponse返送
```

### セッション管理

コンテキストウィンドウ圧縮（`contextWindowCompression: { slidingWindow: {} }`）を有効にすることで、従来の2分セッション制限を撤廃しています。WebSocket接続自体は約10分で切れるため、セッション再開（`sessionResumption`）機能を併用し、サーバーからのGoAwayメッセージを受信して切断前に能動的に再接続します。再接続時はresumeトークンを使って文脈を維持したまま透明に再接続します。resumeトークンが使えない場合（初回接続、トークン期限切れ）は、MemoryStoreの要約記憶がフォールバックとして機能します。ネットワークエラー時は最大3回までリニアバックオフでリトライします。

### 状態遷移

```
SessionState: Idle → Connecting → Connected ←→ Reconnecting (GoAway/切断時)
                                             → Error (リトライ上限超過)

ConnectionState (WebSocket層): DISCONNECTED → CONNECTING → CONNECTED / ERROR
```

## プロジェクト構成

```
app/src/main/java/com/example/aigamerfriend/
├── AIGamerFriendApp.kt              # Application クラス
├── MainActivity.kt                  # メインActivity (Compose)
├── viewmodel/
│   └── GamerViewModel.kt           # セッション管理・映像送信・Function Call処理
├── data/
│   ├── GeminiLiveClient.kt         # OkHttp WebSocketクライアント
│   ├── GeminiLiveModels.kt         # WebSocketプロトコル用データクラス群
│   ├── AudioManager.kt             # マイク録音・スピーカー再生・ゲイン増幅
│   ├── MemoryStore.kt              # セッション要約の永続化 (DataStore)
│   └── SettingsStore.kt            # アプリ設定の永続化 (DataStore)
├── model/
│   └── Emotion.kt                  # 感情enum (7種類)
├── ui/
│   ├── screen/
│   │   └── GamerScreen.kt          # メイン画面 (カメラ + オーバーレイ + コントロール)
│   ├── component/
│   │   ├── AIFace.kt               # 感情アニメーション顔 (Canvas描画)
│   │   ├── CameraPreview.kt        # CameraX連携・フレームキャプチャ
│   │   └── StatusOverlay.kt        # 接続状態インジケータ
│   └── theme/                      # Material 3 ダークテーマ
└── util/
    └── PermissionHelper.kt         # カメラ・マイク権限
```

## 技術スタック

| カテゴリ | ライブラリ |
|---------|-----------|
| AI (Live) | OkHttp WebSocket → Gemini Multimodal Live API |
| AI (要約) | Firebase AI Logic SDK (`gemini-2.5-flash`) |
| UI | Jetpack Compose + Material 3 |
| カメラ | CameraX |
| データ永続化 | DataStore Preferences |
| シリアライズ | kotlinx-serialization-json |
| 非同期 | Kotlin Coroutines |
| ビルド | AGP 9.0.1 / Gradle 9.3.1 |
| リント | ktlint |
| CI | GitHub Actions |
| テスト | JUnit 4 / MockK / kotlinx-coroutines-test |

## ライセンス

MIT
