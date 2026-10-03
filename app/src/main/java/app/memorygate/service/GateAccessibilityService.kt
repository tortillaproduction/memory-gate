package app.memorygate.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import app.memorygate.AppContainer
import app.memorygate.appContainer
import app.memorygate.domain.GateDecision
import app.memorygate.gate.GateActivity
import app.memorygate.gate.GateStateCache
import app.memorygate.gate.SnoozeGateActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 指定アプリの起動を検知してゲート画面を表示する（SPEC 5 章）。画面の内容は読み取らない。
 *
 * - 監視対象アプリが前面に来たら、通常のゲート → スヌーズ用ゲートの順に判定する。別のアプリからの切り替えなら、
 *   スヌーズは間隔に関係なくすぐに出す（自アプリ・IME・SystemUI を経由して同じアプリに戻った場合は切り替えとみなさない）
 * - 監視対象アプリが前面にある間は、次にスヌーズが可能になる時刻にタイマーを設定し、発火したら再判定する（定期表示）
 * - 判定はすべてメモリ上のキャッシュ（[GateStateCache]）で行い、イベントごとに DB を読まない
 *
 * バックグラウンドからの Activity 起動は「システムにバインドされた AccessibilityService」の例外で
 * 許可される想定。実機で起動できない場合のフォールバック案として、SYSTEM_ALERT_WINDOW（他のアプリの上に重ねて表示）
 * 権限を取得し、その権限による例外で Activity を起動する方法がある（MVP では実装しない）。
 */
class GateAccessibilityService : AccessibilityService() {

    private lateinit var container: AppContainer
    private lateinit var cache: GateStateCache
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 直前にフォアグラウンドになったパッケージ（デバウンス用） */
    private var lastPackage: String? = null

    /** 監視対象アプリへの「切り替え」の判定（自アプリ・IME・SystemUI を除いた直前の前面を覚えておく） */
    private lateinit var foregroundTracker: ForegroundTracker

    /** 前面にある監視対象アプリ（自アプリ・IME・SystemUI が一時的に前面に来ても保持する） */
    private var foregroundGuarded: String? = null

    /** 現在前面にあるパッケージ（自アプリのゲートが表示中かの判定用） */
    private var currentForeground: String? = null

    private var timerJob: Job? = null
    private var imePackages: Set<String> = emptySet()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> cancelTimer()
                // 画面が点いて監視対象アプリが前面にあれば再判定する
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> foregroundGuarded?.let(::evaluate)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        container = appContainer
        // 購読を開始してメモリ上にキャッシュする
        cache = container.gateStateCache
        imePackages = loadImePackages()
        foregroundTracker = ForegroundTracker(packageName)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == lastPackage) return
        lastPackage = pkg
        currentForeground = pkg
        if (!::cache.isInitialized) return

        val guarded = cache.snapshot.value?.guardedPackages.orEmpty()
        val change = foregroundTracker.onForeground(pkg, guarded, imePackages)
        when (change.kind) {
            // 自アプリ（ゲート画面自身など）・IME・SystemUI は判定せず、タイマーも止めない
            ForegroundKind.TRANSIENT -> Unit
            ForegroundKind.GUARDED -> {
                foregroundGuarded = pkg
                // 別のアプリから切り替えてきたときは、間隔に関係なくすぐにスヌーズ用ゲートを出す
                evaluate(pkg, switched = change.switched)
            }
            ForegroundKind.OTHER -> {
                foregroundGuarded = null
                cancelTimer()
            }
        }
    }

    /**
     * 通常のゲート → スヌーズ用ゲートの順に判定し、どちらも出ないときはタイマーを設定する。
     * [switched]（監視対象アプリへの切り替え）なら、スヌーズは間隔に関係なく出す
     */
    private fun evaluate(pkg: String, switched: Boolean = false) {
        cancelTimer()
        // 画面がオフのときは表示しない（画面が点いたら再判定する）
        if (!isInteractive()) return
        when (val decision = cache.decide(pkg, switched)) {
            GateDecision.Normal -> startActivity(
                Intent(this, GateActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(GateActivity.EXTRA_SOURCE_PACKAGE, pkg),
            )
            is GateDecision.Snooze -> showSnooze(decision.target.id)
            // ゲートを出したときは、閉じて監視対象アプリに戻ったとき（イベント）に再判定するのでタイマーは不要
            GateDecision.None -> scheduleTimer()
        }
    }

    private fun showSnooze(targetId: Long) {
        val now = container.clock.millis()
        // 表示した時点で lastSnoozeShownAt（アプリ全体）を保存する（キャッシュにはすぐ反映する）
        cache.markSnoozeShown(now)
        container.applicationScope.launch { container.settingsRepository.setLastSnoozeShownAt(now) }
        startActivity(
            Intent(this, SnoozeGateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(SnoozeGateActivity.EXTRA_TARGET_ID, targetId),
        )
    }

    private fun scheduleTimer() {
        val delayMillis = SnoozeInterruptPolicy.timerDelayMillis(cache.nextSnoozeReadyAt(), container.clock.instant()) ?: return
        timerJob = scope.launch {
            delay(delayMillis)
            onTimer()
        }
    }

    private fun onTimer() {
        val pkg = foregroundGuarded ?: return
        // 自アプリのゲートが表示中なら、閉じて戻ったときのイベントで再判定する
        if (currentForeground == packageName) return
        evaluate(pkg)
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun isInteractive(): Boolean = getSystemService(PowerManager::class.java)?.isInteractive ?: true

    private fun loadImePackages(): Set<String> = runCatching {
        getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()
    }.getOrDefault(emptySet())

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        super.onDestroy()
    }
}
