# 設計判断の記録

SPEC.md に明記されていない判断と、その理由を記録する。

## ビルド・依存関係

- **Android Gradle Plugin 9.4.1 / Gradle 9.8.0 / Kotlin 2.4.20 / KSP 2.3.12**: 実装時点（2026-09）の最新安定版。AGP 9 の組み込み Kotlin サポートを使うため、`org.jetbrains.kotlin.android` プラグインは適用しない。
- **compileSdk = 37.2（`release(37) { minorApiLevel = 2 }`）、targetSdk = 37**: 最新安定版のプラットフォーム。targetSdk はメジャー番号のみ指定可能。
- **Room は `androidx.room3`（3.0.3）を使用**: 実装時点での Room の最新安定版は 3.x 系（パッケージ名が `androidx.room3` に変更）。ドライバには `androidx.sqlite:sqlite-bundled` を使う。
- **アイコンは `material-icons-extended` 1.7.8**: Compose BOM から外れたが、最新安定版として個別にバージョン指定する。R8 で未使用アイコンは除去される。
- **バックアップ無効**: 端末内で完結する個人利用アプリのため `allowBackup=false` とし、`dataExtractionRules` でもすべて除外する。
- **versionCode / versionName は `gradle.properties` で管理**: `memorygate.versionCode` / `memorygate.versionName`。リリースタグ `v<versionName>` と一致させる。
