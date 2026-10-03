package app.memorygate.gate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 値は dp で計算する */
class SnoozeCircleLayoutTest {

    private val gap = 24f
    private val max = 320f
    private val min = 64f

    /** タイトル 1 行 + 「開く」ボタンなどの高さ */
    private val content = 150f

    /** 緊急退避ボタン + 余白 */
    private val bottom = 80f

    private fun diameter(width: Float, height: Float, contentHeight: Float = content) =
        SnoozeCircleLayout.diameter(width, height, contentHeight, bottom, gap, max, min)

    @Test
    fun usesThreeQuartersOfWidth() {
        // 幅 360dp・高さ 800dp: 360 × 0.75 = 270dp
        assertEquals(270f, diameter(360f, 800f), 0.01f)
    }

    @Test
    fun cappedAt320dp() {
        // タブレットなど幅が広い画面
        assertEquals(320f, diameter(800f, 1280f), 0.01f)
    }

    @Test
    fun shrinksSoThatButtonsDoNotOverlap() {
        // 幅 360dp・高さ 560dp（小さい画面）: 円の下端 + 余白 + 内容が最下部の領域に収まる大きさまで縮める
        val height = 560f
        val d = diameter(360f, height)
        assertTrue(d < 270f)
        val circleBottom = SnoozeCircleLayout.circleTop(height, d) + d
        assertTrue(circleBottom + gap + content <= height - bottom + 0.01f)
    }

    @Test
    fun circleCenterStaysAt35PercentOfHeight() {
        val height = 800f
        val d = diameter(360f, height)
        assertEquals(height * 0.35f, SnoozeCircleLayout.circleTop(height, d) + d / 2, 0.01f)
    }

    @Test
    fun circleDoesNotGoAboveTheScreen() {
        // 横長の画面: 中心（高さの 35%）から上端までに収まる大きさにする
        val height = 400f
        val d = diameter(900f, height, contentHeight = 0f)
        assertEquals(height * 0.35f * 2, d, 0.01f)
        assertEquals(0f, SnoozeCircleLayout.circleTop(height, d), 0.01f)
    }

    @Test
    fun neverSmallerThanMinimum() {
        assertEquals(min, diameter(360f, 300f), 0.01f)
    }
}
