# AI Gamer Friend

## Purpose

この文書は人間向けの概要、セットアップ、主要コマンドをまとめるための文書です。
セットアップ手順、開発者向け導線、プロジェクト概要が変わったときに更新します。

Android 端末の背面カメラでゲーム画面を映すと、カジュアルなゲーム仲間のようにリアルタイムで音声リアクションしてくれる AI アプリです。OkHttp WebSocket で Gemini Multimodal Live API に直接接続し、双方向の音声と映像ストリーミングで動作します。

## Overview

- 背面カメラのゲーム画面を 1FPS でキャプチャして Gemini に送信
- 日本語の音声リアクションとアニメーション顔表示
- ゲーム名自動検出、Google 検索、音声コマンド
- Gemini Live Native Audio のアフェクティブ ダイアログ対応
- Gemini Live Native Audio のプロアクティブ音声を設定から切替可能
- 自動再接続、セッション再開、会話記憶のフォールバック
- 設定画面で声、リアクション強度、感情追従、プロアクティブ音声、自動スタート、記憶クリアを調整

## Requirements

- Android SDK
- JDK 17
- `local.properties` の `GEMINI_API_KEY`
- `app/google-services.json` for Firebase features

## Setup

1. リポジトリをクローンします。
   ```bash
   git clone https://github.com/<your-username>/AIGamerFriend.git
   cd AIGamerFriend
   ```
2. `local.properties` に API キーを設定します。
   ```properties
   GEMINI_API_KEY=your_api_key
   ```
3. Firebase Console から `google-services.json` を取得して `app/` に配置します。
4. 必要なら `keystore.properties` を用意してリリース署名を設定します。

## Common Commands

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew testDebugUnitTest
./gradlew ktlintCheck
./gradlew ktlintFormat
```

## Project Layout

```text
app/src/main/java/com/example/aigamerfriend/
├── data/        # WebSocket, audio, DataStore
├── model/       # Shared models
├── ui/          # Compose UI
├── util/        # Helpers
└── viewmodel/   # Session state and orchestration
```

## Developer Docs

- `AGENTS.md`: エージェント向けの最初の入口
- `docs/agent-context.md`: プロダクト概要と実装前提
- `docs/architecture.md`: レイヤ構成とデータフロー
- `docs/change-guide.md`: 変更判断基準
- `docs/verification.md`: 検証手順

## User Docs

- `docs/user_guide.md`
- `USER_GUIDE.md`

## CI

GitHub Actions では `ktlintCheck`、`testDebugUnitTest`、`lintDebug`、`assembleRelease` を実行します。CI ではビルドのためにダミーの `google-services.json` と空の API キーを生成します。
