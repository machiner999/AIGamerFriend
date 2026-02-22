---
name: release
description: ユーザーが「リリースビルド」「APK作成」「パッケージ作成」と言った時に使用
disable-model-invocation: true
---

# Release APK Build & Copy to Google Drive

リリースAPKをビルドしてGoogle Driveにコピーする。

## 手順

1. `./gradlew assembleRelease` を実行してリリースAPKをビルドする
2. ビルドが成功したことを確認する
3. 生成されたAPKを `app/build/outputs/apk/release/` から探す（ファイル名は `app-release.apk` または `AIGamerFriend-release.apk`）
4. APKを `~/Google Drive/マイドライブ/` にコピーする（コピー先ファイル名は `AIGamerFriend-release.apk`）
5. コピー完了を確認し、ファイルサイズとともに結果を報告する

## 注意事項

- `keystore.properties` が存在しない場合は署名なしAPKがビルドされる（正常動作）
- ビルドエラーが発生した場合はエラー内容を報告して停止する
- Google Driveフォルダが存在しない場合はユーザーにコピー先を確認する
