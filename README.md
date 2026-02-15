# AI Gamer Friend

Android端末の背面カメラでゲーム画面を映すと、カジュアルなゲーム仲間のようにリアルタイムで音声リアクションしてくれるAIアプリ。Gemini Multimodal Live API (Firebase AI Logic SDK) を使った双方向の音声+映像ストリーミングで動作します。

## 機能

- 背面カメラでゲーム画面を1FPSでキャプチャし、Geminiに送信
- Geminiがゲーム状況を認識し、日本語の音声でリアクション
- Google Search Grounding による攻略情報の検索・回答
- 2分のセッション制限に対する自動再接続 (1分50秒で再接続)

## 必要環境

- Android 8.0 (API 26) 以上
- JDK 17
- Firebase プロジェクト (Gemini API 有効化済み)

## セットアップ

1. リポジトリをクローン:
   ```bash
   git clone https://github.com/<your-username>/AIGamerFriend.git
   cd AIGamerFriend
   ```

2. [Firebase Console](https://console.firebase.google.com/) からプロジェクトを作成し、`google-services.json` をダウンロードして `app/` に配置:
   ```
   app/google-services.json
   ```
   > このファイルは `.gitignore` に含まれているため、リポジトリにはコミットされません。

3. ビルド:
   ```bash
   ./gradlew assembleRelease
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

シングルスクリーン・シングルViewModel構成。ナビゲーション、データベース、リポジトリ層はなし。

```
CameraPreview (1FPS)
  → GamerViewModel.sendVideoFrame() (JPEG圧縮)
    → LiveSession.sendVideoRealtime() (Gemini Live API)
      → 音声レスポンス (startAudioConversation)
```

### セッション管理

Gemini Live API は1セッション最大2分の制限があるため、`GamerViewModel` が1分50秒でプロアクティブに再接続します。ネットワークエラー時は最大3回までリニアバックオフでリトライします。

### 状態遷移

```
Idle → Connecting → Connected → Reconnecting → Connected (ループ)
                                              → Error (リトライ上限超過)
```

## プロジェクト構成

```
app/src/main/java/com/example/aigamerfriend/
├── AIGamerFriendApp.kt          # Application クラス
├── MainActivity.kt              # メインActivity (Compose)
├── viewmodel/
│   └── GamerViewModel.kt        # セッション管理・映像送信
├── ui/
│   ├── screen/
│   │   └── GamerScreen.kt       # メイン画面 (カメラ + コントロール)
│   ├── component/
│   │   ├── CameraPreview.kt     # CameraX連携・フレームキャプチャ
│   │   └── StatusOverlay.kt     # 接続状態インジケータ
│   └── theme/                   # Material 3 ダークテーマ
└── util/
    └── PermissionHelper.kt      # カメラ・マイク権限
```

## 技術スタック

| カテゴリ | ライブラリ |
|---------|-----------|
| AI | Firebase AI Logic SDK (Gemini Multimodal Live API) |
| UI | Jetpack Compose + Material 3 |
| カメラ | CameraX |
| 非同期 | Kotlin Coroutines |
| ビルド | AGP 9.0.1 / Gradle 9.3.1 |
| リント | ktlint |
| CI | GitHub Actions |

## ライセンス

MIT
