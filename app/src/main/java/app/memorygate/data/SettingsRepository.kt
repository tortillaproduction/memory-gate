package app.memorygate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.memorygate.domain.SnoozeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeParseException

interface SettingsRepository {
    /** ゲートのボタンを押した日。この日はゲートを出さない */
    val gatePassedDate: Flow<LocalDate?>
    val onboardingCompleted: Flow<Boolean>

    /** オンボーディングの「メーカー別の追加設定」を自己申告で完了にしたか */
    val manufacturerStepDone: Flow<Boolean>

    /** 新しいバージョンを前回確認した日時 (epoch millis)。未確認なら null */
    val updateLastCheckedAt: Flow<Long?>

    /** 確認で見つかった最新バージョン（例: `0.1.3`）とリリースページの URL */
    val latestRelease: Flow<StoredRelease?>

    /** バナーを「×」で閉じたバージョン */
    val dismissedUpdateVersion: Flow<String?>

    /** スヌーズの設定（アプリ全体で 1 つ） */
    val snoozeSettings: Flow<SnoozeSettings>

    /** スヌーズ用ゲートを最後に表示した日時 (epoch millis)。未表示なら null */
    val lastSnoozeShownAt: Flow<Long?>

    /** ゲートの「開く」で誘導先を開いた日時（スヌーズの休止の開始。v0.1.8）。なければ null */
    val snoozePausedAt: Flow<Long?>

    /** v0.1.6 までの誘導先ごとのスヌーズ設定の引き継ぎが完了したか */
    val snoozeSettingsMigrated: Flow<Boolean>

    suspend fun setGatePassedDate(date: LocalDate?)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setManufacturerStepDone(done: Boolean)
    suspend fun setUpdateLastCheckedAt(epochMillis: Long)
    suspend fun setLatestRelease(version: String, htmlUrl: String)
    suspend fun setDismissedUpdateVersion(version: String)
    suspend fun setSnoozeEnabled(enabled: Boolean)
    suspend fun setSnoozeIntervalMinutes(minutes: Int)
    suspend fun setSnoozeStartMinutes(minutes: Int)
    suspend fun setSnoozeEndMinutes(minutes: Int)
    suspend fun setLastSnoozeShownAt(epochMillis: Long)
    suspend fun setSnoozePausedAt(epochMillis: Long)

    /**
     * 引き継ぎ: まだ完了していなければ [settings] を保存し、完了フラグを立てる（1 回の書き込みで行う）。
     * すでに完了していれば何もしない。保存したら true
     */
    suspend fun migrateSnoozeSettings(settings: SnoozeSettings): Boolean
}

data class StoredRelease(val version: String, val htmlUrl: String)

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val gatePassedDate: Flow<LocalDate?> = dataStore.data.map { prefs ->
        prefs[KEY_GATE_PASSED_DATE]?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    override val onboardingCompleted: Flow<Boolean> =
        dataStore.data.map { it[KEY_ONBOARDING_COMPLETED] ?: false }

    override val manufacturerStepDone: Flow<Boolean> =
        dataStore.data.map { it[KEY_MANUFACTURER_STEP_DONE] ?: false }

    override val updateLastCheckedAt: Flow<Long?> = dataStore.data.map { it[KEY_UPDATE_LAST_CHECKED_AT] }

    override val latestRelease: Flow<StoredRelease?> = dataStore.data.map { prefs ->
        val version = prefs[KEY_UPDATE_LATEST_VERSION]
        val url = prefs[KEY_UPDATE_LATEST_URL]
        if (version != null && url != null) StoredRelease(version, url) else null
    }

    override val dismissedUpdateVersion: Flow<String?> = dataStore.data.map { it[KEY_UPDATE_DISMISSED_VERSION] }

    override val snoozeSettings: Flow<SnoozeSettings> = dataStore.data.map { prefs ->
        SnoozeSettings.sanitized(
            enabled = prefs[KEY_SNOOZE_ENABLED] ?: false,
            intervalMinutes = prefs[KEY_SNOOZE_INTERVAL_MINUTES],
            startMinutes = prefs[KEY_SNOOZE_START_MINUTES],
            endMinutes = prefs[KEY_SNOOZE_END_MINUTES],
        )
    }.distinctUntilChanged()

    override val lastSnoozeShownAt: Flow<Long?> = dataStore.data.map { it[KEY_LAST_SNOOZE_SHOWN_AT] }.distinctUntilChanged()

    override val snoozePausedAt: Flow<Long?> = dataStore.data.map { it[KEY_SNOOZE_PAUSED_AT] }.distinctUntilChanged()

    override val snoozeSettingsMigrated: Flow<Boolean> = dataStore.data.map { it[KEY_SNOOZE_SETTINGS_MIGRATED] ?: false }

    override suspend fun setGatePassedDate(date: LocalDate?) {
        dataStore.edit { prefs ->
            if (date == null) prefs.remove(KEY_GATE_PASSED_DATE) else prefs[KEY_GATE_PASSED_DATE] = date.toString()
        }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setManufacturerStepDone(done: Boolean) {
        dataStore.edit { it[KEY_MANUFACTURER_STEP_DONE] = done }
    }

    override suspend fun setUpdateLastCheckedAt(epochMillis: Long) {
        dataStore.edit { it[KEY_UPDATE_LAST_CHECKED_AT] = epochMillis }
    }

    override suspend fun setLatestRelease(version: String, htmlUrl: String) {
        dataStore.edit {
            it[KEY_UPDATE_LATEST_VERSION] = version
            it[KEY_UPDATE_LATEST_URL] = htmlUrl
        }
    }

    override suspend fun setDismissedUpdateVersion(version: String) {
        dataStore.edit { it[KEY_UPDATE_DISMISSED_VERSION] = version }
    }

    override suspend fun setSnoozeEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SNOOZE_ENABLED] = enabled }
    }

    override suspend fun setSnoozeIntervalMinutes(minutes: Int) {
        dataStore.edit { it[KEY_SNOOZE_INTERVAL_MINUTES] = minutes }
    }

    override suspend fun setSnoozeStartMinutes(minutes: Int) {
        dataStore.edit { it[KEY_SNOOZE_START_MINUTES] = minutes }
    }

    override suspend fun setSnoozeEndMinutes(minutes: Int) {
        dataStore.edit { it[KEY_SNOOZE_END_MINUTES] = minutes }
    }

    override suspend fun setLastSnoozeShownAt(epochMillis: Long) {
        dataStore.edit { it[KEY_LAST_SNOOZE_SHOWN_AT] = epochMillis }
    }

    override suspend fun setSnoozePausedAt(epochMillis: Long) {
        dataStore.edit { it[KEY_SNOOZE_PAUSED_AT] = epochMillis }
    }

    override suspend fun migrateSnoozeSettings(settings: SnoozeSettings): Boolean {
        var applied = false
        dataStore.edit { prefs ->
            if (prefs[KEY_SNOOZE_SETTINGS_MIGRATED] == true) return@edit
            prefs[KEY_SNOOZE_ENABLED] = settings.enabled
            prefs[KEY_SNOOZE_INTERVAL_MINUTES] = settings.intervalMinutes
            prefs[KEY_SNOOZE_START_MINUTES] = settings.startMinutes
            prefs[KEY_SNOOZE_END_MINUTES] = settings.endMinutes
            prefs[KEY_SNOOZE_SETTINGS_MIGRATED] = true
            applied = true
        }
        return applied
    }

    private companion object {
        val KEY_GATE_PASSED_DATE = stringPreferencesKey("gatePassedDate")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboardingCompleted")
        val KEY_MANUFACTURER_STEP_DONE = booleanPreferencesKey("manufacturerStepDone")
        val KEY_UPDATE_LAST_CHECKED_AT = longPreferencesKey("updateLastCheckedAt")
        val KEY_UPDATE_LATEST_VERSION = stringPreferencesKey("updateLatestVersion")
        val KEY_UPDATE_LATEST_URL = stringPreferencesKey("updateLatestUrl")
        val KEY_UPDATE_DISMISSED_VERSION = stringPreferencesKey("updateDismissedVersion")
        val KEY_SNOOZE_ENABLED = booleanPreferencesKey("snoozeEnabled")
        val KEY_SNOOZE_INTERVAL_MINUTES = intPreferencesKey("snoozeIntervalMinutes")
        val KEY_SNOOZE_START_MINUTES = intPreferencesKey("snoozeStartMinutes")
        val KEY_SNOOZE_END_MINUTES = intPreferencesKey("snoozeEndMinutes")
        val KEY_LAST_SNOOZE_SHOWN_AT = longPreferencesKey("lastSnoozeShownAt")
        val KEY_SNOOZE_PAUSED_AT = longPreferencesKey("snoozePausedAt")
        val KEY_SNOOZE_SETTINGS_MIGRATED = booleanPreferencesKey("snoozeSettingsMigrated")
    }
}
