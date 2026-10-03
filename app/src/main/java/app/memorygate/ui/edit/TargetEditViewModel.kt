package app.memorygate.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.memorygate.AppContainer
import app.memorygate.apps.InstalledApp
import app.memorygate.domain.GateLogic
import app.memorygate.domain.ScheduleType
import app.memorygate.domain.Target
import app.memorygate.domain.TargetFormat
import app.memorygate.domain.TargetType
import app.memorygate.domain.TargetValidation
import app.memorygate.image.SnoozeImageSession
import app.memorygate.ui.apps.AppRowState
import app.memorygate.ui.apps.filterByQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI 上のスケジュール選択肢（SPEC 3.1） */
enum class ScheduleOption(val label: String) {
    EVERY_1_DAY("1日ごと"),
    EVERY_3_DAYS("3日ごと"),
    EVERY_7_DAYS("1週間ごと"),
    WEEKLY("毎週〇曜日"),
}

data class TargetEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val title: String = "",
    val type: TargetType = TargetType.URL,
    val url: String = "",
    val packageName: String? = null,
    val appLabel: String? = null,
    val schedule: ScheduleOption = ScheduleOption.EVERY_1_DAY,
    val dayOfWeek: Int = 1,
    val lastVisitedLabel: String = "未訪問",
    val hasVisited: Boolean = false,
    val titleError: String? = null,
    val urlError: String? = null,
    val appError: String? = null,
    /** スヌーズ用ゲートの画像（スヌーズの ON/OFF・間隔・時間帯はアプリ全体の設定） */
    val snoozeImagePath: String? = null,
    val importingImage: Boolean = false,
    val imageError: String? = null,
    /** 保存・削除が終わって画面を閉じるべき状態 */
    val finished: Boolean = false,
)

data class AppPickerUiState(
    val loading: Boolean = true,
    val query: String = "",
    val rows: List<AppRowState> = emptyList(),
)

class TargetEditViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val targetId: Long = savedStateHandle.get<Long>(ARG_TARGET_ID) ?: 0L
    private var original: Target? = null
    private var imageSession = SnoozeImageSession(original = null)
    private var saved = false

    private val _uiState = MutableStateFlow(TargetEditUiState())
    val uiState: StateFlow<TargetEditUiState> = _uiState.asStateFlow()

    private val pickerApps = MutableStateFlow<List<InstalledApp>?>(null)
    private val pickerQuery = MutableStateFlow("")

    val appPickerState: StateFlow<AppPickerUiState> = combine(
        pickerApps,
        pickerQuery,
        container.guardedAppRepository.observeGuardedPackages(),
    ) { apps, query, guarded ->
        AppPickerUiState(
            loading = apps == null,
            query = query,
            rows = apps.orEmpty().filterByQuery(query).map { app ->
                AppRowState(
                    app = app,
                    selected = app.packageName == _uiState.value.packageName,
                    disabledReason = if (app.packageName in guarded) "監視対象アプリに設定済みのため選択できません" else null,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPickerUiState())

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val clock = container.clock
        val target = if (targetId != 0L) container.targetRepository.getTarget(targetId) else null
        original = target
        imageSession = SnoozeImageSession(original = target?.snoozeImagePath)
        if (target == null) {
            _uiState.value = TargetEditUiState(
                loading = false,
                isNew = true,
                dayOfWeek = GateLogic.today(clock).dayOfWeek.value,
            )
            return
        }
        val today = GateLogic.today(clock)
        _uiState.value = TargetEditUiState(
            loading = false,
            isNew = false,
            title = target.title,
            type = target.type,
            url = target.url.orEmpty(),
            packageName = target.packageName,
            appLabel = target.packageName?.let { container.installedAppsRepository.loadLabel(it) ?: it },
            schedule = when (target.scheduleType) {
                ScheduleType.WEEKLY -> ScheduleOption.WEEKLY
                ScheduleType.EVERY_N_DAYS -> when (target.intervalDays) {
                    3 -> ScheduleOption.EVERY_3_DAYS
                    7 -> ScheduleOption.EVERY_7_DAYS
                    else -> ScheduleOption.EVERY_1_DAY
                }
            },
            dayOfWeek = target.dayOfWeek ?: today.dayOfWeek.value,
            lastVisitedLabel = lastVisitedLabel(target.lastVisitedAt),
            hasVisited = target.lastVisitedAt != null,
            snoozeImagePath = target.snoozeImagePath,
        )
    }

    private fun lastVisitedLabel(lastVisitedAt: Long?): String {
        if (lastVisitedAt == null) return "未訪問"
        val date = GateLogic.toLocalDate(lastVisitedAt, container.clock.zone)
        val relative = TargetFormat.lastVisited(lastVisitedAt, GateLogic.today(container.clock), container.clock.zone)
        return "${date.year}/${TargetFormat.shortDate(date)}（${relative.removePrefix("最終: ")}）"
    }

    fun setTitle(value: String) = _uiState.update { it.copy(title = value, titleError = null) }
    fun setType(value: TargetType) = _uiState.update { it.copy(type = value, urlError = null, appError = null) }
    fun setUrl(value: String) = _uiState.update { it.copy(url = value, urlError = null) }
    fun setSchedule(value: ScheduleOption) = _uiState.update { it.copy(schedule = value) }
    fun setDayOfWeek(value: Int) = _uiState.update { it.copy(dayOfWeek = value) }

    /** Photo Picker で選んだ画像を縮小してアプリ内部にコピーする（保存するまで元の画像は残す） */
    fun importSnoozeImage(uri: Uri) {
        _uiState.update { it.copy(importingImage = true, imageError = null) }
        viewModelScope.launch {
            val path = container.snoozeImageStore.import(uri)
            if (path != null) imageSession.onImported(path)
            _uiState.update {
                if (path != null) {
                    it.copy(importingImage = false, snoozeImagePath = path)
                } else {
                    it.copy(importingImage = false, imageError = "画像を読み込めませんでした")
                }
            }
        }
    }

    /** 「画像を削除」（ファイルは保存したときに削除する） */
    fun removeSnoozeImage() = _uiState.update { it.copy(snoozeImagePath = null, imageError = null) }

    fun selectApp(app: InstalledApp) =
        _uiState.update { it.copy(packageName = app.packageName, appLabel = app.label, appError = null) }

    fun openAppPicker() {
        pickerQuery.value = ""
        if (pickerApps.value != null) return
        viewModelScope.launch {
            val repo = container.installedAppsRepository
            pickerApps.value = repo.loadLauncherApps(repo.targetExcludedPackages())
        }
    }

    fun setPickerQuery(value: String) {
        pickerQuery.value = value
    }

    /** 「未訪問に戻す」。既存の誘導先はすぐに保存する */
    fun resetVisited() {
        val target = original ?: return
        viewModelScope.launch {
            container.targetRepository.setLastVisitedAt(target.id, null)
            original = target.copy(lastVisitedAt = null)
            _uiState.update { it.copy(lastVisitedLabel = lastVisitedLabel(null), hasVisited = false) }
        }
    }

    fun save() {
        val state = _uiState.value
        val title = state.title.trim()
        val url = state.url.trim()
        var titleError: String? = null
        var urlError: String? = null
        var appError: String? = null
        if (title.isEmpty()) titleError = "タイトルを入力してください"
        when (state.type) {
            TargetType.URL -> if (!TargetValidation.isValidUrl(url)) {
                urlError = "http:// または https:// で始まる URL を入力してください"
            }
            TargetType.APP -> if (state.packageName == null) appError = "アプリを選択してください"
        }
        viewModelScope.launch {
            if (state.type == TargetType.APP && state.packageName != null) {
                val guarded = container.guardedAppRepository.observeGuardedPackages().first()
                if (state.packageName in guarded) appError = "監視対象アプリに設定済みのアプリは選択できません"
            }
            if (titleError != null || urlError != null || appError != null) {
                _uiState.update { it.copy(titleError = titleError, urlError = urlError, appError = appError) }
                return@launch
            }
            val base = original
            val target = Target(
                id = base?.id ?: 0L,
                title = title,
                type = state.type,
                url = if (state.type == TargetType.URL) url else null,
                packageName = if (state.type == TargetType.APP) state.packageName else null,
                scheduleType = if (state.schedule == ScheduleOption.WEEKLY) ScheduleType.WEEKLY else ScheduleType.EVERY_N_DAYS,
                intervalDays = when (state.schedule) {
                    ScheduleOption.EVERY_1_DAY -> 1
                    ScheduleOption.EVERY_3_DAYS -> 3
                    ScheduleOption.EVERY_7_DAYS -> 7
                    ScheduleOption.WEEKLY -> null
                },
                dayOfWeek = if (state.schedule == ScheduleOption.WEEKLY) state.dayOfWeek else null,
                lastVisitedAt = base?.lastVisitedAt,
                createdAt = base?.createdAt ?: container.clock.millis(),
                // v0.1.6 までの誘導先ごとのスヌーズ設定（引き継ぎ専用。v0.1.7 からは使わない）は、読み込んだ値をそのまま残す
                snoozeEnabled = base?.snoozeEnabled ?: false,
                snoozeIntervalMinutes = base?.snoozeIntervalMinutes,
                snoozeStartMinutes = base?.snoozeStartMinutes,
                snoozeEndMinutes = base?.snoozeEndMinutes,
                snoozeImagePath = state.snoozeImagePath,
                lastSnoozeShownAt = base?.lastSnoozeShownAt,
            )
            container.targetRepository.save(target)
            saved = true
            // 使わなくなった画像（変更前の画像・選び直した画像）を削除する
            imageSession.filesToDeleteOnSave(state.snoozeImagePath).forEach { container.snoozeImageStore.delete(it) }
            _uiState.update { it.copy(finished = true) }
        }
    }

    fun delete() {
        val target = original ?: return
        viewModelScope.launch {
            container.targetRepository.delete(target.id)
            saved = true
            imageSession.filesToDeleteOnTargetDeleted().forEach { container.snoozeImageStore.delete(it) }
            _uiState.update { it.copy(finished = true) }
        }
    }

    override fun onCleared() {
        // 保存せずに閉じた場合は、この編集中に取り込んだ画像を削除する
        if (!saved) {
            val files = imageSession.filesToDeleteOnDiscard()
            if (files.isNotEmpty()) {
                container.applicationScope.launch { files.forEach { container.snoozeImageStore.delete(it) } }
            }
        }
    }

    companion object {
        const val ARG_TARGET_ID = "targetId"
    }
}
