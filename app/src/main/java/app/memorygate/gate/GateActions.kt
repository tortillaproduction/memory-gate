package app.memorygate.gate

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.memorygate.appContainer
import app.memorygate.domain.Target
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * 通常のゲートとスヌーズ用ゲートで共通の操作（SPEC 4.4〜4.6）。
 */
internal object GateActions {

    /**
     * 4.4「開く」: lastVisitedAt と gatePassedDate を更新し、誘導先を開いてゲートを閉じる。
     * 起動できない場合（アプリが見つからないなど）はデータを更新せずに false を返す。
     * [fromSnooze]（スヌーズ用ゲートの「開く」）なら、開いた先のパッケージを記録する。
     */
    fun openTarget(activity: ComponentActivity, target: Target, fromSnooze: Boolean = false): Boolean {
        val container = activity.appContainer
        val launcher = container.targetLauncher
        if (!launcher.canLaunch(target)) return false
        activity.lifecycleScope.launch {
            // 保存の完了を待ってから開く（ブラウザ自体が監視対象アプリの場合に再度ゲートが出ないように）
            container.gateInteractor.onTargetOpened(target.id)
            activity.finish()
            val result = launcher.open(activity, target)
            if (result.opened) {
                // 開けたらスヌーズを休止する（通常のゲート・スヌーズ用ゲートのどちらでも。v0.1.8）。
                // 開いたアプリのウィンドウイベントより先にキャッシュへ反映されるよう、最初の中断まで同期的に実行する
                container.applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    container.gateInteractor.onTargetLaunched()
                }
            }
            if (fromSnooze && result.opened) {
                // 開いた先のアプリでは、前回の表示から間隔が経過するまで切り替え時の即時表示をしない（SPEC 5 章）。
                // 開いたアプリのウィンドウイベントより先に反映されるよう、同じメインスレッドの処理の中で記録する
                container.gateStateCache.markSnoozeOpened(result.packageName)
            }
            if (!result.opened) {
                Toast.makeText(activity.applicationContext, "開けませんでした", Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    /** 4.5 緊急退避 / 4.6 戻る: ホームへ移動してゲートを閉じる（データは更新しない） */
    fun goHome(activity: ComponentActivity) {
        activity.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        activity.finish()
    }
}
