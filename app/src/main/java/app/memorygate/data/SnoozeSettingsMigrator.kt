package app.memorygate.data

import app.memorygate.domain.SnoozeSettingsMigration
import kotlinx.coroutines.flow.first

/**
 * アップデート後の初回起動時に 1 回だけ、v0.1.6 までの誘導先ごとのスヌーズ設定をアプリ全体の設定へ引き継ぐ（SPEC 3.3）。
 * 完了したことは DataStore の `snoozeSettingsMigrated` に保存する。
 */
class SnoozeSettingsMigrator(
    private val targetRepository: TargetRepository,
    private val settingsRepository: SettingsRepository,
) {
    /** 引き継ぎを行ったら true（すでに完了していれば false） */
    suspend fun migrateIfNeeded(): Boolean {
        if (settingsRepository.snoozeSettingsMigrated.first()) return false
        val targets = targetRepository.observeTargets().first()
        return settingsRepository.migrateSnoozeSettings(SnoozeSettingsMigration.fromTargets(targets))
    }
}
