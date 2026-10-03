package app.memorygate.gate

import app.memorygate.data.GuardedAppRepository
import app.memorygate.data.SettingsRepository
import app.memorygate.data.TargetRepository
import app.memorygate.domain.GateDecision
import app.memorygate.domain.GateLogic
import app.memorygate.domain.SnoozeLogic
import app.memorygate.domain.SnoozeSettings
import app.memorygate.domain.Target
import app.memorygate.service.SnoozeInterruptPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** ゲート判定に必要な状態のスナップショット */
data class GateSnapshot(
    val guardedPackages: Set<String>,
    val targets: List<Target>,
    val gatePassedDate: LocalDate?,
    val snoozeSettings: SnoozeSettings,
    /** スヌーズ用ゲートを最後に表示した日時（アプリ全体） */
    val lastSnoozeShownAt: Long?,
    /** ゲートの「開く」で誘導先を開いた日時（スヌーズの休止の開始） */
    val snoozePausedAt: Long? = null,
)

/**
 * 監視対象アプリ・誘導先・gatePassedDate・スヌーズの設定を Flow で購読してメモリ上に保持する（SPEC 5 章）。
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
        settingsRepository.snoozeSettings,
        combine(settingsRepository.lastSnoozeShownAt, settingsRepository.snoozePausedAt) { shown, paused -> shown to paused },
    ) { guarded, targets, passed, snooze, (lastShown, pausedAt) ->
        GateSnapshot(guarded, targets, passed, snooze, lastShown, pausedAt)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * スヌーズ用ゲートを表示した日時の上書き（epoch millis）。
     * DataStore への保存が Flow に反映されるまでの間に、二重に表示しないようにする。
     */
    @Volatile
    private var snoozeShownOverride: Long? = null

    /**
     * スヌーズの休止の開始（「開く」で誘導先を開いた日時）の上書き。
     * DataStore への保存が Flow に反映されるまでの間も、すぐに休止させる。
     */
    @Volatile
    private var snoozePausedOverride: Long? = null

    /** スヌーズ用ゲートの「開く」で開いた先のパッケージ（SPEC 5 章の堂々巡りの防止。メモリ上だけに保持する） */
    @Volatile
    private var snoozeOpenedPackage: String? = null

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

    /**
     * 通常のゲート・スヌーズ用ゲートのどちらを表示するか（4.3・4.7）。読み込み完了前は表示しない。
     *
     * @param switched 監視対象アプリへの切り替えか。切り替え時は間隔に関係なくスヌーズ用ゲートを出す
     *   （ただし「開く」で開いた先のパッケージでは、前回の表示から間隔が経過するまで出さない）
     */
    fun decide(packageName: String, switched: Boolean = false): GateDecision {
        val s = snapshot.value ?: return GateDecision.None
        val now = clock.instant()
        val lastShown = lastShownAt(s)
        val immediate = switched &&
            SnoozeInterruptPolicy.allowImmediate(packageName, snoozeOpenedPackage, s.snoozeSettings, lastShown, now)
        return SnoozeLogic.decideGate(
            packageName = packageName,
            now = now,
            zone = clock.zone,
            guardedPackages = s.guardedPackages,
            gatePassedDate = s.gatePassedDate,
            targets = s.targets,
            settings = s.snoozeSettings,
            lastShownAt = lastShown,
            immediate = immediate,
            pausedAt = pausedAt(s),
        )
    }

    /** 次にスヌーズが可能になる時刻（5 章のタイマー用）。スヌーズ OFF・誘導先なしなら null */
    fun nextSnoozeReadyAt(): Instant? {
        val s = snapshot.value ?: return null
        return SnoozeLogic.nextSnoozeReadyAt(s.snoozeSettings, lastShownAt(s), s.targets, clock.instant(), clock.zone, pausedAt(s))
    }

    /** スヌーズ用ゲートを表示した（DataStore への保存とは別に、すぐ判定に反映する） */
    fun markSnoozeShown(at: Long) {
        snoozeShownOverride = maxOf(at, snoozeShownOverride ?: Long.MIN_VALUE)
    }

    /** スヌーズ用ゲートの「開く」で開いた先のパッケージを記録する（不明なら null） */
    fun markSnoozeOpened(packageName: String?) {
        snoozeOpenedPackage = packageName
    }

    /** ゲートの「開く」で誘導先を開いた（スヌーズを休止する。DataStore への保存とは別に、すぐ判定に反映する） */
    fun markSnoozePaused(at: Long) {
        snoozePausedOverride = maxOf(at, snoozePausedOverride ?: Long.MIN_VALUE)
    }

    private fun pausedAt(s: GateSnapshot): Long? {
        val override = snoozePausedOverride ?: return s.snoozePausedAt
        return maxOf(override, s.snoozePausedAt ?: Long.MIN_VALUE)
    }

    /** 保存された値と、すぐ反映した値のうち新しいほう */
    private fun lastShownAt(s: GateSnapshot): Long? {
        val override = snoozeShownOverride ?: return s.lastSnoozeShownAt
        return maxOf(override, s.lastSnoozeShownAt ?: Long.MIN_VALUE)
    }
}
