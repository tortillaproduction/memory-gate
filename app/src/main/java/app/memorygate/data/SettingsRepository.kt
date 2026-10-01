package app.memorygate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
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

    suspend fun setGatePassedDate(date: LocalDate?)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setManufacturerStepDone(done: Boolean)
    suspend fun setUpdateLastCheckedAt(epochMillis: Long)
    suspend fun setLatestRelease(version: String, htmlUrl: String)
    suspend fun setDismissedUpdateVersion(version: String)
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

    private companion object {
        val KEY_GATE_PASSED_DATE = stringPreferencesKey("gatePassedDate")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboardingCompleted")
        val KEY_MANUFACTURER_STEP_DONE = booleanPreferencesKey("manufacturerStepDone")
        val KEY_UPDATE_LAST_CHECKED_AT = longPreferencesKey("updateLastCheckedAt")
        val KEY_UPDATE_LATEST_VERSION = stringPreferencesKey("updateLatestVersion")
        val KEY_UPDATE_LATEST_URL = stringPreferencesKey("updateLatestUrl")
        val KEY_UPDATE_DISMISSED_VERSION = stringPreferencesKey("updateDismissedVersion")
    }
}
