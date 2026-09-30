package app.memorygate.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.memorygate.AppContainer
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
    val selectedCount: Int = 0,
    val rows: List<AppRowState> = emptyList(),
)

class GuardedAppsViewModel(private val container: AppContainer) : ViewModel() {

    private val apps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")
    private val onlySelected = MutableStateFlow(false)

    val uiState: StateFlow<GuardedAppsUiState> = combine(
        apps,
        container.guardedAppRepository.observeGuardedPackages(),
        container.targetRepository.observeTargets(),
        query,
        onlySelected,
    ) { apps, guarded, targets, query, onlySelected ->
        val targetPackages = targets.filter { it.type == TargetType.APP }.mapNotNull { it.packageName }.toSet()
        val rows = apps.orEmpty()
            .filterByQuery(query)
            .map { app ->
                AppRowState(
                    app = app,
                    selected = app.packageName in guarded,
                    disabledReason = if (app.packageName in targetPackages) "誘導先として登録済みのため選択できません" else null,
                )
            }
            .filter { !onlySelected || it.selected }
        GuardedAppsUiState(
            loading = apps == null,
            query = query,
            onlySelected = onlySelected,
            selectedCount = apps.orEmpty().count { it.packageName in guarded },
            rows = rows,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuardedAppsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val repo = container.installedAppsRepository
            apps.value = repo.loadLauncherApps(repo.guardExcludedPackages())
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setOnlySelected(value: Boolean) {
        onlySelected.value = value
    }

    fun setGuarded(packageName: String, guarded: Boolean) {
        viewModelScope.launch { container.guardedAppRepository.setGuarded(packageName, guarded) }
    }
}
