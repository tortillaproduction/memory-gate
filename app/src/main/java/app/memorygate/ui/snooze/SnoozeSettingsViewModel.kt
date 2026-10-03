package app.memorygate.ui.snooze

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.memorygate.AppContainer
import app.memorygate.domain.SnoozeSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SnoozeSettingsUiState(
    val loading: Boolean = true,
    val settings: SnoozeSettings = SnoozeSettings(),
)

/** スヌーズ設定画面（SPEC 7.6）。変更はすぐに保存する */
class SnoozeSettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val settings = container.settingsRepository

    val uiState: StateFlow<SnoozeSettingsUiState> = settings.snoozeSettings
        .map { SnoozeSettingsUiState(loading = false, settings = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SnoozeSettingsUiState())

    fun setEnabled(enabled: Boolean) = save { settings.setSnoozeEnabled(enabled) }
    fun setInterval(minutes: Int) = save { settings.setSnoozeIntervalMinutes(minutes) }
    fun setStart(minutes: Int) = save { settings.setSnoozeStartMinutes(minutes) }
    fun setEnd(minutes: Int) = save { settings.setSnoozeEndMinutes(minutes) }

    /** 画面を閉じても保存が取り消されないよう、アプリのスコープで保存する */
    private fun save(block: suspend () -> Unit) {
        container.applicationScope.launch { block() }
    }
}
