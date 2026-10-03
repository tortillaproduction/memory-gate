package app.memorygate.gate

import app.memorygate.domain.GateDecision
import app.memorygate.domain.ScheduleType
import app.memorygate.domain.Target
import app.memorygate.domain.TargetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** キャッシュを使ったスヌーズの判定と、表示直後の二重表示の防止 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnoozeCacheTest {
    private val zone = ZoneId.of("Asia/Tokyo")

    /** 2026-10-02 12:00 JST */
    private val now = Instant.parse("2026-10-02T03:00:00Z")
    private val clock = Clock.fixed(now, zone)

    private val snoozeTarget = Target(
        id = 1,
        title = "t",
        type = TargetType.URL,
        url = "https://example.com",
        scheduleType = ScheduleType.EVERY_N_DAYS,
        intervalDays = 1,
        createdAt = 0,
        snoozeEnabled = true,
        snoozeIntervalMinutes = 30,
        snoozeStartMinutes = 9 * 60,
        snoozeEndMinutes = 22 * 60,
    )

    @Test
    fun snoozeAfterGatePassedAndNoDoubleShow() = runTest {
        val settings = FakeSettingsRepository().apply { passed.value = LocalDate.of(2026, 10, 2) }
        val cache = GateStateCache(
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            FakeTargetRepository(listOf(snoozeTarget)),
            FakeGuardedAppRepository(setOf("com.sns")),
            settings,
            clock,
        )
        assertEquals(GateDecision.Snooze(snoozeTarget), cache.decide("com.sns"))
        assertEquals(GateDecision.None, cache.decide("com.other"))
        assertEquals(now, cache.nextSnoozeReadyAt())

        // 表示した直後は、DB の反映を待たずに次の表示まで間隔をあける
        cache.markSnoozeShown(1, now.toEpochMilli())
        assertEquals(GateDecision.None, cache.decide("com.sns"))
        assertEquals(now.plusSeconds(30 * 60), cache.nextSnoozeReadyAt())
    }

    @Test
    fun normalGateWinsWhenNotPassedToday() = runTest {
        val cache = GateStateCache(
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            FakeTargetRepository(listOf(snoozeTarget)),
            FakeGuardedAppRepository(setOf("com.sns")),
            FakeSettingsRepository(),
            clock,
        )
        assertEquals(GateDecision.Normal, cache.decide("com.sns"))
    }

    /** スヌーズ用ゲートの「開く」の後も、間隔が経過すればまたスヌーズ用ゲートを出す（v0.1.6） */
    @Test
    fun snoozeContinuesAfterOpen() = runTest {
        val targets = FakeTargetRepository(listOf(snoozeTarget))
        val settings = FakeSettingsRepository()
        val cache = GateStateCache(
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            targets,
            FakeGuardedAppRepository(setOf("com.sns")),
            settings,
            clock,
        )
        cache.markSnoozeShown(1, now.toEpochMilli())
        GateInteractor(targets, settings, clock).onTargetOpened(1)

        assertEquals(now.toEpochMilli(), targets.getTarget(1)?.lastVisitedAt)
        assertEquals(LocalDate.of(2026, 10, 2), settings.passed.value)
        assertEquals(GateDecision.None, cache.decide("com.sns"))
        // 訪問済み（期限切れではない）でも、前回の表示から 30 分後に再びスヌーズできる
        assertEquals(now.plusSeconds(30 * 60), cache.nextSnoozeReadyAt())
    }

    /** 「スヌーズを止める」: スヌーズだけ OFF にし、ほかの設定と lastVisitedAt・gatePassedDate は変えない */
    @Test
    fun stopSnoozeTurnsOffOnlySnooze() = runTest {
        val image = "/data/snooze_images/a.jpg"
        val targets = FakeTargetRepository(listOf(snoozeTarget.copy(snoozeImagePath = image)))
        val settings = FakeSettingsRepository().apply { passed.value = LocalDate.of(2026, 10, 1) }
        val cache = GateStateCache(
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            targets,
            FakeGuardedAppRepository(setOf("com.sns")),
            settings,
            clock,
        )

        GateInteractor(targets, settings, clock).onSnoozeStopped(1)

        val stopped = targets.getTarget(1)!!
        assertFalse(stopped.snoozeEnabled)
        assertEquals(30, stopped.snoozeIntervalMinutes)
        assertEquals(9 * 60, stopped.snoozeStartMinutes)
        assertEquals(22 * 60, stopped.snoozeEndMinutes)
        assertEquals(image, stopped.snoozeImagePath)
        assertNull(stopped.lastVisitedAt)
        assertEquals(LocalDate.of(2026, 10, 1), settings.passed.value)
        // OFF にしたら表示しない（通常のゲートは期限切れなので出る。通過済みの日はどちらも出ない）
        settings.passed.value = LocalDate.of(2026, 10, 2)
        assertEquals(GateDecision.None, cache.decide("com.sns"))
        assertNull(cache.nextSnoozeReadyAt())
    }
}
