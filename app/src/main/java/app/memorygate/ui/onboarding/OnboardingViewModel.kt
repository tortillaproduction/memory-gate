package app.memorygate.ui.onboarding

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.memorygate.AppContainer
import app.memorygate.service.SystemStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val steps: List<OnboardingStep> = emptyList(),
    val index: Int = 0,
    val done: Set<OnboardingStep> = emptySet(),
    val manufacturer: Manufacturer? = null,
    val targetTitles: List<String> = emptyList(),
    /** 読み込み前は null */
    val onboardingCompleted: Boolean? = null,
) {
    val current: OnboardingStep get() = steps[index]
    val isLast: Boolean get() = index == steps.lastIndex
}

private data class Progress(
    val index: Int,
    val visited: Set<OnboardingStep>,
    val status: SystemStatus,
    val completed: Boolean,
)

class OnboardingViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val steps = OnboardingSteps.visibleSteps(Build.VERSION.SDK_INT, Build.MANUFACTURER)
    private val manufacturer = OnboardingSteps.manufacturerOf(Build.MANUFACTURER)

    private val index = MutableStateFlow(
        savedStateHandle.get<String>(ARG_STEP)
            ?.let { name -> steps.indexOfFirst { it.name == name } }
            ?.takeIf { it >= 0 } ?: 0,
    )
    private val systemStatus = MutableStateFlow(SystemStatus(accessibilityEnabled = false, ignoringBatteryOptimizations = false))
    private val visited = MutableStateFlow(setOf(steps[index.value]))

    val uiState: StateFlow<OnboardingUiState> = combine(
        combine(index, visited, systemStatus, container.settingsRepository.onboardingCompleted, ::Progress),
        container.targetRepository.observeTargets(),
        container.guardedAppRepository.observeGuardedPackages(),
        container.settingsRepository.manufacturerStepDone,
    ) { (index, visited, status, completed), targets, guarded, manufacturerDone ->
        val done = steps.filter { step ->
            when (step) {
                OnboardingStep.INTRO -> visited.any { it != OnboardingStep.INTRO }
                // 制限付き設定の許可状態は直接取得できないため、ユーザー補助が有効になっていれば許可済みとみなす
                OnboardingStep.RESTRICTED_SETTINGS -> status.accessibilityEnabled
                OnboardingStep.ACCESSIBILITY -> status.accessibilityEnabled
                OnboardingStep.BATTERY -> status.ignoringBatteryOptimizations
                OnboardingStep.MANUFACTURER -> manufacturerDone
                OnboardingStep.TARGETS -> targets.isNotEmpty()
                OnboardingStep.GUARDED_APPS -> guarded.isNotEmpty()
            }
        }.toSet()
        OnboardingUiState(
            steps = steps,
            index = index,
            done = done,
            manufacturer = manufacturer,
            targetTitles = targets.map { it.title },
            onboardingCompleted = completed,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        OnboardingUiState(steps = steps, index = index.value, manufacturer = manufacturer),
    )

    /** onResume で端末の状態を再確認する */
    fun updateSystemStatus(status: SystemStatus) {
        systemStatus.value = status
    }

    fun next() = goTo(index.value + 1)

    /**
     * 「戻る」ボタン・システムの戻る操作。2 ステップ目以降は前のステップへ戻り、
     * 最初のステップでは画面を抜ける動作を返す（読み込み前は何もしない）。
     */
    fun onBack(): OnboardingBackAction? {
        val completed = uiState.value.onboardingCompleted ?: return null
        val action = OnboardingBackAction.decide(index.value, completed)
        if (action == OnboardingBackAction.PREVIOUS_STEP) goTo(index.value - 1)
        return action
    }

    fun goTo(newIndex: Int) {
        if (newIndex !in steps.indices) return
        index.value = newIndex
        visited.value += steps[newIndex]
    }

    fun setManufacturerStepDone(done: Boolean) {
        viewModelScope.launch { container.settingsRepository.setManufacturerStepDone(done) }
    }

    fun complete(onCompleted: () -> Unit) {
        viewModelScope.launch {
            container.settingsRepository.setOnboardingCompleted(true)
            onCompleted()
        }
    }

    companion object {
        const val ARG_STEP = "step"
    }
}
