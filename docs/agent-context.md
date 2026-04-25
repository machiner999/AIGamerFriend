# Agent Context

## Purpose
Codex 向けに、プロダクト概要と現在の実装前提を短時間で把握するための文書です。
機能の大枠、主要制約、現時点の設計方針が変わったときに更新します。

## Product Summary

AI Gamer Friend は、Android 端末の背面カメラでゲーム画面を見せると、隣で一緒に遊んでいる友達のように日本語でリアクションするアプリです。Gemini Multimodal Live API と直接 WebSocket 接続し、音声と映像を双方向でやり取りします。

## Major Features

- 背面カメラのゲーム画面を 1FPS でキャプチャして Gemini に送信
- マイク音声を 16kHz PCM で送信し、Gemini の音声応答を再生
- 感情に連動するアニメーション顔と音声レベル表示
- Gemini Live Native Audio のアフェクティブ ダイアログ対応
- プロアクティブ音声を設定で切り替え可能
- Function Calling による感情変更、ゲーム名検出、Google 検索、音声コマンド
- 自動再接続、セッション再開トークン、記憶要約のフォールバック
- 設定画面で声、リアクション強度、感情追従、プロアクティブ音声、自動スタート、記憶クリアを制御

## Current Implementation Direction

- アプリは単一画面、単一 ViewModel を中心に構成されます。
- データベースや repository 層はなく、永続化は DataStore で行います。
- Live API は Firebase AI SDK ではなく OkHttp WebSocket で直接扱います。
- Firebase AI Logic SDK は非ライブの要約生成にのみ使用します。
- UI は Jetpack Compose と Material 3 をベースに、グラスモーフィズム表現を多用しています。

## Important Constraints

- `minSdk` は 26、JDK は 17 が前提です。
- 画面は横画面固定ではなく、現状は縦向き前提の UI です。
- システムプロンプトと UI 文言は日本語前提です。
- `app/google-services.json` はコミットしません。
- `local.properties` の `GEMINI_API_KEY` がないと Live API 接続はできません。
- CI ではダミーの `google-services.json` と空の API キーを生成してビルドだけ検証します。

## Source Of Truth

- 変更判断は `docs/change-guide.md`
- 責務境界と状態遷移は `docs/architecture.md`
- 検証方法は `docs/verification.md`
