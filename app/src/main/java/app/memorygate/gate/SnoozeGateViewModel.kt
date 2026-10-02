package app.memorygate.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.memorygate.AppContainer
import app.memorygate.domain.SnoozeBackground
import app.memorygate.domain.Target
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SnoozeGateUiState(
    val loading: Boolean = true,
    /** 誘導先が削除された場合などは null */
    val target: Target? = null,
    /** false なら「アプリが見つかりません」としてボタンを無効化する */
    val launchable: Boolean = true,
    val daytime: Boolean = true,
)

class SnoozeGateViewModel(private val container: AppContainer) : ViewModel() {
    private val targetId = MutableStateFlow<Long?>(null)
    private val refreshCount = MutableStateFlow(0)

    val uiState: StateFlow<SnoozeGateUiState> = combine(
        container.targetRepository.observeTargets(),
        targetId,
        refreshCount,
    ) { targets, id, _ ->
        val target = targets.find { it.id == id }
        SnoozeGateUiState(
            loading = id == null,
            target = target,
            launchable = target?.let { container.targetLauncher.canLaunch(it) } ?: false,
            daytime = SnoozeBackground.isDaytime(container.clock.instant(), container.clock.zone),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SnoozeGateUiState())

    /** 表示する誘導先を設定する（onCreate / onNewIntent） */
    fun show(id: Long) {
        targetId.value = id
        refreshCount.value++
    }
}
