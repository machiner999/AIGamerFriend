# Repository Guidelines

## Purpose
この文書は、Codex などのコーディングエージェントが最初に読む入口です。
トップレベルの作業フロー、必須コマンド、参照先ドキュメントの構成が変わったときに更新します。

## Communication
- All user-facing responses must be in Japanese.
- Code, commands, logs, and error messages may remain in English.

## First Read Order
1. `AGENTS.md` (this file)
2. `docs/change-guide.md`
3. `docs/architecture.md` and `docs/verification.md` as needed
4. `docs/agent-context.md` when product or implementation context is needed

## Core Commands
リポジトリルートで実行します。
- `./gradlew assembleDebug`
- `./gradlew assembleRelease`
- `./gradlew testDebugUnitTest`
- `./gradlew ktlintCheck`
- `./gradlew ktlintFormat`

## Hard Constraints
- 単一モジュールの Android アプリです。実装は Kotlin + Jetpack Compose 前提です。
- 主なコードは `app/src/main/java/com/example/aigamerfriend/` 配下にあります。
- 秘密情報はコミットしません。`GEMINI_API_KEY` は `local.properties` に置きます。
- `app/google-services.json`、`keystore.properties`、`*.jks` はローカル専用です。
- ローカル開発環境は Android SDK + JDK 17 前提です。
- CI の確認列は `./gradlew ktlintCheck testDebugUnitTest lintDebug assembleRelease` です。

## Definition Of Done
- `./gradlew ktlintCheck` が通ること
- `./gradlew testDebugUnitTest` が通ること
- `./gradlew assembleDebug` が通ること
- リリース影響のある変更では `./gradlew assembleRelease` も通ること

## Detailed Docs
- `docs/change-guide.md`: 変更判断の第一参照先。どこを触るか、何を避けるか、何を検証するかを決めます。
- `docs/architecture.md`: レイヤ境界、状態遷移、主要データフローを確認します。
- `docs/verification.md`: 変更種別ごとの実行コマンドと手動確認観点を確認します。
- `docs/agent-context.md`: プロダクト概要、主要機能、現行の実装前提を確認します。

## Human-Facing Docs
- `README.md`: 開発者向けの概要、セットアップ、主要コマンド
- `docs/user_guide.md` と `USER_GUIDE.md`: エンドユーザー向けの利用ガイド
- `docs/PLAN.md`: 過去の実装計画書。現行の運用判断の正本ではありません
