# Memory Gate 実装指示書

この文書は Memory Gate（Android アプリ）の MVP を実装するための仕様・指示書です。
実装者（Claude Code cloud session）はこの文書を唯一の仕様として扱い、曖昧な点は「9. 判断に迷ったとき」に従ってください。

---

## 1. 目的

SNS 等の暇つぶしアプリ（以下「監視対象アプリ」）を開いたとき、期限切れの「やるべきこと」（以下「誘導先」）がある場合に**ゲート画面**を強制表示し、誘導先（Web サイトまたはアプリ）へ誘導する。

- 個人利用。Play Store は経由せず、署名済み APK を GitHub Releases で配布する
- 端末内で完結する。サーバー・アカウントは不要。ネットワーク通信は、新しいバージョンの確認（GitHub に最新のバージョン番号を問い合わせる）のためだけに行う。INTERNET 権限はこの用途だけに使う（URL は外部ブラウザで開く）
- UI 言語は日本語のみ

---

## 2. 技術スタック

実装時点の**最新安定版**を確認して使用すること（alpha / beta / rc は使わない）。

| 項目 | 採用 |
|---|---|
| 言語 | Kotlin |
| UI | Jetpack Compose + Material 3、Navigation Compose |
| DB | Room（KSP でコード生成） |
| 設定値 | DataStore (Preferences) |
| URL を開く | androidx.browser Custom Tabs（失敗時は `ACTION_VIEW` にフォールバック） |
| DI | 手動 DI（`Application` に `AppContainer` を持たせる）。Hilt は使わない |
| 非同期 | Kotlin Coroutines / Flow |
| ビルド | Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`) |
| SDK | `minSdk = 26`、`compileSdk` / `targetSdk` = 最新安定版 |
| テスト | JUnit（日付計算・ゲート対象選定のロジックを中心に） |

- パッケージ名 / applicationId: `app.memorygate`（初回リリース後は変更不可）
- モジュール構成: 単一 `:app` モジュールで良い

---

## 3. 用語とデータモデル

### 3.1 誘導先 (Target)

| フィールド | 型 | 説明 |
|---|---|---|
| id | Long (PK, auto) | |
| title | String | 表示名（例: 「確定申告」「Duolingo」） |
| type | enum `URL` / `APP` | |
| url | String? | type=URL のとき必須。`http://` / `https://` のみ許可 |
| packageName | String? | type=APP のとき必須 |
| scheduleType | enum `EVERY_N_DAYS` / `WEEKLY` | |
| intervalDays | Int? | EVERY_N_DAYS のとき 1 / 3 / 7 のいずれか |
| dayOfWeek | Int? | WEEKLY のとき 1(月)〜7(日)。`java.time.DayOfWeek` の値 |
| lastVisitedAt | Long? | 最後にゲートから開いた日時 (epoch millis)。null = 未訪問 |
| createdAt | Long | 作成日時 (epoch millis) |
| snoozeImagePath | String? | スヌーズ用ゲートに表示する画像（アプリ内部ストレージのファイルパス） |
| snoozeEnabled / snoozeIntervalMinutes / snoozeStartMinutes / snoozeEndMinutes / lastSnoozeShownAt | | v0.1.6 までの誘導先ごとのスヌーズ設定。v0.1.7 からは使わない（3.3 の引き継ぎでだけ読む）。Room の列は削除せずに残す |

スヌーズの列は v0.1.4 で追加した（Room の DB バージョン 1 → 2）。既存のデータを保持するマイグレーションを用意し、書き出したスキーマ（`app/schemas`）を使ってユニットテストで検証する。
v0.1.7 でスヌーズの設定をアプリ全体で 1 つにした（3.3）。誘導先ごとに残すのは `snoozeImagePath` だけ。DB のバージョンは上げない。

UI 上のスケジュール選択肢は次の 4 種類:
「1日ごと」「3日ごと」「1週間ごと」「毎週〇曜日」（曜日を選択）

### 3.2 監視対象アプリ (GuardedApp)

| フィールド | 型 |
|---|---|
| packageName | String (PK) |

### 3.3 設定 (DataStore)

| キー | 型 | 説明 |
|---|---|---|
| gatePassedDate | String? (ISO LocalDate) | ゲートのボタンを押した日。この日はゲートを出さない |
| onboardingCompleted | Boolean | |
| manufacturerStepDone | Boolean | オンボーディングの「メーカー別の追加設定」を自己申告で完了にしたか |
| updateLastCheckedAt | Long? (epoch millis) | 新しいバージョンを前回確認した日時。失敗した場合も更新する |
| updateLatestVersion | String? | 確認で見つかった、現在より新しいバージョン（例: `0.1.3`。先頭の `v` は除く） |
| updateLatestUrl | String? | そのバージョンのリリースページの URL（`html_url`） |
| updateDismissedVersion | String? | ホームのお知らせバナーを「×」で閉じたバージョン |
| snoozeEnabled | Boolean（既定 false） | スヌーズの ON/OFF（アプリ全体）。OFF にしても下記のスヌーズ設定は保持する |
| snoozeIntervalMinutes | Int（既定 30） | スヌーズの間隔。1 / 5 / 30 / 60 のいずれか |
| snoozeStartMinutes | Int（既定 540） | スヌーズする時間帯の開始（0:00 からの分数。540 = 9:00） |
| snoozeEndMinutes | Int（既定 1320） | スヌーズする時間帯の終了（0:00 からの分数。1320 = 22:00） |
| lastSnoozeShownAt | Long? (epoch millis) | スヌーズ用ゲートを最後に表示した日時（アプリ全体） |
| snoozePausedAt | Long? (epoch millis) | 通常のゲート・スヌーズ用ゲートの「開く」で誘導先を開いた日時（アプリ全体）。ここから間隔の間はスヌーズを休止する（4.7。v0.1.8） |
| snoozeSettingsMigrated | Boolean | v0.1.6 までの誘導先ごとのスヌーズ設定の引き継ぎが完了したか |

**スヌーズ設定の引き継ぎ（v0.1.7）**: アップデート後の初回起動時に 1 回だけ行い、完了したら `snoozeSettingsMigrated = true` を保存する（設定と完了フラグは 1 回の書き込みで保存する）。
- `snoozeEnabled = true` の誘導先がある: 全体の `snoozeEnabled = true`。そのうち 4.2 の並び順で先頭の誘導先の間隔・時間帯（NULL なら既定値）を全体の設定にする
- `snoozeEnabled = true` の誘導先がない: 全体の設定は既定値（OFF）にする

---

## 4. コアロジック

日付はすべて**端末のローカルタイムゾーンの LocalDate**で判定する。日付の境界は 0:00。
テストしやすくするため、`java.time.Clock` を注入すること。

### 4.1 期限切れ判定 `isDue(target, today)`

- `lastVisitedAt == null` → **期限切れ**
- `lastDate = lastVisitedAt をローカル日付に変換したもの`
- `lastDate > today`（時計が巻き戻った場合など）→ 期限切れではない
- EVERY_N_DAYS: `today >= lastDate + intervalDays` なら期限切れ
- WEEKLY: `anchor = today 以前（today を含む）で直近の dayOfWeek の日付` とし、`lastDate < anchor` なら期限切れ

例（1日ごと）: 9/30 に訪問 → 10/1 に期限切れ
例（毎週月曜）: 前回訪問が先週金曜で今日が月曜 → 期限切れ。今日の月曜に訪問済み → 翌週月曜まで期限切れにならない

### 4.2 ゲートに表示する誘導先 `selectGateTargets(targets, today)`

1. `isDue` が true のものを抽出する
2. 並び順: `lastVisitedAt` の昇順（null = 未訪問が最優先）→ 同順位は `createdAt` の昇順
3. 先頭 **5 件**まで

### 4.3 ゲートを表示するか `shouldShowGate(pkg, today)`

次のすべてを満たすときに表示する:
- `pkg` が監視対象アプリに含まれる
- `gatePassedDate != today`
- `selectGateTargets` が 1 件以上

### 4.4 ゲートのボタンを押したとき

1. その誘導先の `lastVisitedAt = now`
2. `gatePassedDate = today`（**その日はもうゲートを出さない**。翌日以降に期限切れがあれば再び出す）
3. ゲートを閉じ（`finish()`）、誘導先を開く
   - URL: Custom Tabs で開く
   - APP: `packageManager.getLaunchIntentForPackage(packageName)` で起動する。アンインストール済みなどで起動できない場合は、ボタンを無効化して「アプリが見つかりません」と表示する（その場合は 1・2 を行わない）
4. 誘導先を開けたら、全体の `snoozePausedAt = now` を保存する（スヌーズの休止。4.7。v0.1.8）。通常のゲート・スヌーズ用ゲートのどちらでも行う。開けなかった場合は保存しない

### 4.5 緊急退避ボタン

- ゲート画面の**最下部**に配置する。目立たない控えめなデザインで、ラベルは「長押しで退避（3秒）」
- **3 秒間長押し**すると発動する。押している間は進捗（リングまたはバー）を表示し、途中で指を離したらリセットする
- 発動したらホーム画面へ移動し（`Intent.ACTION_MAIN` + `CATEGORY_HOME`）、ゲートを閉じる
- `lastVisitedAt` と `gatePassedDate` は**更新しない**（次に監視対象アプリを開けば再びゲートが出る）

### 4.6 ゲート画面のその他の挙動

- 戻るボタン / 戻るジェスチャー: 緊急退避と同じくホームへ移動する（監視対象アプリへは戻さない）
- 各誘導先カードに表示する内容: タイトル、種別アイコン（Web / アプリ）、「最終: N日前」または「未訪問」、「開く」ボタン
- Activity 設定: `excludeFromRecents="true"`、`launchMode="singleTask"`、専用の `taskAffinity`、`noHistory` は付けない
- すでにゲートが表示中なら再起動せず、内容だけ更新する（`onNewIntent`）

### 4.7 スヌーズ

指定した時間帯・間隔で、誘導先をスヌーズ用ゲート（7.5.1）で知らせる。設定はアプリ全体で 1 つ（3.3。v0.1.7 から）。期限切れかどうか・通常のゲートを通過したかどうか（`gatePassedDate == today`）には関係なく動く。判定は `java.time.Clock` を注入してユニットテストする。

- `isInSnoozeWindow(settings, now)`: 現在時刻（ローカル時刻の 0:00 からの分数）が全体の `snoozeStartMinutes`〜`snoozeEndMinutes` に入っているか（開始を含み、終了を含まない）
  - 開始 < 終了: 通常の範囲
  - 開始 > 終了: 日付をまたぐ（例: 22:00〜2:00）
  - 開始 == 終了: 24 時間ずっと
- `isSnoozeReady(settings, now)`: 次のすべてを満たす（`isDue` は条件にしない）
  - 全体の `snoozeEnabled`
  - `isInSnoozeWindow`
  - 「開く」の後の休止中でない（下記）
  - 全体の `lastSnoozeShownAt` が null、または `now - lastSnoozeShownAt >= snoozeIntervalMinutes`（監視対象アプリへの切り替え時は、5 章のとおり間隔を問わない）
- **「開く」の後の休止**（v0.1.8）: `now < snoozePausedAt + snoozeIntervalMinutes` の間は、スヌーズ用ゲートを一切出さない（切り替え時の即時表示も、使用中の定期表示も）
  - 通常のゲート・スヌーズ用ゲートの「開く」で誘導先を開けたときに休止する（4.4 の 4）
  - 緊急退避・戻る操作でゲートを閉じた場合は休止しない（監視対象アプリを開き直せばすぐに表示する）。「開く」で誘導先を開けなかった場合（アプリが見つからないなど）も休止しない
  - 誘導先が複数あっても、休止中は「開く」のたびに次の誘導先が続けて出ることはない
- `selectSnoozeTarget(targets)`: すべての誘導先の中から、4.2 と同じ並び順（`lastVisitedAt` の古い順、未訪問が最優先、同じなら `createdAt` の古い順）で先頭の 1 件。期限切れかどうかは問わない
  - 「開く」を押すとその誘導先の `lastVisitedAt` が更新されるので、次回は別の誘導先が選ばれる
  - 誘導先が 1 件もない場合はスヌーズ用ゲートを表示しない
- 通常のゲート（4.3）の表示条件を満たすときは、通常のゲートを優先する。スヌーズ用ゲートは、通常のゲートが出ないとき（`gatePassedDate == today`、期限切れがないなど）に判定する。監視対象アプリ以外ではどちらも出さない
- `nextSnoozeReadyAt(settings, now)`: 次にスヌーズが可能になる時刻（前回表示 + 間隔、休止の終了（`snoozePausedAt` + 間隔）、時間帯の開始のうち最も遅いもの）。スヌーズ OFF・誘導先なしなら null。5 章のタイマーに使う
- スヌーズ用ゲートを表示したら、全体の `lastSnoozeShownAt = now` を保存する
- スヌーズ用ゲートの「開く」ボタンを押したときは 4.4 と同じ（その誘導先の `lastVisitedAt` と `gatePassedDate` を更新し、誘導先を開き、開けたら休止する）。スヌーズは止めない。緊急退避・戻る操作は 4.5・4.6 と同じ
- スヌーズが止まるのは次の 2 つの場合だけ。どちらも全体の `snoozeEnabled = false` にする（間隔・時間帯の設定は残す）
  - スヌーズ設定画面（7.6）で、スイッチを OFF にしたとき
  - スヌーズ用ゲートの「スヌーズを止める」ボタン（7.5.1）で OFF にしたとき
- 時間帯の外では表示しない。翌日に時間帯へ入れば再び表示する

---

## 5. アプリ起動の検知（AccessibilityService）

- `GateAccessibilityService` を実装する
  - 設定 XML: `accessibilityEventTypes="typeWindowStateChanged"`、`canRetrieveWindowContent="false"`、`accessibilityFeedbackType="feedbackGeneric"`、`notificationTimeout="100"`
  - `description` には用途を日本語で明記する（「指定アプリの起動を検知してゲート画面を表示するためにのみ使用します。画面の内容は読み取りません」）
- `onAccessibilityEvent`:
  - `event.packageName` を取得する。自アプリ・null・直前と同じパッケージは無視する（デバウンス）
  - `shouldShowGate` が true なら、`FLAG_ACTIVITY_NEW_TASK` 付きで GateActivity を起動する
- **パフォーマンス**: イベントごとに DB を読まないこと。監視対象アプリの集合・誘導先一覧・gatePassedDate・スヌーズの設定（`lastSnoozeShownAt` を含む）は Flow を購読してメモリ上にキャッシュし、判定はメモリ上で行う
- **スヌーズの割り込み**（4.7）:
  - 監視対象アプリが前面に来たときに、通常のゲート（4.3）を判定した後でスヌーズ（4.7）を判定する。条件を満たせば `FLAG_ACTIVITY_NEW_TASK` 付きでスヌーズ用ゲートを起動する
  - **切り替え時の即時表示**（v0.1.7）: スヌーズが ON で時間帯内のとき、監視対象アプリが前面に来たら（直前の前面が監視対象アプリ以外、または別の監視対象アプリだった場合）、間隔に関係なくすぐにスヌーズ用ゲートを出す
    - 自アプリ・IME・SystemUI（通知シェード）のウィンドウを経由して同じ監視対象アプリに戻った場合は「切り替え」とみなさない
    - 「直前の前面」は、これらを除いた最後のパッケージで判定する
  - **使用中の定期表示**: どちらのゲートも出ないときは、監視対象アプリが前面にある間、次にスヌーズが可能になる時刻（`nextSnoozeReadyAt`。`lastSnoozeShownAt` + 間隔、または休止の終了）にタイマー（coroutine の `delay`）を設定する。タイマーが発火したら再判定し、条件を満たせばスヌーズ用ゲートを起動する
  - **「開く」の後の休止**（v0.1.8）: 休止中（4.7）は、切り替え時の即時表示も定期表示もしない。休止が終わったら、監視対象アプリが前面にあればすぐに判定する（タイマーの次回時刻に休止の終了時刻を含める）
    - 休止はメモリ上のキャッシュにもすぐ反映する（`snoozePausedAt` の保存が Flow に反映されるのを待たない）
  - **堂々巡りの防止**（v0.1.7）: スヌーズ用ゲートの「開く」で開いた先のパッケージでは、`lastSnoozeShownAt` + 間隔が経過するまで切り替え時の即時表示をしない（定期表示は通常どおり）
    - 開いた先のパッケージ: type=APP ならその `packageName`。type=URL なら実際に起動したブラウザ（Custom Tabs で解決されたパッケージ、または `ACTION_VIEW` で解決されたパッケージ）
  - 監視対象アプリ以外が前面に来たらタイマーを止める。ただし、IME・SystemUI（通知シェード）・自アプリのウィンドウイベントでは止めない
  - 画面がオフのとき（`PowerManager.isInteractive == false`）は表示しない。画面が点いて監視対象アプリが前面にあれば再判定する
  - スヌーズ用ゲートを表示した時点で、全体の `lastSnoozeShownAt = now` を保存する
  - イベントごとに DB を読まない（上記と同じくメモリ上のキャッシュで判定する）
- バックグラウンドからの Activity 起動は「システムにバインドされた AccessibilityService」の例外で許可される想定。実機で起動できない場合に備え、`SYSTEM_ALERT_WINDOW` をフォールバックとして取得する案をコメントに残す（MVP では実装しなくて良い）

---

## 6. インストール済みアプリの一覧

- `AndroidManifest.xml` に `<queries>` で `MAIN` / `LAUNCHER` の intent と、`mailto:` の `ACTION_SENDTO` の intent を宣言する（`QUERY_ALL_PACKAGES` は使わない）
- ランチャーに表示されるアプリを取得し、アプリ名・アイコン・パッケージ名を一覧にする。アプリ名順に並べ、検索ボックスで絞り込めるようにする
- **除外アプリ**（監視対象アプリとして選択不可。一覧には表示しない）:
  - 自アプリ
  - 既定の電話アプリ（`TelecomManager.defaultDialerPackage`）
  - 既定の SMS アプリ（`Telephony.Sms.getDefaultSmsPackage`）
  - ホームアプリ（`CATEGORY_HOME` を解決したもの）
  - IME（`InputMethodManager.enabledInputMethodList`）
  - 固定リスト: `jp.naver.line.android`、`com.android.settings`、`com.android.emergency`、`com.google.android.dialer`、`com.android.dialer`、`com.google.android.apps.messaging`、`com.android.contacts`、`com.google.android.contacts`、`com.samsung.android.dialer`、`com.samsung.android.messaging`
  - メールアプリ: `mailto:` の `ACTION_SENDTO` に応答するアプリと、固定リスト `com.google.android.gm`、`com.microsoft.office.outlook`、`jp.co.yahoo.android.ymail`、`com.samsung.android.email.provider`、`com.android.email`
  - システムアプリ（下記の「システムアプリも表示」がオフのときのみ除外）:
    - `ApplicationInfo.FLAG_SYSTEM` があり、かつ `FLAG_UPDATED_SYSTEM_APP` がないアプリ（時計・電卓・カメラ・ファイル管理・メーカー独自ツールなど）
    - OS 基盤アプリ: `com.android.vending`、`com.google.android.gms`、`com.google.android.googlequicksearchbox`
  - 除外判定は `ExcludedApps` のような 1 か所にまとめること。端末のパッケージ情報は差し替え可能にしてユニットテストする
- 監視対象アプリの選択画面には「システムアプリも表示」スイッチを置く（既定はオフ）。オンにするとシステムアプリも一覧に表示する。オンにしても、メールアプリと上記のその他の除外アプリ（電話・SMS・LINE など）は表示しない
- 誘導先（type=APP）の選択にも同じ一覧を使う（こちらは自アプリ以外を除外しない、単一選択。メール・システムアプリの除外も適用しない）
- 誘導先として登録されているアプリは、監視対象アプリとして選択不可にする（逆も同様）。選択不可の理由を小さく表示する

---

## 7. 画面構成

### 7.0 全画面共通: システムナビゲーションバー

- ゲート画面を含むアプリ内のすべての画面で、システムナビゲーションバー（戻る・ホーム・履歴のボタンとその背景の帯）を既定で非表示にする（`WindowInsetsControllerCompat`）
- 画面上のボタンや入力欄以外の部分をタップするたびに、ナビゲーションバーの表示・非表示を切り替える。画面の端からのスワイプでは OS 標準の動作どおり一時的に表示される（`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`）
- ナビゲーションバーが表示されているときも、アプリのボタンが帯に重ならないようにする（下部のボタン領域などに `navigationBarsPadding` / WindowInsets を適用する）
- ステータスバー（画面上部）は常に表示する

### 7.1 オンボーディング（初回起動時。設定画面からいつでも再表示できる）

各ステップは状態を自動で確認し、完了したらチェックマークを表示する（`onResume` で再確認）。

「戻る」ボタン（下部）とシステムの戻る操作（ボタン・ジェスチャー）は同じ動作にする:
- 2 ステップ目以降: 前のステップへ戻る
- 最初のステップ（はじめに）: 「戻る」ボタンは常に有効
  - メニューなどから再表示したとき（`onboardingCompleted == true`）: ホーム画面に戻る
  - 初回起動時（`onboardingCompleted == false`）: アプリを閉じる（Activity を `finish()` する。次に起動したときは再びオンボーディングから始まる）

1. **はじめに**: アプリの目的と仕組みの説明。データの保存先の説明は「データはこの端末内にだけ保存され、外部へ送信されることはありません。新しいバージョンの確認のため、GitHub に最新のバージョン番号を問い合わせます。」とする。末尾（データの保存先の説明の下）に免責事項（README の「免責事項」と同じ文言）を同じ小さめの文字で表示する
2. **制限付き設定の許可**（Android 13 以上のみ表示）:
   「ストア外からインストールしたアプリはユーザー補助を有効にできないことがあります。アプリ情報 → 右上の︙ → 『制限付き設定を許可』をタップしてください」と説明し、`Settings.ACTION_APPLICATION_DETAILS_SETTINGS` を開くボタンを置く
3. **ユーザー補助（Accessibility）を有効化**: `Settings.ACTION_ACCESSIBILITY_SETTINGS` を開くボタンを置き、有効化されているかを判定して表示する
4. **電池の最適化から除外**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 権限を宣言し、`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` で依頼する。状態は `PowerManager.isIgnoringBatteryOptimizations` で判定する
5. **メーカー別の追加設定**: `Build.MANUFACTURER`（大文字小文字を区別しない）が OPPO または motorola の場合のみ表示する。それ以外のメーカーではこのステップ自体を表示しない
   - OPPO: 「設定 → バッテリー → アプリのバッテリー管理 → Memory Gate で『バックグラウンド実行を許可』『自動起動を許可』をオン」
   - motorola: 「設定 → アプリ → Memory Gate → アプリのバッテリー使用量 → 『制限なし』を選択」
   - このステップは自己申告で「完了」にする
6. **誘導先を登録**（1 件以上。スキップ可）
7. **監視対象アプリを選択**（1 件以上。スキップ可）

### 7.2 ホーム

- 上部: サービス状態のバナー。Accessibility が無効なら赤で警告し、タップでオンボーディングの該当ステップへ移動する
- 新しいバージョンのお知らせバナー（サービス状態のバナーがあるときはその下）:
  - 確認のタイミング: ホーム画面を表示したとき（`onResume`）。前回の確認（`updateLastCheckedAt`）から 1 時間以上たっている場合だけ問い合わせる。ゲート画面・オンボーディング・AccessibilityService の中では確認しない
  - 確認の方法: `GET https://api.github.com/repos/tortillaproduction/memory-gate/releases/latest`（認証なし、`Accept: application/vnd.github+json`、接続・読み込みのタイムアウト 10 秒、`HttpURLConnection` と `org.json`、IO ディスパッチャ）。`tag_name` から先頭の `v` を除き、`BuildConfig.VERSION_NAME` と major.minor.patch を数値で比較する（`0.1.10` > `0.1.9`）。新しければバージョン番号と `html_url` を保存する
  - 通信エラー・解析エラー・レート制限などの失敗時は何も表示せず、ログだけ残す。失敗した場合も `updateLastCheckedAt` は更新する
  - 表示条件: 保存された最新バージョンが `BuildConfig.VERSION_NAME` より新しく、かつ「×」で閉じたバージョン（`updateDismissedVersion`）より新しい（または閉じていない）とき。アップデートして現在のバージョンが最新になったら表示しない
  - 文言「新しいバージョン v0.1.3 が公開されています」（バージョン番号は保存された値）
  - ボタン「ダウンロードページを開く」: `html_url` を Custom Tabs で開く
  - 「×」ボタン: そのバージョンについてはバナーを閉じる（閉じたバージョン番号を保存する）。それより新しいバージョンが出たら、また表示する
- スヌーズの状態（サービス状態・お知らせのバナーの下に 1 行）: 例「スヌーズ：ON（30分おき 9:00〜22:00）」「スヌーズ：OFF」。タップでスヌーズ設定画面（7.6）を開く
- 「今日はゲート通過済み」の表示（gatePassedDate == today のとき）
- 誘導先一覧: タイトル、スケジュール、最終訪問、「期限切れ」バッジ、次の期限日。タップで編集画面
- FAB: 誘導先を追加
- メニュー: 監視対象アプリ、スヌーズ設定（7.6）、オンボーディングを再表示、アプリ情報（バージョンと免責事項。免責事項は README の「免責事項」と同じ文言）
- アプリ情報の「アップデートを確認」ボタン: 前回の確認日時にかかわらず、すぐに問い合わせる（問い合わせ方法・結果の保存・`updateLastCheckedAt` の更新はお知らせバナーの確認と同じ）
  - 問い合わせ中はボタンを無効にし、文言を「確認中…」にする
  - 結果をボタンの下に表示する。新しいバージョンがあれば「新しいバージョン v0.1.x が公開されています」と「ダウンロードページを開く」ボタン（`html_url` を Custom Tabs で開く）。最新なら「最新のバージョンです」。失敗したら「確認できませんでした。通信状態を確認して、しばらくしてからもう一度お試しください」
  - アプリ情報を閉じたら結果の表示は消す

### 7.3 誘導先の編集

- タイトル（必須）
- 種別の切り替え: Web サイト / アプリ
  - Web: URL 入力（バリデーションあり）
  - アプリ: アプリ選択ダイアログ（6 の一覧から単一選択）
- スケジュール: 1日ごと / 3日ごと / 1週間ごと / 毎週〇曜日（曜日のチップ）
- 見出し「スヌーズ用ゲートの画像（任意）」（v0.1.7 から。スヌーズの ON/OFF・間隔・時間帯はスヌーズ設定画面（7.6）にあり、ここにはない）。スヌーズが OFF でも設定できる
  - 画像: Photo Picker（`PickVisualMedia`、権限不要）で選ぶ。丸く切り抜いたプレビュー（スヌーズ用ゲートの丸い画像と同じく `ContentScale.Crop`・`Alignment.Center`）と「画像を削除」ボタンを付ける
  - 選んだ画像は縦横比を保って長辺 1024px 程度に縮小し（EXIF の向きも反映する）、アプリ内部ストレージにコピーする。画像を変更・削除したとき、または誘導先を削除したときは古いファイルも削除する
- 最終訪問日の表示と「未訪問に戻す」ボタン
- 削除（確認ダイアログあり）

### 7.4 監視対象アプリの選択

- アイコン・アプリ名・チェックボックスの一覧（6 の除外ルールを適用）
- 検索ボックス、「選択中のみ表示」フィルタ、「システムアプリも表示」スイッチ（既定はオフ）

### 7.5 ゲート（4.4〜4.6 参照）

- 全画面表示。見出し「やることがあります」、期限切れの誘導先を最大 5 件表示
- 最下部に緊急退避の長押しボタン

#### 7.5.1 スヌーズ用ゲート（4.7・5 章参照）

- 背景: 画面全体を隙間なく覆う（`ContentScale.Crop`、ステータスバーとナビゲーションバーの裏まで edge-to-edge）
  - 6:00〜18:00: 入道雲が浮かぶ真っ青な空の画像
  - それ以外: 満天の星空の画像
  - 画像は CC0 またはパブリックドメインの写真を使い、出典・作者・ライセンスを `docs/ASSETS.md` に記録する（WebP、長辺 1600px 程度、1 枚 500KB 以下、`res/drawable-nodpi/`）
- 中央やや上寄り（円の中心が画面の高さの約 35% の位置）に、丸く切り抜いた画像（白い細い縁取り）
  - 直径は画面の幅の 75% 程度（上限 320dp）。円の下のタイトル・ボタン類が円や最下部の緊急退避ボタンと重ならないようにし、小さい画面ではボタン類を優先して円を縮める
  - 画像は縦横比を変えずに円いっぱいに表示する（`ContentScale.Crop`、`Alignment.Center`。画像の中心を円の中心に合わせる）
  - 表示する誘導先（4.7 の `selectSnoozeTarget` で選んだもの）の `snoozeImagePath` があればその画像。なければ type=APP は誘導先アプリのアイコン、type=URL は地球のアイコン
- その下に誘導先のタイトルと「開く」ボタン（4.4 と同じ処理）
- 「開く」ボタンの下に、控えめなテキストボタン「スヌーズを止める」（白文字、読みやすいよう半透明の下地付き）
  - 押すと確認ダイアログを出す:「スヌーズを OFF にしますか？スヌーズ設定からいつでも ON に戻せます。」［OFF にする］［キャンセル］
  - 「OFF にする」: 全体の `snoozeEnabled = false` にする（スヌーズ設定画面でスイッチを OFF にしたのと同じ。間隔・時間帯の設定は残す）。その後、スヌーズ用ゲートを閉じて元のアプリに戻る（ホームへは移動しない）
  - `lastVisitedAt` と `gatePassedDate` は更新しない
- 最下部に緊急退避ボタン（4.5 と同じ）。戻る操作はホームへ移動する（4.6 と同じ）
- 文字は背景の上でも読めるよう、影や半透明の下地を付ける

### 7.6 スヌーズ設定（v0.1.7）

ホームのメニュー「スヌーズ設定」、またはホームのスヌーズの状態表示から開く。設定はアプリ全体で 1 つ（3.3・4.7）。変更はすぐに保存する。

- ON/OFF スイッチ。ON のときだけ以下の設定を表示する。OFF にしても設定値は保持する（再び ON にしたときに元に戻る）
  - スイッチの左に見出し「スヌーズ」と説明文「時間帯の間、指定の間隔で表示します」を置く。説明文は 1 行に収め（`maxLines = 1`、はみ出す場合は `TextOverflow.Ellipsis`）、文字の列は `weight(1f)` で幅を取り、スイッチとの間に 16dp 以上あける
- 間隔: 「1分おき」「5分おき」「30分おき」「1時間おき」のチップから 1 つ選ぶ（初期値は 30分おき）
- 時間帯: 開始時刻・終了時刻（TimePicker、初期値は 9:00〜22:00）。開始＞終了は日付をまたぐ、開始＝終了は 24 時間ずっと、の補足を表示する

---

## 8. 配布・CI

- `.github/workflows/build.yml`: push と PR で `./gradlew lint testDebugUnitTest assembleDebug` を実行する
- `.github/workflows/release.yml`: `v*` タグの push で署名済みの release APK をビルドし、GitHub Release に添付する
  - 署名情報は Secrets から取得する: `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`
  - `app/build.gradle.kts` では、環境変数がなければ release の署名設定をスキップする（ローカルで debug ビルドが通るように）
- `versionCode` / `versionName` はタグから導出するか、`gradle.properties` で管理する
- R8（minify）は有効にする。Room 等に必要な keep ルールを確認すること
- keystore やパスワードを**リポジトリにコミットしない**。`.gitignore` に `*.jks`、`*.keystore`、`keystore.properties`、`local.properties` を含める
- `README.md` に以下を記載する: インストール手順（提供元不明のアプリの許可、Play プロテクトの警告）、keystore の作成方法（`keytool` コマンド例）と Secrets の登録手順、keystore を失うとアップデートできないためバックアップが必要なこと

---

## 9. 判断に迷ったとき

- 仕様の欠落は「最小限で素直な実装」を選び、`docs/DECISIONS.md` に判断内容と理由を追記する
- この文書に書かれた挙動（特に 4 章）は変更しない。変更が必要だと判断した場合は実装せず、DECISIONS.md に提案として記録する
- スコープ外（実装しないこと）: サーバー / 同期、Chrome 等ブラウザ内の URL 監視、スキップ機能、統計画面、PIN ロック、多言語対応

---

## 10. 実装の進め方と完了条件

以下の順にコミットを分けて進めること。

1. プロジェクト雛形（Gradle、Version Catalog、Compose、`.gitignore`、CI の build.yml）
2. ドメインロジック（`isDue` / `selectGateTargets` / `shouldShowGate`）とユニットテスト
   - テストケース: 未訪問、1/3/7 日の境界、WEEKLY の当日訪問・前週訪問・曜日をまたぐケース、未来日時、5 件の上限と並び順、gatePassedDate が当日・前日
3. Room、DataStore、Repository、AppContainer
4. アプリ一覧と除外ルール、監視対象アプリの選択画面
5. 誘導先の一覧・編集画面
6. AccessibilityService とゲート画面（緊急退避ボタンを含む）
7. オンボーディング
8. release.yml、R8、README

**完了条件**
- `./gradlew lint testDebugUnitTest assembleDebug` がエラーなく通る
- 4 章のロジックがユニットテストでカバーされている
- README の手順どおりに署名済み APK を作成できる
- 実機での手動確認手順を `docs/MANUAL_TEST.md` にチェックリストとして残す（オンボーディング、ゲート表示、ボタン押下後に当日は出ないこと、緊急退避、戻るボタン、除外アプリが一覧に出ないこと、再起動後もサービスが動くこと）
