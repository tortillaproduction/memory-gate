# Memory Gate

Android アプリ（Kotlin + Jetpack Compose）。仕様は [docs/SPEC.md](docs/SPEC.md) がすべて。作業前に必ず全文を読むこと。

- 依存ライブラリは実装時点の最新安定版を使い、`gradle/libs.versions.toml` で一元管理する
- 仕様にない判断をしたら `docs/DECISIONS.md` に記録する
- コミット前に `./gradlew lint testDebugUnitTest assembleDebug` を通す
- keystore・パスワード・`local.properties` はコミットしない
- UI 文言は日本語
