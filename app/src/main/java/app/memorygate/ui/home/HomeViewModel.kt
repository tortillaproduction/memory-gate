package app.memorygate.ui.home

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
import java.time.LocalDate

data class HomeTargetItem(
    val target: Target,
    val schedule: String,
    val lastVisited: String,
    val isDue: Boolean,
    /** 次の期限日。期限切れ・未訪問なら null */
    val nextDue: String?,
)

data class HomeUiState(
    val loading: Boolean = true,
    val targets: List<HomeTargetItem> = emptyList(),
    val gatePassedToday: Boolean = false,
    val guardedCount: Int = 0,
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    private val clock = container.clock
    private val today = MutableStateFlow(GateLogic.today(clock))

    val uiState: StateFlow<HomeUiState> = combine(
        container.targetRepository.observeTargets(),
        container.settingsRepository.gatePassedDate,
        container.guardedAppRepository.observeGuardedPackages(),
        today,
    ) { targets, gatePassedDate, guarded, today ->
        HomeUiState(
            loading = false,
            targets = targets.map { it.toItem(today) },
            gatePassedToday = gatePassedDate == today,
            guardedCount = guarded.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 画面復帰時に日付を更新する（日付をまたいだ場合に表示を更新するため） */
    fun refreshToday() {
        today.value = GateLogic.today(clock)
    }

    private fun Target.toItem(today: LocalDate): HomeTargetItem {
        val zone = clock.zone
        return HomeTargetItem(
            target = this,
            schedule = TargetFormat.schedule(this),
            lastVisited = TargetFormat.lastVisited(lastVisitedAt, today, zone),
            isDue = GateLogic.isDue(this, today, zone),
            nextDue = GateLogic.nextDueDate(this, today, zone)?.let(TargetFormat::shortDate),
        )
    }
}
