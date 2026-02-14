# AIGamerFriend 実装プラン

## Context

カメラでTVに映るゲーム画面を撮影し、カジュアルな友達のようにリアクション・アドバイスしてくれるAIゲーマー友達Androidアプリを新規作成する。Gemini Multimodal Live APIをFirebase AI Logic SDK経由で利用し、映像+音声のリアルタイム双方向ストリーミングを実現する。

**技術スタック**: Kotlin + Jetpack Compose + Firebase AI Logic SDK + CameraX

**重要な制約**:
- 映像+音声セッションは2分制限（Firebase AI Logic SDKではcontext window compression未サポート）
- 映像送信は2秒に1フレーム（0.5 FPS）— RPG想定で十分な頻度
- Firebase AI Logic SDKはセッション再開(session resumption)未サポート
- 入力音声: 16-bit PCM, mono, 16kHz / 出力音声: 24kHz

---

## プロジェクト構成

```
AIGamerFriend/
├── app/
│   ├── build.gradle.kts
│   ├── google-services.json          # Firebase設定（手動追加）
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/aigamerfriend/
│           ├── AIGamerFriendApp.kt
│           ├── MainActivity.kt
│           ├── ui/
│           │   ├── theme/  (Color.kt, Theme.kt, Type.kt)
│           │   ├── screen/GamerScreen.kt
│           │   └── component/
│           │       ├── CameraPreview.kt
│           │       └── StatusOverlay.kt
│           ├── viewmodel/GamerViewModel.kt
│           └── util/PermissionHelper.kt
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 実装ステップ

### Step 1: プロジェクト作成・Gradle設定 ✅

Android Studioの「Empty Compose Activity」テンプレートでプロジェクト作成し、以下を設定:

- `settings.gradle.kts` — リポジトリ設定、`rootProject.name = "AIGamerFriend"`
- `build.gradle.kts`(プロジェクト) — AGP 8.7.3, Kotlin 2.1.0, google-services 4.4.2 プラグイン
- `app/build.gradle.kts` — 以下の主要依存関係:
  - `com.google.firebase:firebase-bom:34.9.0` + `firebase-ai`
  - `androidx.compose:compose-bom:2025.01.01` + material3
  - `androidx.camera:camera-*:1.5.0-alpha06` (core, camera2, lifecycle, view)
  - `androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0`
  - `kotlinx-coroutines-android:1.9.0`
  - minSdk=26, targetSdk=35, compileSdk=35, JVM target 17

### Step 2: AndroidManifest + Application クラス ✅

- パーミッション宣言: `CAMERA`, `RECORD_AUDIO`, `INTERNET`
- `<uses-feature android:name="android.hardware.camera" android:required="true" />`
- Activity: `screenOrientation="portrait"`
- `AIGamerFriendApp.kt` — 最小限のApplicationクラス

### Step 3: テーマ設定（ダークテーマ） ✅

- ゲーミングコンテキスト（暗い部屋でTV視聴）に合わせたMaterial 3ダークテーマ
- `Color.kt`, `Theme.kt`, `Type.kt`

### Step 4: CameraPreview コンポーネント ✅

`ui/component/CameraPreview.kt`:
- `AndroidView`で`PreviewView`をラップしカメラフィードを表示
- CameraXの`ImageAnalysis`(STRATEGY_KEEP_ONLY_LATEST)で2秒間隔のフレームキャプチャ
- `DEFAULT_BACK_CAMERA`使用（TVに向ける用途）
- `onFrameCaptured(Bitmap)`コールバックでViewModelにフレームを渡す
- `SnapshotFrameAnalyzer`内部クラスで2秒間隔スロットリング

### Step 5: GamerViewModel（コアロジック） ✅

`viewmodel/GamerViewModel.kt` — アプリの最重要ファイル:

**状態管理**:
```kotlin
sealed interface SessionState {
    data object Idle : SessionState
    data object Connecting : SessionState
    data object Connected : SessionState
    data class Error(val message: String) : SessionState
    data object Reconnecting : SessionState
}
```

**LiveModel初期化**:
- `Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(...)`
- モデル: `gemini-2.5-flash-native-audio-preview-12-2025`
- `responseModality = ResponseModality.AUDIO`
- `speechConfig = SpeechConfig(voice = Voice("AOEDE"))` — 暖かみのある声

**セッション管理**:
- `startSession()`: `liveModel.connect()` → `startAudioConversation()` → タイマー開始
- `stopSession()`: `stopAudioConversation()` → state=Idle
- `sendVideoFrame(Bitmap)`: JPEG 80%圧縮 → `session.sendVideoRealtime(InlineData(...))`

**2分制限対策 — プロアクティブ再接続**:
- 1分50秒で自動再接続（2分制限の10秒前）
- 再接続中はstate=Reconnecting、フレームはドロップ
- 新しいセッションでsystem promptは再送信されるのでキャラクターは維持
- コンテキストは失われるが、映像から再度状況把握

**エラーハンドリング**:
- ネットワークエラー: 最大3回リトライ（バックオフ付き）
- 予期しない切断: 自動再接続試行

**システムプロンプト（キャラクター設定）**:
```
あなたは「ゲーム友達AI」。ユーザーの隣に座ってゲームを一緒に見ている友達として振る舞う。
- カジュアルなタメ口（「おー！すげー！」「それヤバくない？左に敵いるよ！」）
- リアクションは大げさめ、感情豊か
- 常にしゃべり続けない。沈黙も自然
- 危険やチャンスを見つけたら声をかける
- アドバイスは押し付けがましくなく、さりげなく
- ゲームの種類を映像から推測して適切なアドバイス
- 「何かお手伝いできますか？」のような丁寧表現は禁止
```

### Step 6: GamerScreen（メイン画面） ✅

`ui/screen/GamerScreen.kt` — 単一画面のMVP:

```
+-----------------------------------+
|                                    |
|      カメラプレビュー (70%)         |
|   [StatusOverlay: "LIVE" etc.]    |
|                                    |
+-----------------------------------+
|   状態テキスト                      |
|   「タップしてゲーム友達を呼ぼう」    |
|                                    |
|      [ 開始 / 終了 ボタン ]         |
+-----------------------------------+
```

- パーミッションチェック → 未許可なら説明表示
- `DisposableEffect`でアプリ離脱時にセッション停止
- `SessionState`に応じたUI更新

### Step 7: StatusOverlay ✅

`ui/component/StatusOverlay.kt`:
- Connected: 緑ドット + "LIVE"
- Reconnecting: 黄ドット + "再接続中"
- Error: 赤ドット + "エラー"
- Idle: 表示なし

### Step 8: MainActivity ✅

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIGamerFriendTheme(darkTheme = true) { GamerScreen() }
        }
    }
}
```

---

## 現在のステータス

**全Step実装完了** — `./gradlew assembleDebug` ビルド成功済み（2025-02-11）

残作業:
- [ ] Firebase Consoleでプロジェクト作成し、本物の`google-services.json`を配置
- [ ] 実機でのパーミッション・カメラプレビュー・API接続・音声応答の動作確認

実装時の差分:
- プランではフレームキャプチャ間隔が「2秒(0.5FPS)」だったが、実装では「1秒(1FPS)」に変更
- `adaptive-icon`ベースの最小ランチャーアイコン(`drawable/ic_launcher.xml`)を追加
- `kotlinOptions`（非推奨）→ `kotlin { jvmToolchain(17) }` に変更

---

## Firebase セットアップ（コード実装前に必要）

1. Firebase Consoleで新規プロジェクト「AIGamerFriend」作成
2. Androidアプリ追加（パッケージ名: `com.example.aigamerfriend`）
3. `google-services.json`をダウンロードし`app/`に配置
4. Firebase ConsoleでAI Logic → Gemini Developer API有効化
5. Gemini API利用規約に同意

---

## エッジケース対応

| ケース | 対応 |
|--------|------|
| パーミッション拒否 | 説明メッセージ表示、アプリ設定への誘導 |
| カメラ使用不可 | エラーメッセージ表示 |
| ネットワーク切断 | 3回リトライ後にエラー表示 |
| アプリバックグラウンド | DisposableEffectでセッション停止 |
| 映像が暗い/見にくい | system promptで「見えにくいな」と自然に言うよう指示済み |

---

## 検証方法

1. **ビルド確認**: `./gradlew assembleDebug` が成功すること ✅
2. **パーミッション**: カメラ・マイク権限の許可/拒否フローが正常動作
3. **カメラプレビュー**: 背面カメラの映像がリアルタイムで表示される
4. **API接続**: 「開始」タップでGemini Live APIに接続、ステータスが"LIVE"に変わる
5. **音声応答**: AIがカメラ映像を見てカジュアルな日本語で音声リアクションする
6. **映像送信**: フレームが1FPSで送信され、AIが画面内容に言及する
7. **再接続**: 1分50秒後に自動再接続が発生し、セッションが継続する
8. **終了**: 「終了」タップでセッションが正常に停止する
