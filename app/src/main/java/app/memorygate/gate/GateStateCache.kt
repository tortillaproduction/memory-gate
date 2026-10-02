package app.memorygate.gate

import app.memorygate.data.GuardedAppRepository
import app.memorygate.data.SettingsRepository
import app.memorygate.data.TargetRepository
import app.memorygate.domain.GateDecision
import app.memorygate.domain.GateLogic
import app.memorygate.domain.SnoozeLogic
import app.memorygate.domain.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
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

    /**
     * スヌーズ用ゲートを表示した日時の上書き（誘導先 ID → epoch millis）。
     * DB への保存が Flow に反映されるまでの間に、同じ誘導先で二重に表示しないようにする。
     */
    private val snoozeShownOverrides = ConcurrentHashMap<Long, Long>()

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

    /** 通常のゲート・スヌーズ用ゲートのどちらを表示するか（4.3・4.7）。読み込み完了前は表示しない */
    fun decide(packageName: String): GateDecision {
        val s = snapshot.value ?: return GateDecision.None
        return SnoozeLogic.decideGate(packageName, clock.instant(), clock.zone, s.guardedPackages, s.gatePassedDate, targets(s))
    }

    /** 次にスヌーズが可能になる時刻（5 章のタイマー用）。スヌーズ ON の誘導先がなければ null */
    fun nextSnoozeReadyAt(): Instant? {
        val s = snapshot.value ?: return null
        return SnoozeLogic.nextSnoozeReadyAt(targets(s), clock.instant(), clock.zone)
    }

    /** スヌーズ用ゲートを表示した（DB への保存とは別に、すぐ判定に反映する） */
    fun markSnoozeShown(targetId: Long, at: Long) {
        snoozeShownOverrides[targetId] = at
    }

    private fun targets(s: GateSnapshot): List<Target> =
        if (snoozeShownOverrides.isEmpty()) {
            s.targets
        } else {
            s.targets.map { t ->
                val override = snoozeShownOverrides[t.id]
                if (override != null && (t.lastSnoozeShownAt ?: Long.MIN_VALUE) < override) t.copy(lastSnoozeShownAt = override) else t
            }
        }
}
