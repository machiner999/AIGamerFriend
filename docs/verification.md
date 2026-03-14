# Verification Guide

## Purpose
Codex が変更内容に応じて、どのコマンドを回し、何を手動確認すべきか判断するための文書です。
CI の流れ、ローカル検証手順、変更種別ごとの確認観点が変わったときに更新します。

## Baseline Commands

リポジトリルートで実行します。

- `./gradlew ktlintCheck`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

リリースや依存関係、CI 影響がある変更では追加で:

- `./gradlew assembleRelease`
- `./gradlew lintDebug`

## CI Mapping

GitHub Actions の CI は次を実行します。

- `./gradlew ktlintCheck testDebugUnitTest lintDebug assembleRelease`

CI では以下を事前生成します。

- ダミーの `app/google-services.json`
- 空の `GEMINI_API_KEY` を含む `local.properties`

## Recommended Verification By Change Type

### UI Changes

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- 目視確認
  - 接続状態表示
  - コントロール操作
  - アニメーション崩れ
  - 安全領域とレイアウト崩れ

### ViewModel Changes

- `./gradlew testDebugUnitTest`
- 必要に応じて対象テストを個別実行
- セッション開始、停止、エラー、再接続の期待状態を確認

### Data Layer Changes

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- 必要に応じて `./gradlew assembleRelease`
- 次を重点確認
  - シリアライズ互換性
  - 接続状態遷移
  - 設定/記憶の読み書き
  - 音声フォーマット前提

### Build Or CI Changes

- `./gradlew ktlintCheck testDebugUnitTest lintDebug assembleRelease`
- ワークフロー定義とローカルコマンドの差分がないか確認

## Manual Scenarios Worth Checking

- 初回起動時の権限要求
- セッション開始から `Connected` までの流れ
- ミュート切替
- ゲーム名表示
- エラー時のメッセージと UI 反応
- 長時間利用時の再接続

## Notes

- `ktlintCheck` は現状のツールチェーン上、実質的に `.kts` 中心の確認になる点に注意します。
- `testDebugUnitTest` が主な自動回帰確認です。instrumented test は現在ありません。
- リリース関連の変更では、署名設定がなくても `assembleRelease` 自体は確認できます。
