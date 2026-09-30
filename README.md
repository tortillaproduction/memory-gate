# Memory Gate

SNS などの「つい開いてしまうアプリ」を開いたときに、期限切れの「やるべきこと」（誘導先）があればゲート画面を表示して、そちらへ誘導する Android アプリです。

- 個人利用向け。Play Store は経由せず、署名済み APK を GitHub Releases で配布します
- 端末内で完結します（サーバー・アカウント・インターネット権限なし）
- 仕様: [docs/SPEC.md](docs/SPEC.md) / 設計判断: [docs/DECISIONS.md](docs/DECISIONS.md) / 実機確認: [docs/MANUAL_TEST.md](docs/MANUAL_TEST.md)

## 動作環境

- Android 8.0（API 26）以上

## インストール手順

1. [Releases](../../releases) から最新の `memory-gate-vX.Y.Z.apk` を端末にダウンロードします。
2. ダウンロードした APK をタップします。
3. 「提供元不明のアプリ」のインストールを許可します。
   - 初回は「このソースからのアプリのインストールを許可」を求められるので、APK を開いたアプリ（Chrome やファイルアプリなど）に対して許可してください。
   - 設定場所は機種により異なります（例: 設定 → アプリ → 特別なアプリアクセス → 不明なアプリのインストール）。
4. **Play プロテクトの警告**が表示されることがあります。
   - 「安全でない可能性のあるアプリ」「アプリをスキャンしますか？」などと表示された場合は、「詳細」→「インストールする（安全でない可能性あり）」を選びます。
   - Play Store を経由しない個人署名のアプリのため表示されるもので、アプリの動作には影響しません。
5. アプリを起動し、オンボーディングに従って設定します。
   - Android 13 以上では、ストア外からインストールしたアプリはユーザー補助を有効にできないことがあります。アプリ情報 → 右上の︙ → 「制限付き設定を許可」をタップしてから、ユーザー補助で Memory Gate をオンにしてください。

### アップデート

新しい APK を同じ手順でインストールすると、データを保持したまま上書きアップデートされます。**同じ keystore で署名された APK でないとアップデートできません**（後述）。

## リリース用 keystore の作成と Secrets の登録

リリース APK は GitHub Actions（`.github/workflows/release.yml`）で署名します。署名情報は GitHub の Secrets から取得し、**リポジトリには絶対にコミットしません**（`.gitignore` で `*.jks` / `*.keystore` / `keystore.properties` / `local.properties` を除外しています）。

### 1. keystore を作成する（初回のみ）

JDK に含まれる `keytool` を使います。

```sh
keytool -genkeypair -v \
  -keystore memory-gate-release.jks \
  -alias memorygate \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

- keystore のパスワード、鍵のパスワード、名前などを聞かれるので入力します。
- `-alias` に指定した値（例: `memorygate`）が `KEY_ALIAS` になります。

### 2. keystore を Base64 に変換する

```sh
# Linux
base64 -w 0 memory-gate-release.jks > keystore.base64.txt
# macOS
base64 -i memory-gate-release.jks -o keystore.base64.txt
```

### 3. GitHub の Secrets に登録する

リポジトリの Settings → Secrets and variables → Actions → New repository secret で、次の 4 つを登録します。

| Secret 名 | 値 |
|---|---|
| `KEYSTORE_BASE64` | `keystore.base64.txt` の中身 |
| `KEYSTORE_PASSWORD` | keystore のパスワード |
| `KEY_ALIAS` | 鍵のエイリアス（例: `memorygate`） |
| `KEY_PASSWORD` | 鍵のパスワード |

登録後、`keystore.base64.txt` は削除してください。

### 4. keystore をバックアップする（重要）

**keystore（`.jks` ファイル）とパスワードを失うと、インストール済みのアプリをアップデートできなくなります**（別の鍵で署名した APK は上書きインストールできず、アンインストール＝データ消去が必要になります）。

- `.jks` ファイルとパスワードを、パスワードマネージャーや暗号化したストレージなど、リポジトリ以外の安全な場所に必ずバックアップしてください。
- GitHub の Secrets は後から値を読み出せないため、バックアップの代わりにはなりません。

## リリース手順

1. `gradle.properties` の `memorygate.versionCode`（1 ずつ増やす）と `memorygate.versionName` を更新してコミットします。
2. `v<versionName>` のタグを作成して push します。

   ```sh
   git tag v0.1.0
   git push origin v0.1.0
   ```

3. GitHub Actions が署名済みの release APK をビルドし、GitHub Release に `memory-gate-v0.1.0.apk` として添付します。タグと `versionName` が一致しない場合はビルドが失敗します。

### ローカルで署名済み APK を作る場合

環境変数で署名情報を渡して `assembleRelease` を実行します。環境変数がない場合、release ビルドは署名なしで作成されます（debug ビルドには影響しません）。

```sh
export KEYSTORE_PATH=/path/to/memory-gate-release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=memorygate
export KEY_PASSWORD=...
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

## 開発

- JDK 21 と Android SDK が必要です（SDK のパスは `local.properties` の `sdk.dir` か `ANDROID_HOME` で指定。`local.properties` はコミットしない）。
- コミット前に次のコマンドが通ることを確認してください（CI の `.github/workflows/build.yml` でも実行されます）。

  ```sh
  ./gradlew lint testDebugUnitTest assembleDebug
  ```
