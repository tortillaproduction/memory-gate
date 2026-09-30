package app.memorygate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class GateLogicTest {

    private val zone: ZoneId = ZoneId.of("Asia/Tokyo")

    /** 2026-09-30 は水曜日 */
    private val today: LocalDate = LocalDate.of(2026, 9, 30)

    private fun millis(date: LocalDate, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun everyN(days: Int, lastVisited: LocalDate?, id: Long = 1, createdAt: Long = 0) = Target(
        id = id,
        title = "t$id",
        type = TargetType.URL,
        url = "https://example.com",
        scheduleType = ScheduleType.EVERY_N_DAYS,
        intervalDays = days,
        lastVisitedAt = lastVisited?.let { millis(it) },
        createdAt = createdAt,
    )

    private fun weekly(dayOfWeek: DayOfWeek, lastVisited: LocalDate?) = Target(
        title = "weekly",
        type = TargetType.APP,
        packageName = "com.example",
        scheduleType = ScheduleType.WEEKLY,
        dayOfWeek = dayOfWeek.value,
        lastVisitedAt = lastVisited?.let { millis(it) },
        createdAt = 0,
    )

    // ---- 4.1 isDue ----

    @Test
    fun `未訪問は期限切れ`() {
        assertTrue(GateLogic.isDue(everyN(1, null), today, zone))
        assertTrue(GateLogic.isDue(everyN(7, null), today, zone))
        assertTrue(GateLogic.isDue(weekly(DayOfWeek.MONDAY, null), today, zone))
    }

    @Test
    fun `1日ごと - 当日訪問は期限切れでなく翌日に期限切れ`() {
        val t = everyN(1, LocalDate.of(2026, 9, 30))
        assertFalse(GateLogic.isDue(t, LocalDate.of(2026, 9, 30), zone))
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 10, 1), zone))
    }

    @Test
    fun `1日ごと - 23時59分に訪問しても翌日0時で期限切れ`() {
        val t = everyN(1, null).copy(lastVisitedAt = millis(LocalDate.of(2026, 9, 30), 23, 59))
        assertFalse(GateLogic.isDue(t, LocalDate.of(2026, 9, 30), zone))
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 10, 1), zone))
    }

    @Test
    fun `3日ごと - 境界`() {
        val t = everyN(3, LocalDate.of(2026, 9, 27))
        assertFalse(GateLogic.isDue(t, LocalDate.of(2026, 9, 28), zone))
        assertFalse(GateLogic.isDue(t, LocalDate.of(2026, 9, 29), zone))
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 9, 30), zone))
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 10, 5), zone))
    }

    @Test
    fun `7日ごと - 境界`() {
        val t = everyN(7, LocalDate.of(2026, 9, 23))
        assertFalse(GateLogic.isDue(t, LocalDate.of(2026, 9, 29), zone))
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 9, 30), zone))
    }

    @Test
    fun `毎週月曜 - 先週金曜に訪問して今日が月曜なら期限切れ`() {
        val t = weekly(DayOfWeek.MONDAY, LocalDate.of(2026, 9, 25)) // 金
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 9, 28), zone)) // 月
    }

    @Test
    fun `毎週月曜 - 当日の月曜に訪問済みなら翌週月曜まで期限切れにならない`() {
        val t = weekly(DayOfWeek.MONDAY, LocalDate.of(2026, 9, 28)) // 月
        assertFalse(GateLogic.isDue(t, LocalDate.of(2026, 9, 28), zone))
        assertFalse(GateLogic.isDue(t, LocalDate.of(2026, 9, 30), zone))
        assertFalse(GateLogic.isDue(t, LocalDate.of(2026, 10, 4), zone)) // 日
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 10, 5), zone)) // 翌月
    }

    @Test
    fun `毎週月曜 - 前週に訪問していれば今週の月曜以降は期限切れ`() {
        val t = weekly(DayOfWeek.MONDAY, LocalDate.of(2026, 9, 21)) // 前週の月曜
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 9, 28), zone))
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 9, 30), zone))
    }

    @Test
    fun `毎週月曜 - 曜日をまたぐ - 火曜訪問なら次の月曜の前日までは期限切れでない`() {
        val t = weekly(DayOfWeek.MONDAY, LocalDate.of(2026, 9, 29)) // 火
        assertFalse(GateLogic.isDue(t, LocalDate.of(2026, 9, 30), zone)) // 水
        assertFalse(GateLogic.isDue(t, LocalDate.of(2026, 10, 4), zone)) // 日
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 10, 5), zone)) // 月
    }

    @Test
    fun `毎週金曜 - 月曜に訪問なら同じ週の金曜に期限切れ`() {
        val t = weekly(DayOfWeek.FRIDAY, LocalDate.of(2026, 9, 28)) // 月
        assertFalse(GateLogic.isDue(t, LocalDate.of(2026, 10, 1), zone)) // 木
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 10, 2), zone)) // 金
    }

    @Test
    fun `毎週日曜 - 週の境界（日曜=7）をまたぐ`() {
        val t = weekly(DayOfWeek.SUNDAY, LocalDate.of(2026, 9, 26)) // 土
        assertTrue(GateLogic.isDue(t, LocalDate.of(2026, 9, 27), zone)) // 日
        val visitedSunday = weekly(DayOfWeek.SUNDAY, LocalDate.of(2026, 9, 27))
        assertFalse(GateLogic.isDue(visitedSunday, LocalDate.of(2026, 10, 3), zone)) // 土
        assertTrue(GateLogic.isDue(visitedSunday, LocalDate.of(2026, 10, 4), zone)) // 日
    }

    @Test
    fun `未来日時（時計の巻き戻り）は期限切れでない`() {
        val future = LocalDate.of(2026, 10, 10)
        assertFalse(GateLogic.isDue(everyN(1, future), today, zone))
        assertFalse(GateLogic.isDue(everyN(7, future), today, zone))
        assertFalse(GateLogic.isDue(weekly(DayOfWeek.WEDNESDAY, future), today, zone))
    }

    // ---- nextDueDate ----

    @Test
    fun `次の期限日`() {
        assertNull(GateLogic.nextDueDate(everyN(1, null), today, zone))
        assertEquals(
            LocalDate.of(2026, 10, 3),
            GateLogic.nextDueDate(everyN(3, LocalDate.of(2026, 9, 30)), today, zone),
        )
        assertEquals(
            LocalDate.of(2026, 10, 5),
            GateLogic.nextDueDate(weekly(DayOfWeek.MONDAY, LocalDate.of(2026, 9, 28)), today, zone),
        )
    }

    // ---- 4.2 selectGateTargets ----

    @Test
    fun `期限切れのみ抽出し、未訪問が最優先、最終訪問の昇順、同順位は作成日時の昇順`() {
        val notDue = everyN(7, today, id = 1, createdAt = 0)
        val old = everyN(1, LocalDate.of(2026, 9, 1), id = 2, createdAt = 10)
        val newer = everyN(1, LocalDate.of(2026, 9, 20), id = 3, createdAt = 0)
        val neverB = everyN(1, null, id = 4, createdAt = 200)
        val neverA = everyN(1, null, id = 5, createdAt = 100)
        val sameDayLate = everyN(1, LocalDate.of(2026, 9, 1), id = 6, createdAt = 5)

        val result = GateLogic.selectGateTargets(listOf(notDue, old, newer, neverB, neverA, sameDayLate), today, zone)

        assertEquals(listOf(5L, 4L, 6L, 2L, 3L), result.map { it.id })
    }

    @Test
    fun `最大5件まで`() {
        val targets = (1L..8L).map { everyN(1, null, id = it, createdAt = 100 - it) }
        val result = GateLogic.selectGateTargets(targets, today, zone)
        assertEquals(5, result.size)
        assertEquals(listOf(8L, 7L, 6L, 5L, 4L), result.map { it.id })
    }

    @Test
    fun `期限切れがなければ空`() {
        assertTrue(GateLogic.selectGateTargets(listOf(everyN(1, today)), today, zone).isEmpty())
        assertTrue(GateLogic.selectGateTargets(emptyList(), today, zone).isEmpty())
    }

    // ---- 4.3 shouldShowGate ----

    private val guarded = setOf("com.sns")
    private val dueTargets = listOf(everyN(1, null))

    @Test
    fun `監視対象・未通過・期限切れありなら表示する`() {
        assertTrue(GateLogic.shouldShowGate("com.sns", today, zone, guarded, null, dueTargets))
    }

    @Test
    fun `gatePassedDate が前日なら表示する`() {
        assertTrue(GateLogic.shouldShowGate("com.sns", today, zone, guarded, today.minusDays(1), dueTargets))
    }

    @Test
    fun `gatePassedDate が当日なら表示しない`() {
        assertFalse(GateLogic.shouldShowGate("com.sns", today, zone, guarded, today, dueTargets))
    }

    @Test
    fun `監視対象外のアプリでは表示しない`() {
        assertFalse(GateLogic.shouldShowGate("com.other", today, zone, guarded, null, dueTargets))
    }

    @Test
    fun `期限切れがなければ表示しない`() {
        assertFalse(GateLogic.shouldShowGate("com.sns", today, zone, guarded, null, listOf(everyN(1, today))))
        assertFalse(GateLogic.shouldShowGate("com.sns", today, zone, guarded, null, emptyList()))
    }

    @Test
    fun `today は注入した Clock のローカル日付`() {
        // UTC 2026-09-30 16:00 は東京では 10/1 01:00
        val clock = Clock.fixed(java.time.Instant.parse("2026-09-30T16:00:00Z"), zone)
        assertEquals(LocalDate.of(2026, 10, 1), GateLogic.today(clock))
    }
}
