package app.memorygate.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.memorygate.AppContainer
import app.memorygate.apps.GuardExclusion
import app.memorygate.apps.InstalledApp
import app.memorygate.domain.TargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GuardedAppsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val onlySelected: Boolean = false,
    val showSystemApps: Boolean = false,
    val selectedCount: Int = 0,
    val rows: List<AppRowState> = emptyList(),
)

class GuardedAppsViewModel(private val container: AppContainer) : ViewModel() {

    /** ランチャーに表示されるアプリ（自アプリ以外）と、その除外判定 */
    private data class Candidates(val apps: List<InstalledApp>, val exclusion: GuardExclusion)

    private data class Filters(val query: String, val onlySelected: Boolean, val showSystemApps: Boolean)

    private val candidates = MutableStateFlow<Candidates?>(null)
    private val query = MutableStateFlow("")
    private val onlySelected = MutableStateFlow(false)

    /** 「システムアプリも表示」。既定はオフ（画面を開くたびにオフに戻る） */
    private val showSystemApps = MutableStateFlow(false)

    private val filters = combine(query, onlySelected, showSystemApps, ::Filters)

    val uiState: StateFlow<GuardedAppsUiState> = combine(
        candidates,
        container.guardedAppRepository.observeGuardedPackages(),
        container.targetRepository.observeTargets(),
        filters,
    ) { candidates, guarded, targets, filters ->
        val targetPackages = targets.filter { it.type == TargetType.APP }.mapNotNull { it.packageName }.toSet()
        val visibleApps = candidates?.let { c ->
            c.apps.filter { c.exclusion.isVisible(it.packageName, filters.showSystemApps, guarded) }
        }.orEmpty()
        val rows = visibleApps
            .filterByQuery(filters.query)
            .map { app ->
                AppRowState(
                    app = app,
                    selected = app.packageName in guarded,
                    disabledReason = if (app.packageName in targetPackages) "誘導先として登録済みのため選択できません" else null,
                )
            }
            .filter { !filters.onlySelected || it.selected }
        GuardedAppsUiState(
            loading = candidates == null,
            query = filters.query,
            onlySelected = filters.onlySelected,
            showSystemApps = filters.showSystemApps,
            selectedCount = visibleApps.count { it.packageName in guarded },
            rows = rows,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuardedAppsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val repo = container.installedAppsRepository
            val apps = repo.loadLauncherApps(repo.targetExcludedPackages())
            candidates.value = Candidates(apps, repo.guardExclusion(apps.map { it.packageName }))
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setOnlySelected(value: Boolean) {
        onlySelected.value = value
    }

    fun setShowSystemApps(value: Boolean) {
        showSystemApps.value = value
    }

    fun setGuarded(packageName: String, guarded: Boolean) {
        viewModelScope.launch { container.guardedAppRepository.setGuarded(packageName, guarded) }
    }
}
