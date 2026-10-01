package app.memorygate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.memorygate.AppContainer
import app.memorygate.BuildConfig
import app.memorygate.domain.GateLogic
import app.memorygate.domain.Target
import app.memorygate.domain.TargetFormat
import app.memorygate.update.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeTargetItem(
    val target: Target,
    val schedule: String,
    val lastVisited: String,
    val isDue: Boolean,
    /** 次の期限日。期限切れ・未訪問なら null */
    val nextDue: String?,
)

/** 新しいバージョンのお知らせ */
data class UpdateBannerState(val version: String, val htmlUrl: String)

data class HomeUiState(
    val loading: Boolean = true,
    val targets: List<HomeTargetItem> = emptyList(),
    val gatePassedToday: Boolean = false,
    val guardedCount: Int = 0,
    /** 表示する新しいバージョンのお知らせ。なければ null */
    val update: UpdateBannerState? = null,
)

class HomeViewModel(
    private val container: AppContainer,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
) : ViewModel() {
    private val clock = container.clock
    private val today = MutableStateFlow(GateLogic.today(clock))
    private val settings = container.settingsRepository

    private val update = combine(settings.latestRelease, settings.dismissedUpdateVersion) { release, dismissed ->
        release
            ?.takeIf { UpdateChecker.shouldShowBanner(it.version, currentVersion, dismissed) }
            ?.let { UpdateBannerState(it.version, it.htmlUrl) }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        container.targetRepository.observeTargets(),
        settings.gatePassedDate,
        container.guardedAppRepository.observeGuardedPackages(),
        today,
        update,
    ) { targets, gatePassedDate, guarded, today, update ->
        HomeUiState(
            loading = false,
            targets = targets.map { it.toItem(today) },
            gatePassedToday = gatePassedDate == today,
            guardedCount = guarded.size,
            update = update,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * ホーム画面を表示したときに、新しいバージョンを確認する（前回から 12 時間以上たっている場合だけ問い合わせる）。
     * 画面を離れても取り消されないよう、アプリのスコープで実行する。
     */
    fun checkForUpdate() {
        container.applicationScope.launch { container.updateChecker.checkIfDue() }
    }

    /** 「×」: そのバージョンについてはバナーを閉じる */
    fun dismissUpdate(version: String) {
        viewModelScope.launch { settings.setDismissedUpdateVersion(version) }
    }

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
