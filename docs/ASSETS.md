# 画像素材の出典

アプリに同梱している画像素材の出典・作者・ライセンス。CC0 またはパブリックドメインのもの、かつ作者自身がそのライセンスを付けたことが確認できるものだけを使う（他サイトからの転載で、元サイトのライセンスが確認できないものは使わない）。

## スヌーズ用ゲートの背景

### `app/src/main/res/drawable-nodpi/snooze_bg_day.webp`（昼: 6:00〜18:00）

| 項目 | 内容 |
|---|---|
| 内容 | 濃い青空と、縦に立ち上がる入道雲（雄大積雲） |
| 出典 | https://commons.wikimedia.org/wiki/File:Cumulus_congestus_over_G%C3%A5seberg_1.jpg （スウェーデン・リーセシル市 Gåseberg 上空の雄大積雲、2018-06-11 撮影） |
| 作者 | W.carter（Wikimedia Commons、本人の撮影） |
| ライセンス | CC0 1.0 Universal（パブリックドメイン提供） https://creativecommons.org/publicdomain/zero/1.0/ |
| 加工 | Commons の 3840px サムネイル（元画像 4925×2770）から、雲の頂部が入るよう右寄りを縦長（9:16）にトリミングして 900×1600 に縮小し、WebP（品質 90、約 55KB）に変換 |

### `app/src/main/res/drawable-nodpi/snooze_bg_night.webp`（夜: 18:00〜翌 6:00）

| 項目 | 内容 |
|---|---|
| 内容 | 濃紺の夜空に星が全体に散らばった星空（地上の景色は含まない） |
| 出典 | https://commons.wikimedia.org/wiki/File:Stars_and_moonrise_over_Lysekil.jpg （スウェーデン・リーセシルの Stångehuvud から見た星空、2019-09-17 撮影） |
| 作者 | W.carter（Wikimedia Commons、本人の撮影） |
| ライセンス | CC0 1.0 Universal（パブリックドメイン提供） https://creativecommons.org/publicdomain/zero/1.0/ |
| 加工 | Commons の 1920px サムネイル（元画像 3283×4925、縦長）から、地平線・街の明かり・地上の景色が入らないよう上端から 900×1600 を左右中央で切り出し（縮小なし）、WebP（品質 90、約 438KB）に変換 |

## 取得方法のメモ

- 2026-10-02 に Wikimedia Commons から取得。ライセンスは各ファイルページのカテゴリ（`CC-Zero`）と出典を確認した。
- Wikimedia のレート制限のため、元画像ではなく標準サイズのサムネイルを使った（昼は幅 3840px、夜は幅 1920px）。どちらも 900×1600 で、引き伸ばしはしていない。
- 昼の画像は v0.1.4 の作業中に、淡い青空とわた雲の写真（BlueSkyWhiteClouds21.jpg）から、濃い青空と入道雲の写真に差し替えた。
- 夜の画像は v0.1.4 の作業中に、山の稜線が写った天の川の写真（Milky_Way_over_the_San_Francisco_Peaks.jpg）から、空だけを切り出した星空の写真に差し替えた。
