# Architecture

## Purpose
Codex が責務境界とデータフローを誤らず把握するための文書です。
レイヤ構成、状態遷移、主要サブシステムの責務が変わったときに更新します。

## Layer Map

- `app/src/main/java/com/example/aigamerfriend/ui`
  - Compose UI。画面、オーバーレイ、コントロール、テーマを保持します。
- `app/src/main/java/com/example/aigamerfriend/viewmodel`
  - UI 状態とセッション制御の中心です。画面イベントとデータ層をつなぎます。
- `app/src/main/java/com/example/aigamerfriend/data`
  - WebSocket、音声、DataStore、Live API プロトコルの実装を置きます。
- `app/src/main/java/com/example/aigamerfriend/model`
  - UI と ViewModel で共有する軽量なモデルを置きます。
- `app/src/main/java/com/example/aigamerfriend/util`
  - Android 権限など、横断的だが小さい補助ロジックを置きます。

## Primary Runtime Flow

1. `CameraPreview` がフレームをキャプチャします。
2. `GamerViewModel.sendVideoFrame()` が送信条件を満たすフレームを処理します。
3. `GeminiLiveClient.sendVideoFrame()` が WebSocket メッセージとして Gemini に送ります。
4. マイク入力は `AudioManager` が収集し、`GeminiLiveClient.sendAudioChunk()` から送信します。
5. Gemini の音声応答は `AudioManager` で再生されます。
6. Function Call は ViewModel で解釈し、状態更新またはツール応答送信に変換します。

## State Model

- UI に公開する主状態は `GamerViewModel` の `SessionState` です。
- WebSocket レベルの接続状態は `GeminiLiveClient` 内部の `ConnectionState` です。
- UI は `SessionState` を見て描画し、フレーム送信は `Connected` 中のみ実行します。

想定遷移:

`Idle -> Connecting -> Connected -> Reconnecting -> Connected`

失敗時:

`Connecting/Connected/Reconnecting -> Error`

## Responsibility Boundaries

- `ui` は表示とユーザー入力の収集に留めます。
- `viewmodel` はセッション制御、状態遷移、Function Calling の分岐を担います。
- `data` は外部 I/O、永続化、シリアライズ、音声処理を担います。
- `util` にはアプリの中核ロジックを置きません。

## Subsystems That Are Easy To Break

- セッション再開と GoAway 対応
  - 再接続順序を崩すと、新旧セッションの競合で誤ったエラー処理を起こしやすいです。
- Function Calling
  - `parameters` の形やツール名を変えると、Gemini 側との互換性を壊しやすいです。
- 音声入出力
  - サンプリング周波数や PCM 前提をずらすと、応答品質や接続互換性に影響します。
- DataStore ベースの記憶と設定
  - 失敗時は非致命的に扱う前提なので、例外を UI エラーに昇格させないよう注意が必要です。

## Related Files

- `app/src/main/java/com/example/aigamerfriend/viewmodel/GamerViewModel.kt`
- `app/src/main/java/com/example/aigamerfriend/data/GeminiLiveClient.kt`
- `app/src/main/java/com/example/aigamerfriend/data/AudioManager.kt`
