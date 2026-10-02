package app.memorygate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** SPEC 4.7 スヌーズの判定（ロケールに依存しないよう ASCII のメソッド名にしている） */
class SnoozeLogicTest {

    private val zone: ZoneId = ZoneId.of("Asia/Tokyo")
    private val today: LocalDate = LocalDate.of(2026, 10, 2)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = today): Instant =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun target(
        id: Long = 1,
        enabled: Boolean = true,
        interval: Int? = 30,
        start: Int? = 9 * 60,
        end: Int? = 22 * 60,
        lastVisitedAt: Long? = null,
        lastShown: Long? = null,
        createdAt: Long = id,
    ) = Target(
        id = id,
        title = "t$id",
        type = TargetType.URL,
        url = "https://example.com",
        scheduleType = ScheduleType.EVERY_N_DAYS,
        intervalDays = 1,
        lastVisitedAt = lastVisitedAt,
        createdAt = createdAt,
        snoozeEnabled = enabled,
        snoozeIntervalMinutes = interval,
        snoozeStartMinutes = start,
        snoozeEndMinutes = end,
        lastSnoozeShownAt = lastShown,
    )

    // ---- isInSnoozeWindow ----

    @Test
    fun windowNormalRange() {
        val t = target(start = 9 * 60, end = 22 * 60)
        assertFalse(SnoozeLogic.isInSnoozeWindow(t, at(8, 59), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(t, at(9, 0), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(t, at(21, 59), zone))
        assertFalse(SnoozeLogic.isInSnoozeWindow(t, at(22, 0), zone))
    }

    @Test
    fun windowCrossesMidnight() {
        val t = target(start = 22 * 60, end = 2 * 60)
        assertTrue(SnoozeLogic.isInSnoozeWindow(t, at(22, 0), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(t, at(23, 59), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(t, at(0, 0), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(t, at(1, 59), zone))
        assertFalse(SnoozeLogic.isInSnoozeWindow(t, at(2, 0), zone))
        assertFalse(SnoozeLogic.isInSnoozeWindow(t, at(12, 0), zone))
        assertFalse(SnoozeLogic.isInSnoozeWindow(t, at(21, 59), zone))
    }

    @Test
    fun windowStartEqualsEndMeansAllDay() {
        val t = target(start = 10 * 60, end = 10 * 60)
        for (hour in 0..23) assertTrue(SnoozeLogic.isInSnoozeWindow(t, at(hour, 30), zone))
    }

    @Test
    fun windowDefaultsTo9To22WhenUnset() {
        val t = target(start = null, end = null)
        assertFalse(SnoozeLogic.isInSnoozeWindow(t, at(8, 59), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(t, at(9, 0), zone))
        assertFalse(SnoozeLogic.isInSnoozeWindow(t, at(22, 0), zone))
    }

    // ---- isSnoozeReady ----

    @Test
    fun readyWhenAllConditionsHold() {
        assertTrue(SnoozeLogic.isSnoozeReady(target(), at(12), zone))
    }

    @Test
    fun notReadyWhenDisabled() {
        assertFalse(SnoozeLogic.isSnoozeReady(target(enabled = false), at(12), zone))
    }

    @Test
    fun notReadyWhenNotDue() {
        // 今日訪問済み（1日ごと）→ 期限切れではない
        val visitedToday = target(lastVisitedAt = at(8).toEpochMilli())
        assertFalse(SnoozeLogic.isSnoozeReady(visitedToday, at(12), zone))
    }

    @Test
    fun notReadyOutsideWindow() {
        assertFalse(SnoozeLogic.isSnoozeReady(target(), at(23), zone))
    }

    @Test
    fun intervalSinceLastShown() {
        val now = at(12)
        val shown29MinAgo = target(interval = 30, lastShown = now.minusSeconds(29 * 60).toEpochMilli())
        val shown30MinAgo = target(interval = 30, lastShown = now.minusSeconds(30 * 60).toEpochMilli())
        assertFalse(SnoozeLogic.isSnoozeReady(shown29MinAgo, now, zone))
        assertTrue(SnoozeLogic.isSnoozeReady(shown30MinAgo, now, zone))
        val shown1MinAgo = target(interval = 1, lastShown = now.minusSeconds(60).toEpochMilli())
        assertTrue(SnoozeLogic.isSnoozeReady(shown1MinAgo, now, zone))
        val shown59MinAgo = target(interval = 60, lastShown = now.minusSeconds(59 * 60).toEpochMilli())
        assertFalse(SnoozeLogic.isSnoozeReady(shown59MinAgo, now, zone))
    }

    @Test
    fun intervalDefaultsTo30Minutes() {
        val now = at(12)
        assertFalse(SnoozeLogic.isSnoozeReady(target(interval = null, lastShown = now.minusSeconds(29 * 60).toEpochMilli()), now, zone))
        assertTrue(SnoozeLogic.isSnoozeReady(target(interval = null, lastShown = now.minusSeconds(30 * 60).toEpochMilli()), now, zone))
    }

    // ---- selectSnoozeTarget ----

    @Test
    fun selectUsesGateOrder() {
        val now = at(12)
        val old = target(id = 1, lastVisitedAt = at(12, date = today.minusDays(5)).toEpochMilli(), createdAt = 1)
        val never2 = target(id = 2, createdAt = 20)
        val never3 = target(id = 3, createdAt = 10)
        val disabled = target(id = 4, enabled = false, createdAt = 0)
        assertEquals(3L, SnoozeLogic.selectSnoozeTarget(listOf(old, never2, never3, disabled), now, zone)?.id)
        assertEquals(1L, SnoozeLogic.selectSnoozeTarget(listOf(old, disabled), now, zone)?.id)
        assertNull(SnoozeLogic.selectSnoozeTarget(listOf(disabled), now, zone))
    }

    // ---- decideGate ----

    @Test
    fun normalGateHasPriority() {
        val decision = SnoozeLogic.decideGate("com.sns", at(12), zone, setOf("com.sns"), null, listOf(target()))
        assertEquals(GateDecision.Normal, decision)
    }

    @Test
    fun snoozeWhenGateAlreadyPassedToday() {
        val t = target()
        val decision = SnoozeLogic.decideGate("com.sns", at(12), zone, setOf("com.sns"), today, listOf(t))
        assertEquals(GateDecision.Snooze(t), decision)
    }

    @Test
    fun noneForUnguardedAppOrWhenNotReady() {
        assertEquals(GateDecision.None, SnoozeLogic.decideGate("com.other", at(12), zone, setOf("com.sns"), today, listOf(target())))
        assertEquals(GateDecision.None, SnoozeLogic.decideGate("com.sns", at(23), zone, setOf("com.sns"), today, listOf(target())))
        assertEquals(GateDecision.None, SnoozeLogic.decideGate("com.sns", at(12), zone, setOf("com.sns"), today, listOf(target(enabled = false))))
    }

    // ---- nextSnoozeReadyAt ----

    @Test
    fun nextReadyIsNowWhenAlreadyReady() {
        assertEquals(at(12), SnoozeLogic.nextSnoozeReadyAt(listOf(target()), at(12), zone))
    }

    @Test
    fun nextReadyAfterInterval() {
        val shown = at(12).toEpochMilli()
        assertEquals(at(12, 5), SnoozeLogic.nextSnoozeReadyAt(listOf(target(interval = 5, lastShown = shown)), at(12, 1), zone))
    }

    @Test
    fun nextReadyWaitsForWindowStart() {
        // 8:00 → 当日 9:00
        assertEquals(at(9), SnoozeLogic.nextSnoozeReadyAt(listOf(target()), at(8), zone))
        // 23:00 → 翌日 9:00
        assertEquals(at(9, date = today.plusDays(1)), SnoozeLogic.nextSnoozeReadyAt(listOf(target()), at(23), zone))
        // 間隔が明けるのが時間帯の外（21:50 表示・60 分）→ 翌日 9:00
        val shown = at(21, 50).toEpochMilli()
        assertEquals(at(9, date = today.plusDays(1)), SnoozeLogic.nextSnoozeReadyAt(listOf(target(interval = 60, lastShown = shown)), at(21, 55), zone))
    }

    @Test
    fun nextReadyWaitsUntilDue() {
        // 今日訪問済み（1日ごと）→ 翌日 0:00 に期限切れ、時間帯の開始 9:00 から
        val visited = target(lastVisitedAt = at(8).toEpochMilli())
        assertEquals(at(9, date = today.plusDays(1)), SnoozeLogic.nextSnoozeReadyAt(listOf(visited), at(12), zone))
        // 24 時間の時間帯なら翌日 0:00
        val allDay = target(lastVisitedAt = at(8).toEpochMilli(), start = 0, end = 0)
        assertEquals(at(0, date = today.plusDays(1)), SnoozeLogic.nextSnoozeReadyAt(listOf(allDay), at(12), zone))
    }

    @Test
    fun nextReadyIsEarliestAmongEnabledTargets() {
        val a = target(id = 1, interval = 60, lastShown = at(12).toEpochMilli())
        val b = target(id = 2, interval = 5, lastShown = at(12).toEpochMilli())
        val off = target(id = 3, enabled = false)
        assertEquals(at(12, 5), SnoozeLogic.nextSnoozeReadyAt(listOf(a, b, off), at(12, 1), zone))
        assertNull(SnoozeLogic.nextSnoozeReadyAt(listOf(off), at(12), zone))
    }

    @Test
    fun nextReadyInCrossMidnightWindow() {
        val t = target(start = 22 * 60, end = 2 * 60)
        assertEquals(at(22), SnoozeLogic.nextSnoozeReadyAt(listOf(t), at(12), zone))
        assertEquals(at(1), SnoozeLogic.nextSnoozeReadyAt(listOf(t), at(1), zone))
    }
}

class SnoozeBackgroundTest {
    private val zone: ZoneId = ZoneId.of("Asia/Tokyo")
    private fun at(hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(LocalDate.of(2026, 10, 2), LocalTime.of(hour, minute)).atZone(zone).toInstant()

    @Test
    fun dayFrom6To18() {
        assertFalse(SnoozeBackground.isDaytime(at(5, 59), zone))
        assertTrue(SnoozeBackground.isDaytime(at(6, 0), zone))
        assertTrue(SnoozeBackground.isDaytime(at(17, 59), zone))
        assertFalse(SnoozeBackground.isDaytime(at(18, 0), zone))
        assertFalse(SnoozeBackground.isDaytime(at(0, 0), zone))
    }
}
