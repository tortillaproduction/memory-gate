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

## 誘導先の一覧・編集

- **最終訪問の表示**: 当日（または時計の巻き戻りで未来日時）の場合は「最終: 今日」、それ以外は「最終: N日前」、未訪問は「未訪問」。
- **次の期限日**: 期限切れ・未訪問の誘導先には表示しない（「期限切れ」バッジを表示）。WEEKLY は今日より後で直近の指定曜日。
- **新規作成時の「毎週〇曜日」の初期値は今日の曜日**。
- **「未訪問に戻す」は押した時点で即時に保存する**（保存ボタンを押さなくても反映）。
- **URL のバリデーション**: 前後の空白を除去したうえで、スキームが `http` / `https` でホスト名があることを確認する。空白を含む URL は不可。
- **種別を切り替えても入力済みの URL / アプリは保持し、保存時には選択中の種別の値だけを保存する**。

## AccessibilityService・ゲート画面

- **Clock はタイムゾーン変更に追従する `DeviceClock` を注入する**: `Clock.systemDefaultZone()` は生成時点のタイムゾーンに固定されるため、アプリ全体で共有する Clock として毎回 `ZoneId.systemDefault()` を返す実装を用意した。テストでは `Clock.fixed` を注入する。
- **デバウンスの「直前のパッケージ」には自アプリも記録する**（ゲートの判定は行わない）。ゲート表示後に監視対象アプリへ戻ったときに確実に再判定するため。
- **キャッシュの読み込み完了前（サービス接続直後）はゲートを出さない**。
- **ゲート表示中に期限切れの誘導先がなくなった場合**（誘導先の削除など）はゲートを閉じる。
- **「開く」の処理順**: 起動可否を確認 → `lastVisitedAt` / `gatePassedDate` を保存（完了を待つ）→ 誘導先を開く → `finish()`。保存完了を待ってから開くのは、ブラウザ自体が監視対象アプリの場合に再度ゲートが出るのを防ぐため。バックグラウンドからの Activity 起動制限を避けるため、`finish()` より先に誘導先を起動する。
- **誘導先は `FLAG_ACTIVITY_NEW_TASK` で開く**: ゲートの専用タスク（`excludeFromRecents`）に誘導先が載って最近のタスクから消えるのを防ぐため。
- **URL を開けなかった場合**（ブラウザがない等）はトーストで「開けませんでした」と表示する。データはすでに更新済み。
- **緊急退避の長押し時間は、アニメーション速度の設定（開発者オプション）に影響されないよう、フレーム時刻から計算する**。
- **AccessibilityService は `exported="true"` + `BIND_ACCESSIBILITY_SERVICE` 権限で保護する**（一般的な構成）。
- **コルーチンのラムダを含むユニットテストは ASCII のメソッド名にする**: 生成されるクラス名がファイル名になり、ロケール未設定の環境（`LANG` が空など）でコンパイルに失敗するため。

## オンボーディング

- **1 ステップずつ表示する形式**（上部に進捗バー、下部に「戻る」「次へ」）。誘導先・監視対象アプリのステップは未完了なら「スキップ」と表示する。最後のステップの「完了」で `onboardingCompleted = true` にしてホームへ移動する。
- **ステップの完了判定**:
  - はじめに: 次のステップへ進んだら完了
  - 制限付き設定の許可: 許可状態を公開 API で取得できないため、**ユーザー補助が有効になっていれば完了**とみなす
  - メーカー別の追加設定: 自己申告のチェックボックス。状態は DataStore の `manufacturerStepDone`（SPEC 3.3 にないキーを追加）に保存する
- **`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` は SPEC の指定どおり使用し、lint の `BatteryLife` 警告は抑制する**（Play Store を経由しないため、ポリシー上の問題はない）。
- **設定画面が開けない端末では、アプリ情報画面にフォールバックする**。
- **ホームのバナーからは、オンボーディングの「ユーザー補助を有効化」ステップを直接開く**。

## 配布・CI

- **署名情報の受け渡し**: release.yml で `KEYSTORE_BASE64` をデコードしてランナーの一時ディレクトリに書き出し、そのパスを `KEYSTORE_PATH` 環境変数で Gradle に渡す。`app/build.gradle.kts` は `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` がすべて揃っているときだけ release の署名設定を作る。
- **タグと versionName の整合性チェック**: release.yml でタグが `v<versionName>` と一致しない場合は失敗させる（`gradle.properties` を唯一の情報源にするため）。
- **GitHub Release の作成には `gh release create` を使う**（ランナーに標準で入っており、追加の Action に依存しない）。APK は `memory-gate-<タグ>.apk` という名前で添付する。
- **R8**: release で `isMinifyEnabled` と `isShrinkResources` を有効にする。Room（`-keep class * extends androidx.room3.RoomDatabase`）・DataStore・Navigation などは各ライブラリ同梱の consumer ルールで足りるため、独自の keep ルールは行番号の保持のみ。Room の enum 変換は生成コードの文字列リテラルで行われ、R8 による難読化の影響を受けないことを確認した。
- **build.yml は push と pull_request の両方で実行する**（SPEC の指定どおり。PR ブランチでは同じコミットに対して 2 回実行される）。
