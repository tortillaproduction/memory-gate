package app.memorygate.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import app.memorygate.appContainer
import app.memorygate.gate.GateActivity
import app.memorygate.gate.GateStateCache

/**
 * 指定アプリの起動を検知してゲート画面を表示する（SPEC 5 章）。画面の内容は読み取らない。
 *
 * バックグラウンドからの Activity 起動は「システムにバインドされた AccessibilityService」の例外で
 * 許可される想定。実機で起動できない場合のフォールバック案として、SYSTEM_ALERT_WINDOW（他のアプリの上に重ねて表示）
 * 権限を取得し、その権限による例外で Activity を起動する方法がある（MVP では実装しない）。
 */
class GateAccessibilityService : AccessibilityService() {

    private lateinit var cache: GateStateCache

    /** 直前にフォアグラウンドになったパッケージ（デバウンス用） */
    private var lastPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 購読を開始してメモリ上にキャッシュする
        cache = appContainer.gateStateCache
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == lastPackage) return
        lastPackage = pkg
        // 自アプリ（ゲート画面自身など）は判定しない
        if (pkg == packageName) return
        if (!::cache.isInitialized) return

        if (cache.shouldShowGate(pkg)) {
            startActivity(
                Intent(this, GateActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(GateActivity.EXTRA_SOURCE_PACKAGE, pkg),
            )
        }
    }

    override fun onInterrupt() = Unit
}
