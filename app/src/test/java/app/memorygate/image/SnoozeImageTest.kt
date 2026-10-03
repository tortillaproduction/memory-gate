package app.memorygate.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeImageTest {

    @Test
    fun scaledSizeKeepsAspectRatio() {
        assertEquals(1024 to 768, ImageScaling.scaledSize(4000, 3000, 1024))
        assertEquals(576 to 1024, ImageScaling.scaledSize(2160, 3840, 1024))
        assertEquals(1024 to 1024, ImageScaling.scaledSize(5000, 5000, 1024))
    }

    /** さまざまな大きさ・縦横比で、縮小後の縦横比が元と（丸め誤差の 1px 以内で）同じであること */
    @Test
    fun scaledSizeKeepsAspectRatioForManySizes() {
        val sizes = listOf(
            4032 to 3024, 3024 to 4032, 4000 to 1800, 1080 to 2400, 1920 to 1080, 6000 to 4000,
            3000 to 3001, 1025 to 7, 7 to 1025, 2049 to 1537,
        )
        for ((w, h) in sizes) {
            val (sw, sh) = ImageScaling.scaledSize(w, h, 1024)
            assertEquals("long side of ${w}x$h", 1024, maxOf(sw, sh))
            // 長辺から計算した短辺と 1px 以内（四捨五入）
            val expectedShort = minOf(w, h).toDouble() * 1024 / maxOf(w, h)
            assertTrue("aspect of ${w}x$h -> ${sw}x$sh", kotlin.math.abs(minOf(sw, sh) - expectedShort) <= 1.0)
            // 縦長・横長が入れ替わらない（ほぼ正方形は丸めで同じ長さになってよい）
            if (w > h) assertTrue("orientation of ${w}x$h", sw >= sh)
            if (h > w) assertTrue("orientation of ${w}x$h", sh >= sw)
        }
    }

    /** Android 8.x の経路（inSampleSize で間引いてから縮小）でも縦横比が保たれること */
    @Test
    fun sampledThenScaledKeepsAspectRatio() {
        for ((w, h) in listOf(4032 to 3024, 3024 to 4032, 4000 to 1800, 5000 to 5000)) {
            val sample = ImageScaling.sampleSize(w, h, 1024)
            // BitmapFactory は間引いた幅・高さを切り捨てる
            val (sw, sh) = ImageScaling.scaledSize(w / sample, h / sample, 1024)
            val ratio = w.toDouble() / h
            assertEquals("aspect of ${w}x$h -> ${sw}x$sh", ratio, sw.toDouble() / sh, ratio * 0.01)
        }
    }

    @Test
    fun smallImagesAreNotEnlarged() {
        assertEquals(800 to 600, ImageScaling.scaledSize(800, 600, 1024))
        assertEquals(1024 to 10, ImageScaling.scaledSize(1024, 10, 1024))
    }

    @Test
    fun sampleSize() {
        assertEquals(1, ImageScaling.sampleSize(1500, 1000, 1024))
        assertEquals(2, ImageScaling.sampleSize(2048, 1536, 1024))
        assertEquals(4, ImageScaling.sampleSize(4096, 3000, 1024))
        assertEquals(2, ImageScaling.sampleSize(4000, 3000, 1024))
    }

    @Test
    fun replacingImageDeletesOldOnSave() {
        val session = SnoozeImageSession(original = "old.jpg")
        session.onImported("a.jpg")
        session.onImported("b.jpg")
        assertEquals(listOf("old.jpg", "a.jpg"), session.filesToDeleteOnSave(saved = "b.jpg"))
    }

    @Test
    fun removingImageDeletesAllOnSave() {
        val session = SnoozeImageSession(original = "old.jpg")
        session.onImported("a.jpg")
        assertEquals(listOf("old.jpg", "a.jpg"), session.filesToDeleteOnSave(saved = null))
    }

    @Test
    fun keepingOriginalDeletesNothingOnSave() {
        assertEquals(emptyList<String>(), SnoozeImageSession(original = "old.jpg").filesToDeleteOnSave(saved = "old.jpg"))
        assertEquals(emptyList<String>(), SnoozeImageSession(original = null).filesToDeleteOnSave(saved = null))
    }

    @Test
    fun discardDeletesOnlyNewImports() {
        val session = SnoozeImageSession(original = "old.jpg")
        session.onImported("a.jpg")
        assertEquals(listOf("a.jpg"), session.filesToDeleteOnDiscard())
    }

    @Test
    fun deletingTargetDeletesEverything() {
        val session = SnoozeImageSession(original = "old.jpg")
        session.onImported("a.jpg")
        assertEquals(listOf("old.jpg", "a.jpg"), session.filesToDeleteOnTargetDeleted())
    }
}
