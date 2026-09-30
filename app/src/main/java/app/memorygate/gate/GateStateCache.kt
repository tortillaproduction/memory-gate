package app.memorygate.gate

import app.memorygate.data.GuardedAppRepository
import app.memorygate.data.SettingsRepository
import app.memorygate.data.TargetRepository
import app.memorygate.domain.GateLogic
import app.memorygate.domain.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

/** ゲート判定に必要な状態のスナップショット */
data class GateSnapshot(
    val guardedPackages: Set<String>,
    val targets: List<Target>,
    val gatePassedDate: LocalDate?,
)

/**
 * 監視対象アプリ・誘導先・gatePassedDate を Flow で購読してメモリ上に保持する（SPEC 5 章）。
 * AccessibilityService はイベントごとに DB を読まず、ここにキャッシュされた値で判定する。
 */
class GateStateCache(
    scope: CoroutineScope,
    targetRepository: TargetRepository,
    guardedAppRepository: GuardedAppRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) {
    /** 読み込み完了前は null */
    val snapshot: StateFlow<GateSnapshot?> = combine(
        guardedAppRepository.observeGuardedPackages(),
        targetRepository.observeTargets(),
        settingsRepository.gatePassedDate,
    ) { guarded, targets, passed -> GateSnapshot(guarded, targets, passed) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** 4.3 ゲートを表示するか。読み込み完了前は表示しない */
    fun shouldShowGate(packageName: String): Boolean {
        val s = snapshot.value ?: return false
        return GateLogic.shouldShowGate(
            packageName = packageName,
            today = GateLogic.today(clock),
            zone = clock.zone,
            guardedPackages = s.guardedPackages,
            gatePassedDate = s.gatePassedDate,
            targets = s.targets,
        )
    }
}
