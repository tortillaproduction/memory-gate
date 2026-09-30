# Memory Gate

Android アプリ（Kotlin + Jetpack Compose）。仕様は [docs/SPEC.md](docs/SPEC.md) がすべて。作業前に必ず全文を読むこと。

- 依存ライブラリは実装時点の最新安定版を使い、`gradle/libs.versions.toml` で一元管理する
- 仕様にない判断をしたら `docs/DECISIONS.md` に記録する
- コミット前に `./gradlew lint testDebugUnitTest assembleDebug` を通す
- keystore・パスワード・`local.properties` はコミットしない
- UI 文言は日本語

## ビルド環境のセットアップ（クラウド環境など SDK がない場合）

作業開始時に `java -version` と `$ANDROID_HOME` を確認し、なければ以下を行う。

1. JDK 21 をインストールする（例: `apt-get install -y openjdk-21-jdk-headless`）
2. Android SDK をインストールする
   - https://developer.android.com/studio#command-line-tools-only から最新の Linux 用 cmdline-tools を取得し、`$HOME/android-sdk/cmdline-tools/latest` に展開する
   - `export ANDROID_HOME=$HOME/android-sdk` を設定し、`$ANDROID_HOME/cmdline-tools/latest/bin` と `$ANDROID_HOME/platform-tools` を PATH に追加する
   - `yes | sdkmanager --licenses` を実行してから、`sdkmanager "platform-tools" "platforms;android-<compileSdk>" "build-tools;<最新安定版>"` を実行する
3. SDK のパスは `local.properties` に書くか環境変数で渡す。`local.properties` はコミットしない
4. ダウンロードがネットワーク制限で失敗した場合は、ブロックされたドメインをユーザーに報告して作業を止める（回避策を探さない）
