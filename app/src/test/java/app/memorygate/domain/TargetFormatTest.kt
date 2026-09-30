package app.memorygate.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TargetFormatTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.of(2026, 9, 30)

    private fun target(type: ScheduleType, interval: Int? = null, dow: Int? = null) = Target(
        title = "t",
        type = TargetType.URL,
        url = "https://example.com",
        scheduleType = type,
        intervalDays = interval,
        dayOfWeek = dow,
        createdAt = 0,
    )

    @Test
    fun `スケジュールの表示`() {
        assertEquals("1日ごと", TargetFormat.schedule(target(ScheduleType.EVERY_N_DAYS, 1)))
        assertEquals("3日ごと", TargetFormat.schedule(target(ScheduleType.EVERY_N_DAYS, 3)))
        assertEquals("1週間ごと", TargetFormat.schedule(target(ScheduleType.EVERY_N_DAYS, 7)))
        assertEquals("毎週月曜日", TargetFormat.schedule(target(ScheduleType.WEEKLY, dow = 1)))
        assertEquals("毎週日曜日", TargetFormat.schedule(target(ScheduleType.WEEKLY, dow = 7)))
    }

    @Test
    fun `最終訪問の表示`() {
        fun millis(d: LocalDate) = d.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("未訪問", TargetFormat.lastVisited(null, today, zone))
        assertEquals("最終: 今日", TargetFormat.lastVisited(millis(today), today, zone))
        assertEquals("最終: 1日前", TargetFormat.lastVisited(millis(today.minusDays(1)), today, zone))
        assertEquals("最終: 10日前", TargetFormat.lastVisited(millis(today.minusDays(10)), today, zone))
        assertEquals("最終: 今日", TargetFormat.lastVisited(millis(today.plusDays(2)), today, zone))
    }

    @Test
    fun `日付の表示`() {
        assertEquals("10/3(土)", TargetFormat.shortDate(LocalDate.of(2026, 10, 3)))
    }
}
