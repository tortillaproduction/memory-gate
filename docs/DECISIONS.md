# 設計判断の記録

SPEC.md に明記されていない判断と、その理由を記録する。

## ビルド・依存関係

- **Android Gradle Plugin 9.4.1 / Gradle 9.8.0 / Kotlin 2.4.20 / KSP 2.3.12**: 実装時点（2026-09）の最新安定版。AGP 9 の組み込み Kotlin サポートを使うため、`org.jetbrains.kotlin.android` プラグインは適用しない。
- **compileSdk = 37.2（`release(37) { minorApiLevel = 2 }`）、targetSdk = 37**: 最新安定版のプラットフォーム。targetSdk はメジャー番号のみ指定可能。
- **Room は `androidx.room3`（3.0.3）を使用**: 実装時点での Room の最新安定版は 3.x 系（パッケージ名が `androidx.room3` に変更）。ドライバは既定の `AndroidSQLiteDriver`（OS の SQLite）を使い、APK にネイティブライブラリを含めない。
- **アイコンは `material-icons-extended` 1.7.8**: Compose BOM から外れたが、最新安定版として個別にバージョン指定する。R8 で未使用アイコンは除去される。
- **バックアップ無効**: 端末内で完結する個人利用アプリのため `allowBackup=false` とし、`dataExtractionRules` でもすべて除外する。
- **versionCode / versionName は `gradle.properties` で管理**: `memorygate.versionCode` / `memorygate.versionName`。リリースタグ `v<versionName>` と一致させる。

## アプリ一覧・除外ルール

- **`<queries>` に `MAIN` / `HOME` の intent も追加**: SPEC は `MAIN` / `LAUNCHER` の宣言のみ指定しているが、除外対象の「ホームアプリ（`CATEGORY_HOME` を解決したもの）」を Android 11 以降のパッケージ可視性の下で確実に取得するために追加した。`QUERY_ALL_PACKAGES` は使わない。
- **ホームアプリは `CATEGORY_HOME` を解決できるすべてのパッケージを除外**: 既定のホームだけでなく、インストールされているホームアプリすべてを除外する（切り替え時に監視対象に残らないようにするため）。
- **検索はアプリ名とパッケージ名の部分一致（大文字小文字を区別しない）**。アプリ名の並び順は日本語ロケールの `Collator` を使う。
- **除外アプリがすでに監視対象に登録されている場合**（例: 後から既定の SMS アプリに変更した）は、一覧には表示しないが DB からは削除しない。ゲート判定では監視対象として扱われる。
