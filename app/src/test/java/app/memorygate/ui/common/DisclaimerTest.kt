package app.memorygate.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DisclaimerTest {

    /** アプリ内の免責事項が README の「免責事項」と同じ文言であること（ユニットテストの作業ディレクトリは app/） */
    @Test
    fun readmeContainsSameDisclaimer() {
        val readme = File("../README.md").readText()
        val section = readme.substringAfter("## 免責事項").substringBefore("\n## ")
        assertTrue("README の免責事項がアプリ内の文言と一致しません", section.trim() == DISCLAIMER_TEXT)
    }
}
