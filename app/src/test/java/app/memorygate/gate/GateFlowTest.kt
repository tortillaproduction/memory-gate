package app.memorygate.gate

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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * SPEC 4.3〜4.4: キャッシュを使ったゲート判定と、ボタン押下時のデータ更新。
 * （コルーチンのラムダを含むテストは、生成クラス名がファイル名になるため ASCII のメソッド名にしている）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GateFlowTest {
    private val zone = ZoneId.of("Asia/Tokyo")

    /** 2026-09-30 10:00 JST */
    private val now = Instant.parse("2026-09-30T01:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val tomorrowClock = Clock.fixed(now.plusSeconds(24 * 60 * 60), zone)

    private fun daily(id: Long, lastVisitedAt: Long? = null) = Target(
        id = id,
        title = "t$id",
        type = TargetType.URL,
        url = "https://example.com",
        scheduleType = ScheduleType.EVERY_N_DAYS,
        intervalDays = 1,
        lastVisitedAt = lastVisitedAt,
        createdAt = id,
    )

    /** ボタンを押すと lastVisitedAt と gatePassedDate が更新され、当日はゲートが出ない */
    @Test
    fun openTarget_updatesDataAndSuppressesGateForToday() = runTest {
        val targets = FakeTargetRepository(listOf(daily(1), daily(2)))
        val guarded = FakeGuardedAppRepository(setOf("com.sns"))
        val settings = FakeSettingsRepository()
        val scope = backgroundScope
        val cache = GateStateCache(
            CoroutineScope(scope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            targets, guarded, settings, clock,
        )
        assertTrue(cache.shouldShowGate("com.sns"))
        assertFalse(cache.shouldShowGate("com.other"))

        GateInteractor(targets, settings, clock).onTargetOpened(1)

        assertEquals(now.toEpochMilli(), targets.getTarget(1)?.lastVisitedAt)
        assertNull(targets.getTarget(2)?.lastVisitedAt)
        assertEquals(LocalDate.of(2026, 9, 30), settings.passed.value)
        // 誘導先 2 はまだ期限切れだが、当日はゲートを出さない
        assertFalse(cache.shouldShowGate("com.sns"))

        // 翌日は期限切れがあれば再び出す
        val tomorrowCache = GateStateCache(
            CoroutineScope(scope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            targets, guarded, settings, tomorrowClock,
        )
        assertTrue(tomorrowCache.shouldShowGate("com.sns"))
    }

    /** 読み込み完了前は出さず、監視対象・誘導先の変更がキャッシュに反映される */
    @Test
    fun cacheReflectsRepositoryChanges() = runTest {
        val targets = FakeTargetRepository(listOf(daily(1)))
        val guarded = FakeGuardedAppRepository()
        val settings = FakeSettingsRepository()
        val cache = GateStateCache(
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            targets, guarded, settings, clock,
        )
        assertFalse(cache.shouldShowGate("com.sns"))
        guarded.setGuarded("com.sns", true)
        assertTrue(cache.shouldShowGate("com.sns"))
        targets.delete(1)
        assertFalse(cache.shouldShowGate("com.sns"))
    }
}
