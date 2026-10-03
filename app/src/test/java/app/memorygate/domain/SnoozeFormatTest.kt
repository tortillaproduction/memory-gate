package app.memorygate.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeFormatTest {
    @Test
    fun interval() {
        assertEquals("1分おき", SnoozeFormat.interval(1))
        assertEquals("5分おき", SnoozeFormat.interval(5))
        assertEquals("30分おき", SnoozeFormat.interval(30))
        assertEquals("1時間おき", SnoozeFormat.interval(60))
    }

    @Test
    fun time() {
        assertEquals("9:00", SnoozeFormat.time(540))
        assertEquals("22:00", SnoozeFormat.time(1320))
        assertEquals("0:05", SnoozeFormat.time(5))
    }

    @Test
    fun windowNote() {
        assertEquals("日付をまたぎます（22:00〜翌2:00）", SnoozeFormat.windowNote(1320, 120))
        assertEquals("開始と終了が同じときは 24 時間ずっと知らせます", SnoozeFormat.windowNote(600, 600))
    }

    @Test
    fun statusLine() {
        assertEquals("スヌーズ：OFF", SnoozeFormat.status(SnoozeSettings()))
        assertEquals("スヌーズ：ON（30分おき 9:00〜22:00）", SnoozeFormat.status(SnoozeSettings(enabled = true)))
        assertEquals(
            "スヌーズ：ON（1時間おき 22:00〜2:00）",
            SnoozeFormat.status(SnoozeSettings(enabled = true, intervalMinutes = 60, startMinutes = 22 * 60, endMinutes = 2 * 60)),
        )
        assertEquals("スヌーズ：ON（5分おき 24時間）", SnoozeFormat.status(SnoozeSettings(enabled = true, intervalMinutes = 5, startMinutes = 0, endMinutes = 0)))
    }
}
