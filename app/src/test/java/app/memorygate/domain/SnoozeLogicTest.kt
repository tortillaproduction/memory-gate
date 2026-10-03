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

/** SPEC 4.7 スヌーズの判定（設定はアプリ全体で 1 つ。ロケールに依存しないよう ASCII のメソッド名にしている） */
class SnoozeLogicTest {

    private val zone: ZoneId = ZoneId.of("Asia/Tokyo")
    private val today: LocalDate = LocalDate.of(2026, 10, 2)
    private val guarded = setOf("com.sns")

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = today): Instant =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun settings(
        enabled: Boolean = true,
        interval: Int = 30,
        start: Int = 9 * 60,
        end: Int = 22 * 60,
    ) = SnoozeSettings(enabled = enabled, intervalMinutes = interval, startMinutes = start, endMinutes = end)

    private fun target(id: Long = 1, lastVisitedAt: Long? = null, createdAt: Long = id) = Target(
        id = id,
        title = "t$id",
        type = TargetType.URL,
        url = "https://example.com",
        scheduleType = ScheduleType.EVERY_N_DAYS,
        intervalDays = 1,
        lastVisitedAt = lastVisitedAt,
        createdAt = createdAt,
    )

    private fun decide(
        now: Instant,
        targets: List<Target> = listOf(target()),
        snooze: SnoozeSettings = settings(),
        lastShown: Long? = null,
        passed: LocalDate? = today,
        pkg: String = "com.sns",
        immediate: Boolean = false,
    ) = SnoozeLogic.decideGate(pkg, now, zone, guarded, passed, targets, snooze, lastShown, immediate)

    // ---- isInSnoozeWindow ----

    @Test
    fun windowNormalRange() {
        val s = settings(start = 9 * 60, end = 22 * 60)
        assertFalse(SnoozeLogic.isInSnoozeWindow(s, at(8, 59), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(s, at(9, 0), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(s, at(21, 59), zone))
        assertFalse(SnoozeLogic.isInSnoozeWindow(s, at(22, 0), zone))
    }

    @Test
    fun windowCrossesMidnight() {
        val s = settings(start = 22 * 60, end = 2 * 60)
        assertTrue(SnoozeLogic.isInSnoozeWindow(s, at(22, 0), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(s, at(23, 59), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(s, at(0, 0), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(s, at(1, 59), zone))
        assertFalse(SnoozeLogic.isInSnoozeWindow(s, at(2, 0), zone))
        assertFalse(SnoozeLogic.isInSnoozeWindow(s, at(12, 0), zone))
        assertFalse(SnoozeLogic.isInSnoozeWindow(s, at(21, 59), zone))
    }

    @Test
    fun windowStartEqualsEndMeansAllDay() {
        val s = settings(start = 10 * 60, end = 10 * 60)
        for (hour in 0..23) assertTrue(SnoozeLogic.isInSnoozeWindow(s, at(hour, 30), zone))
    }

    @Test
    fun defaultsAreOffAnd9To22Every30Minutes() {
        val s = SnoozeSettings()
        assertFalse(s.enabled)
        assertEquals(30, s.intervalMinutes)
        assertFalse(SnoozeLogic.isInSnoozeWindow(s, at(8, 59), zone))
        assertTrue(SnoozeLogic.isInSnoozeWindow(s, at(9, 0), zone))
        assertFalse(SnoozeLogic.isInSnoozeWindow(s, at(22, 0), zone))
    }

    // ---- isSnoozeReady ----

    @Test
    fun readyWhenAllConditionsHold() {
        assertTrue(SnoozeLogic.isSnoozeReady(settings(), null, at(12), zone))
    }

    @Test
    fun notReadyWhenDisabledOrOutsideWindow() {
        assertFalse(SnoozeLogic.isSnoozeReady(settings(enabled = false), null, at(12), zone))
        assertFalse(SnoozeLogic.isSnoozeReady(settings(), null, at(23), zone))
    }

    @Test
    fun intervalCountedFromGlobalLastShown() {
        val now = at(12)
        assertFalse(SnoozeLogic.isSnoozeReady(settings(interval = 30), now.minusSeconds(29 * 60).toEpochMilli(), now, zone))
        assertTrue(SnoozeLogic.isSnoozeReady(settings(interval = 30), now.minusSeconds(30 * 60).toEpochMilli(), now, zone))
        assertTrue(SnoozeLogic.isSnoozeReady(settings(interval = 1), now.minusSeconds(60).toEpochMilli(), now, zone))
        assertFalse(SnoozeLogic.isSnoozeReady(settings(interval = 60), now.minusSeconds(59 * 60).toEpochMilli(), now, zone))
    }

    // ---- selectSnoozeTarget ----

    @Test
    fun targetIsChosenByLastVisitedOrder() {
        // 未訪問が最優先 → lastVisitedAt の古い順 → createdAt の古い順。期限切れかどうかは問わない
        val visitedToday = target(id = 1, lastVisitedAt = at(8).toEpochMilli(), createdAt = 1)
        val visitedLongAgo = target(id = 2, lastVisitedAt = at(8, date = today.minusDays(5)).toEpochMilli(), createdAt = 2)
        val neverNewer = target(id = 3, createdAt = 30)
        val neverOlder = target(id = 4, createdAt = 20)
        assertEquals(4L, SnoozeLogic.selectSnoozeTarget(listOf(visitedToday, visitedLongAgo, neverNewer, neverOlder))?.id)
        assertEquals(2L, SnoozeLogic.selectSnoozeTarget(listOf(visitedToday, visitedLongAgo))?.id)
        // 期限切れでない（今日訪問済み）誘導先しかなくても選ぶ
        assertEquals(1L, SnoozeLogic.selectSnoozeTarget(listOf(visitedToday))?.id)
        assertNull(SnoozeLogic.selectSnoozeTarget(emptyList()))
    }

    @Test
    fun openingMovesToNextTarget() {
        // 「開く」で lastVisitedAt が更新されると、次回は別の誘導先が選ばれる
        val a = target(id = 1, lastVisitedAt = at(8).toEpochMilli())
        val b = target(id = 2, lastVisitedAt = at(9).toEpochMilli())
        assertEquals(1L, SnoozeLogic.selectSnoozeTarget(listOf(a, b))?.id)
        val aOpened = a.copy(lastVisitedAt = at(12).toEpochMilli())
        assertEquals(2L, SnoozeLogic.selectSnoozeTarget(listOf(aOpened, b))?.id)
    }

    // ---- decideGate ----

    @Test
    fun normalGateHasPriority() {
        assertEquals(GateDecision.Normal, decide(at(12), passed = null))
    }

    @Test
    fun snoozeWhenGateAlreadyPassedToday() {
        val t = target()
        assertEquals(GateDecision.Snooze(t), decide(at(12), listOf(t)))
    }

    @Test
    fun snoozeEvenWhenNoTargetIsDue() {
        // すべて今日訪問済み（期限切れなし）→ 通常のゲートは出ず、スヌーズ用ゲートは出る
        val t = target(lastVisitedAt = at(8).toEpochMilli())
        assertEquals(GateDecision.Snooze(t), decide(at(12), listOf(t), passed = null))
    }

    @Test
    fun noneWhenOffOutsideWindowIntervalOrNoTargets() {
        assertEquals(GateDecision.None, decide(at(12), pkg = "com.other"))
        assertEquals(GateDecision.None, decide(at(23)))
        assertEquals(GateDecision.None, decide(at(12), snooze = settings(enabled = false)))
        assertEquals(GateDecision.None, decide(at(12), lastShown = at(11, 45).toEpochMilli()))
        assertEquals(GateDecision.None, decide(at(12), targets = emptyList()))
    }

    @Test
    fun immediateIgnoresIntervalOnSwitch() {
        // 切り替え時は、前回の表示から間隔がたっていなくても表示する
        val t = target()
        val shownJustNow = at(11, 59).toEpochMilli()
        assertEquals(GateDecision.None, decide(at(12), listOf(t), lastShown = shownJustNow))
        assertEquals(GateDecision.Snooze(t), decide(at(12), listOf(t), lastShown = shownJustNow, immediate = true))
        // OFF・時間帯の外・誘導先なし・監視対象外では、切り替え時でも表示しない。通常のゲートが優先
        assertEquals(GateDecision.None, decide(at(12), snooze = settings(enabled = false), immediate = true))
        assertEquals(GateDecision.None, decide(at(23), immediate = true))
        assertEquals(GateDecision.None, decide(at(12), targets = emptyList(), immediate = true))
        assertEquals(GateDecision.None, decide(at(12), pkg = "com.other", immediate = true))
        assertEquals(GateDecision.Normal, decide(at(12), passed = null, immediate = true))
    }

    // ---- nextSnoozeReadyAt ----

    @Test
    fun nextReadyIsNowWhenAlreadyReady() {
        assertEquals(at(12), SnoozeLogic.nextSnoozeReadyAt(settings(), null, listOf(target()), at(12), zone))
    }

    @Test
    fun nextReadyAfterInterval() {
        val shown = at(12).toEpochMilli()
        assertEquals(at(12, 5), SnoozeLogic.nextSnoozeReadyAt(settings(interval = 5), shown, listOf(target()), at(12, 1), zone))
    }

    @Test
    fun nextReadyWaitsForWindowStart() {
        // 8:00 → 当日 9:00、23:00 → 翌日 9:00
        assertEquals(at(9), SnoozeLogic.nextSnoozeReadyAt(settings(), null, listOf(target()), at(8), zone))
        assertEquals(at(9, date = today.plusDays(1)), SnoozeLogic.nextSnoozeReadyAt(settings(), null, listOf(target()), at(23), zone))
        // 間隔が明けるのが時間帯の外（21:50 表示・60 分）→ 翌日 9:00
        val shown = at(21, 50).toEpochMilli()
        assertEquals(at(9, date = today.plusDays(1)), SnoozeLogic.nextSnoozeReadyAt(settings(interval = 60), shown, listOf(target()), at(21, 55), zone))
    }

    @Test
    fun nextReadyDoesNotWaitUntilDue() {
        // 今日訪問済みでも、期限切れになる翌日まで待たない
        val visited = target(lastVisitedAt = at(8).toEpochMilli())
        assertEquals(at(12), SnoozeLogic.nextSnoozeReadyAt(settings(), null, listOf(visited), at(12), zone))
    }

    @Test
    fun nextReadyNullWhenOffOrNoTargets() {
        assertNull(SnoozeLogic.nextSnoozeReadyAt(settings(enabled = false), null, listOf(target()), at(12), zone))
        assertNull(SnoozeLogic.nextSnoozeReadyAt(settings(), null, emptyList(), at(12), zone))
    }

    @Test
    fun nextReadyInCrossMidnightWindow() {
        val s = settings(start = 22 * 60, end = 2 * 60)
        assertEquals(at(22), SnoozeLogic.nextSnoozeReadyAt(s, null, listOf(target()), at(12), zone))
        assertEquals(at(1), SnoozeLogic.nextSnoozeReadyAt(s, null, listOf(target()), at(1), zone))
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
