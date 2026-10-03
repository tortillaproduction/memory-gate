package app.memorygate.data

import app.memorygate.domain.ScheduleType
import app.memorygate.domain.SnoozeSettings
import app.memorygate.domain.SnoozeSettingsMigration
import app.memorygate.domain.Target
import app.memorygate.domain.TargetType
import app.memorygate.gate.FakeSettingsRepository
import app.memorygate.gate.FakeTargetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.6 までの誘導先ごとのスヌーズ設定を、アプリ全体の設定へ引き継ぐ（SPEC 3.3） */
class SnoozeSettingsMigrationTest {

    private fun target(
        id: Long,
        enabled: Boolean,
        lastVisitedAt: Long? = null,
        createdAt: Long = id,
        interval: Int? = null,
        start: Int? = null,
        end: Int? = null,
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
    )

    @Test
    fun noEnabledTargetGivesDefaultsOff() {
        assertEquals(SnoozeSettings(), SnoozeSettingsMigration.fromTargets(emptyList()))
        val disabled = target(1, enabled = false, interval = 5, start = 60, end = 120)
        assertEquals(SnoozeSettings(), SnoozeSettingsMigration.fromTargets(listOf(disabled)))
    }

    @Test
    fun usesFirstEnabledTargetInGateOrder() {
        // 並び順: 未訪問が最優先 → lastVisitedAt の古い順 → createdAt の古い順。スヌーズ OFF の誘導先は対象外
        val visitedOld = target(1, enabled = true, lastVisitedAt = 100, interval = 60, start = 8 * 60, end = 20 * 60)
        val neverNewer = target(2, enabled = true, createdAt = 20, interval = 5, start = 10 * 60, end = 23 * 60)
        val neverOlder = target(3, enabled = true, createdAt = 10, interval = 1, start = 22 * 60, end = 2 * 60)
        val disabledFirst = target(4, enabled = false, createdAt = 0, interval = 60, start = 0, end = 0)
        assertEquals(
            SnoozeSettings(enabled = true, intervalMinutes = 1, startMinutes = 22 * 60, endMinutes = 2 * 60),
            SnoozeSettingsMigration.fromTargets(listOf(visitedOld, neverNewer, neverOlder, disabledFirst)),
        )
        assertEquals(
            SnoozeSettings(enabled = true, intervalMinutes = 60, startMinutes = 8 * 60, endMinutes = 20 * 60),
            SnoozeSettingsMigration.fromTargets(listOf(visitedOld, disabledFirst)),
        )
    }

    @Test
    fun nullValuesBecomeDefaults() {
        val t = target(1, enabled = true)
        assertEquals(SnoozeSettings(enabled = true, intervalMinutes = 30, startMinutes = 540, endMinutes = 1320), SnoozeSettingsMigration.fromTargets(listOf(t)))
        // 選択肢にない間隔も既定値にする
        assertEquals(30, SnoozeSettingsMigration.fromTargets(listOf(target(1, enabled = true, interval = 7))).intervalMinutes)
    }

    @Test
    fun migratorRunsOnlyOnce() = runTest {
        val targets = FakeTargetRepository(listOf(target(1, enabled = true, interval = 5)))
        val settings = FakeSettingsRepository()
        val migrator = SnoozeSettingsMigrator(targets, settings)

        assertTrue(migrator.migrateIfNeeded())
        assertTrue(settings.snoozeSettingsMigrated.first())
        assertEquals(SnoozeSettings(enabled = true, intervalMinutes = 5), settings.snoozeSettings.first())

        // 引き継いだ後に利用者が変更した設定は、次の起動で上書きしない
        settings.setSnoozeEnabled(false)
        assertFalse(migrator.migrateIfNeeded())
        assertFalse(settings.snoozeSettings.first().enabled)
    }

    @Test
    fun migratorWithoutEnabledTargetsSetsDefaultsAndFlag() = runTest {
        val settings = FakeSettingsRepository()
        assertTrue(SnoozeSettingsMigrator(FakeTargetRepository(listOf(target(1, enabled = false))), settings).migrateIfNeeded())
        assertEquals(SnoozeSettings(), settings.snoozeSettings.first())
        assertTrue(settings.snoozeSettingsMigrated.first())
    }
}
