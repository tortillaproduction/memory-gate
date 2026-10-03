package app.memorygate.gate

/**
 * スヌーズ用ゲートの丸い画像の大きさ（SPEC 7.5.1）。ユニットテストのため Compose から分離する。
 * 値の単位は呼び出し側でそろえる（px でも dp でもよい）。
 */
object SnoozeCircleLayout {
    /** 直径は画面の幅のこの割合 */
    const val WIDTH_RATIO = 0.75f

    /** 円の中心は画面の高さのこの割合の位置 */
    const val CENTER_RATIO = 0.35f

    /**
     * 円の直径。画面の幅の 75%（上限 [maxDiameter]）を基本にし、
     * 円の上端が画面からはみ出す場合や、円の下の内容（タイトル・ボタン類）が最下部の領域に重なる場合は縮める。
     * 縮めても [minDiameter] より小さくはしない。
     *
     * @param contentHeight 円の下に置く内容（タイトル・ボタン類）の高さ
     * @param bottomHeight 最下部に確保する領域（緊急退避ボタンとその上の余白）の高さ
     * @param gap 円と内容の間の余白
     */
    fun diameter(
        width: Float,
        height: Float,
        contentHeight: Float,
        bottomHeight: Float,
        gap: Float,
        maxDiameter: Float,
        minDiameter: Float,
    ): Float {
        val desired = minOf(width * WIDTH_RATIO, maxDiameter)
        val centerY = height * CENTER_RATIO
        // 円の上端が画面の上端より上に出ない
        val fitTop = 2 * centerY
        // 円の下端 + 余白 + 内容 が最下部の領域より上に収まる
        val fitBottom = 2 * (height - bottomHeight - contentHeight - gap - centerY)
        return minOf(desired, fitTop, fitBottom).coerceAtLeast(minDiameter)
    }

    /** 円の上端の位置（中心を画面の高さの 35% に置く。画面の上端より上には出さない） */
    fun circleTop(height: Float, diameter: Float): Float = (height * CENTER_RATIO - diameter / 2).coerceAtLeast(0f)
}
