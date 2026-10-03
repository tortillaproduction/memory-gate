package app.memorygate.gate

import app.memorygate.domain.GateDecision
import app.memorygate.domain.ScheduleType
import app.memorygate.domain.SnoozeSettings
import app.memorygate.domain.Target
import app.memorygate.domain.TargetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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

/** キャッシュを使ったスヌーズの判定（設定はアプリ全体で 1 つ）と、表示直後の二重表示の防止 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnoozeCacheTest {
    private val zone = ZoneId.of("Asia/Tokyo")

    /** 2026-10-02 12:00 JST */
    private val now = Instant.parse("2026-10-02T03:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val today = LocalDate.of(2026, 10, 2)

    private val target = Target(
        id = 1,
        title = "t",
        type = TargetType.URL,
        url = "https://example.com",
        scheduleType = ScheduleType.EVERY_N_DAYS,
        intervalDays = 1,
        createdAt = 0,
    )

    private fun TestScope.cache(targets: FakeTargetRepository, settings: FakeSettingsRepository) = GateStateCache(
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        targets,
        FakeGuardedAppRepository(setOf("com.sns")),
        settings,
        clock,
    )

    private fun snoozeOn() = FakeSettingsRepository().apply {
        passed.value = today
        snooze.value = SnoozeSettings(enabled = true, intervalMinutes = 30)
    }

    @Test
    fun snoozeAfterGatePassedAndNoDoubleShow() = runTest {
        val cache = cache(FakeTargetRepository(listOf(target)), snoozeOn())
        assertEquals(GateDecision.Snooze(target), cache.decide("com.sns"))
        assertEquals(GateDecision.None, cache.decide("com.other"))
        assertEquals(now, cache.nextSnoozeReadyAt())

        // 表示した直後は、DataStore の反映を待たずに次の表示まで間隔をあける
        cache.markSnoozeShown(now.toEpochMilli())
        assertEquals(GateDecision.None, cache.decide("com.sns"))
        assertEquals(now.plusSeconds(30 * 60), cache.nextSnoozeReadyAt())
    }

    @Test
    fun usesSavedLastShownAt() = runTest {
        val settings = snoozeOn().apply { lastSnoozeShown.value = now.minusSeconds(10 * 60).toEpochMilli() }
        val cache = cache(FakeTargetRepository(listOf(target)), settings)
        assertEquals(GateDecision.None, cache.decide("com.sns"))
        assertEquals(now.plusSeconds(20 * 60), cache.nextSnoozeReadyAt())
    }

    @Test
    fun normalGateWinsWhenNotPassedToday() = runTest {
        val settings = snoozeOn().apply { passed.value = null }
        assertEquals(GateDecision.Normal, cache(FakeTargetRepository(listOf(target)), settings).decide("com.sns"))
    }

    /** スヌーズ用ゲートの「開く」の後も、間隔が経過すればまたスヌーズ用ゲートを出す */
    @Test
    fun snoozeContinuesAfterOpen() = runTest {
        val targets = FakeTargetRepository(listOf(target))
        val settings = snoozeOn()
        val cache = cache(targets, settings)
        cache.markSnoozeShown(now.toEpochMilli())
        GateInteractor(targets, settings, clock).onTargetOpened(1)

        assertEquals(now.toEpochMilli(), targets.getTarget(1)?.lastVisitedAt)
        assertEquals(today, settings.passed.value)
        assertEquals(GateDecision.None, cache.decide("com.sns"))
        assertEquals(now.plusSeconds(30 * 60), cache.nextSnoozeReadyAt())
    }

    /** 「スヌーズを止める」: 全体のスヌーズだけ OFF にし、間隔・時間帯と lastVisitedAt・gatePassedDate は変えない */
    @Test
    fun stopSnoozeTurnsOffGlobalSnooze() = runTest {
        val targets = FakeTargetRepository(listOf(target))
        val settings = snoozeOn().apply {
            passed.value = LocalDate.of(2026, 10, 1)
            snooze.value = SnoozeSettings(enabled = true, intervalMinutes = 5, startMinutes = 8 * 60, endMinutes = 23 * 60)
        }
        val cache = cache(targets, settings)

        GateInteractor(targets, settings, clock).onSnoozeStopped()

        assertEquals(SnoozeSettings(enabled = false, intervalMinutes = 5, startMinutes = 8 * 60, endMinutes = 23 * 60), settings.snooze.value)
        assertNull(targets.getTarget(1)?.lastVisitedAt)
        assertEquals(LocalDate.of(2026, 10, 1), settings.passed.value)
        // OFF にしたら表示しない（通過済みの日はどちらのゲートも出ない）
        settings.passed.value = today
        assertEquals(GateDecision.None, cache.decide("com.sns"))
        assertNull(cache.nextSnoozeReadyAt())
        assertFalse(settings.snooze.value.enabled)
    }

    @Test
    fun noSnoozeWithoutTargets() = runTest {
        val cache = cache(FakeTargetRepository(emptyList()), snoozeOn())
        assertEquals(GateDecision.None, cache.decide("com.sns"))
        assertNull(cache.nextSnoozeReadyAt())
    }

    /** 切り替え時は間隔に関係なく表示する。「開く」で開いた先のパッケージでは、間隔が経過するまで即時表示しない */
    @Test
    fun switchShowsImmediatelyExceptInOpenedPackage() = runTest {
        val cache = GateStateCache(
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            FakeTargetRepository(listOf(target)),
            FakeGuardedAppRepository(setOf("com.sns", "com.android.chrome")),
            snoozeOn(),
            clock,
        )
        cache.markSnoozeShown(now.minusSeconds(60).toEpochMilli())
        // 定期表示（切り替えではない）は間隔を待つ
        assertEquals(GateDecision.None, cache.decide("com.sns"))
        // 切り替え時はすぐ表示する
        assertEquals(GateDecision.Snooze(target), cache.decide("com.sns", switched = true))

        // スヌーズ用ゲートの「開く」で開いたブラウザ（監視対象）では、即時表示しない（堂々巡りの防止）
        cache.markSnoozeOpened("com.android.chrome")
        assertEquals(GateDecision.None, cache.decide("com.android.chrome", switched = true))
        assertEquals(GateDecision.Snooze(target), cache.decide("com.sns", switched = true))
    }
}
