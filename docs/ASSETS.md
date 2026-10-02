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
| 内容 | サンフランシスコ・ピークス上空の天の川と満天の星空 |
| 出典 | https://commons.wikimedia.org/wiki/File:Milky_Way_over_the_San_Francisco_Peaks.jpg （元: https://www.flickr.com/photos/coconinonationalforest/38140800022/ ） |
| 作者 | Deborah Lee Soltesz / U.S. Forest Service, Coconino National Forest |
| ライセンス | CC0 1.0 Universal（パブリックドメイン提供。Flickr で作者が付与） https://creativecommons.org/publicdomain/zero/1.0/ |
| 加工 | Commons の 3840px サムネイル（元画像 7360×4912）から、天の川が入るよう縦長（9:16）にトリミングして 900×1600 に縮小し、WebP（品質 77、約 454KB）に変換 |

## 取得方法のメモ

- 2026-10-02 に Wikimedia Commons から取得。ライセンスは各ファイルページのカテゴリ（`CC-Zero`）と出典を確認した。
- Wikimedia のレート制限のため、元画像ではなく標準サイズ（幅 3840px）のサムネイルを使った。縦長に切り出したあと 900×1600 に縮小した（引き伸ばしはしていない）。
- 昼の画像は v0.1.4 の作業中に、淡い青空とわた雲の写真（BlueSkyWhiteClouds21.jpg）から、濃い青空と入道雲の写真に差し替えた。
