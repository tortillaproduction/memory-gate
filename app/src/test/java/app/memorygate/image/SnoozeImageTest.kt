package app.memorygate.image

import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeImageTest {

    @Test
    fun scaledSizeKeepsAspectRatio() {
        assertEquals(1024 to 768, ImageScaling.scaledSize(4000, 3000, 1024))
        assertEquals(576 to 1024, ImageScaling.scaledSize(2160, 3840, 1024))
        assertEquals(1024 to 1024, ImageScaling.scaledSize(5000, 5000, 1024))
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
