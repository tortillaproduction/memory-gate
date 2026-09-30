package app.memorygate.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    suspend fun setGatePassedDate(date: LocalDate?)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setManufacturerStepDone(done: Boolean)
}

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

    private companion object {
        val KEY_GATE_PASSED_DATE = stringPreferencesKey("gatePassedDate")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboardingCompleted")
        val KEY_MANUFACTURER_STEP_DONE = booleanPreferencesKey("manufacturerStepDone")
    }
}
