# Change Guide

## Purpose
Codex が「どこを触るか」「何を避けるか」「最低限何を確かめるか」を判断するための文書です。
設計原則、責務分割、変更時の注意点が変わったときに更新します。

## First Rules

- 変更前に `AGENTS.md` を読み、この文書を最優先の詳細ガイドとして扱います。
- 説明追加よりも、既存パターンに沿った変更を優先します。
- 迷ったら新しい層を増やさず、既存の `ui` / `viewmodel` / `data` / `util` の責務に収めます。
- UI 文字列とユーザー向け説明は日本語で扱います。

## Where To Change

### `ui`

次の変更は `ui` に置きます。

- Compose 画面レイアウト
- 表示アニメーション、色、テーマ、オーバーレイ
- ユーザー入力イベントの受け口
- JVM テスト可能な UI 補助関数

`ui` に置かないもの:

- WebSocket 通信
- セッション再接続ポリシー
- DataStore 永続化

### `viewmodel`

次の変更は `viewmodel` に置きます。

- 画面状態の集約
- セッション開始、停止、再接続
- Function Calling の解釈と分岐
- `ui` と `data` の橋渡し

`viewmodel` に置かないもの:

- 低レベルの音声処理
- WebSocket フレーム整形
- Android View 実装そのもの

### `data`

次の変更は `data` に置きます。

- Gemini Live API プロトコル
- OkHttp WebSocket クライアント
- AudioRecord / AudioTrack 周辺
- DataStore による設定、記憶、永続化
- JSON シリアライズ

`data` に置かないもの:

- Compose 依存の描画ロジック
- 画面ごとの状態文言

### `util`

次の変更は `util` に置きます。

- 小さく独立した Android 補助処理
- 明確に汎用で、他レイヤに属しない薄いヘルパー

`util` に置かないもの:

- ビジネスロジック
- 重要な状態遷移
- セッション制御の本体

## Existing Patterns To Preserve

- 状態の中心は `GamerViewModel` に寄せます。
- Compose 側では StateFlow を `collectAsStateWithLifecycle` で収集します。
- 永続化失敗は、記憶・設定まわりでは基本的に非致命で扱います。
- Live API は直接 WebSocket 接続です。Firebase AI SDK の live session に戻さないでください。
- Kotlin のリント規約は `.editorconfig` と ktlint を基準にします。

## Do Not Change Casually

- Gemini モデル名、ツール名、Function Calling の JSON 形状
- 音声のフォーマット前提
- セッション再開トークンと GoAway 周辺の再接続順序
- セキュリティ前提
  - `allowBackup=false`
  - cleartext 不許可
  - 機密ログは `BuildConfig.DEBUG` ガード

## Risky Areas

- `GamerViewModel` の再接続処理
  - セッション切替順序を変えると、旧接続の切断が新接続のエラーとして扱われる危険があります。
- `GeminiLiveModels` の変更
  - サーバー互換性を壊すと、接続できてもツール呼び出しや再開が失敗します。
- `AudioManager`
  - サンプルレートやバッファ処理の変更はノイズや応答遅延を招きます。
- `MemoryStore` / `SettingsStore`
  - 仕様上の「静かに失敗する」前提を壊すと UX が悪化します。

## Testing Expectations By Change Type

- `ui` だけの軽微変更
  - 影響箇所の目視確認
  - 関連する JVM テストがあれば更新
- `viewmodel` 変更
  - 少なくとも成功系 1 件、失敗または境界系 1 件のユニットテストを追加または更新
- `data` 変更
  - 既存テストの更新に加え、シリアライズ、接続状態、永続化の期待値を確認
- ビルド設定や依存変更
  - `ktlintCheck`、`testDebugUnitTest`、必要なら `assembleRelease` を確認

## Practical File Pointers

- セッション制御: `app/src/main/java/com/example/aigamerfriend/viewmodel/GamerViewModel.kt`
- Live API 通信: `app/src/main/java/com/example/aigamerfriend/data/GeminiLiveClient.kt`
- プロトコルモデル: `app/src/main/java/com/example/aigamerfriend/data/GeminiLiveModels.kt`
- 音声処理: `app/src/main/java/com/example/aigamerfriend/data/AudioManager.kt`
- 画面本体: `app/src/main/java/com/example/aigamerfriend/ui/screen/GamerScreen.kt`
