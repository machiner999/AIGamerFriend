# AIGamerFriend

カメラでTVに映るゲーム画面を撮影し、カジュアルな友達のようにリアクション・アドバイスしてくれるAIゲーマー友達Androidアプリ。

## 技術スタック

- **言語**: Kotlin
- **UI**: Jetpack Compose + Material 3 (ダークテーマ)
- **AI**: Firebase AI Logic SDK (`firebase-ai`) → Gemini Multimodal Live API
- **カメラ**: CameraX 1.5.0-alpha06
- **ビルド**: Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0
- **最小SDK**: 26 / **ターゲットSDK**: 35

## プロジェクト構成

```
app/src/main/java/com/example/aigamerfriend/
├── AIGamerFriendApp.kt          # Applicationクラス
├── MainActivity.kt              # エントリポイント
├── ui/
│   ├── theme/ (Color, Theme, Type)  # ゲーミング向けダークテーマ
│   ├── screen/GamerScreen.kt       # メイン画面（カメラ70% + コントロール30%）
│   └── component/
│       ├── CameraPreview.kt        # CameraX + 1FPSフレームキャプチャ
│       └── StatusOverlay.kt        # LIVE/再接続中/エラー インジケータ
├── viewmodel/GamerViewModel.kt     # コアロジック（セッション管理・映像送信・再接続）
└── util/PermissionHelper.kt        # CAMERA + RECORD_AUDIO パーミッション
```

## 重要な制約

- Gemini Live APIセッションは**2分制限** → 1分50秒で自動再接続
- 映像送信は**1FPS**（`SnapshotFrameAnalyzer`でスロットリング）
- Firebase AI Logic SDKはセッション再開(session resumption)未サポート
- `@OptIn(PublicPreviewAPI::class)` が必要（Firebase AI Live API）

## ビルド・実行

```bash
./gradlew assembleDebug
```

**事前準備**: Firebase Consoleでプロジェクト作成し、`app/google-services.json`を配置すること（`.gitignore`済み）。

## コーディング規約

- 日本語コメントは最小限（コード自体が説明的であること）
- システムプロンプトは日本語（ユーザーとの会話言語）
- Compose UI → `collectAsStateWithLifecycle` でStateFlow収集
