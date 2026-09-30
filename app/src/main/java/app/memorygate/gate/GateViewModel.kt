package app.memorygate.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.memorygate.AppContainer
import app.memorygate.domain.GateLogic
import app.memorygate.domain.Target
import app.memorygate.domain.TargetFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class GateItem(
    val target: Target,
    val lastVisited: String,
    /** false なら「アプリが見つかりません」としてボタンを無効化する */
    val launchable: Boolean,
)

data class GateUiState(
    val loading: Boolean = true,
    val items: List<GateItem> = emptyList(),
)

class GateViewModel(private val container: AppContainer) : ViewModel() {
    private val clock = container.clock
    private val today = MutableStateFlow(GateLogic.today(clock))
    /** 表示のたびに起動可否を再判定するためのカウンタ */
    private val refreshCount = MutableStateFlow(0)

    val uiState: StateFlow<GateUiState> = combine(
        container.targetRepository.observeTargets(),
        today,
        refreshCount,
    ) { targets, today, _ ->
        val launcher = container.targetLauncher
        GateUiState(
            loading = false,
            items = GateLogic.selectGateTargets(targets, today, clock.zone).map {
                GateItem(
                    target = it,
                    lastVisited = TargetFormat.lastVisited(it.lastVisitedAt, today, clock.zone),
                    launchable = launcher.canLaunch(it),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GateUiState())

    /** ゲートを（再）表示したとき */
    fun refresh() {
        today.value = GateLogic.today(clock)
        refreshCount.value++
    }
}
