package app.memorygate.ui.common

import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * システムナビゲーションバー（戻る・ホーム・履歴のボタンとその背景の帯）の表示制御（SPEC 7.0）。
 * ステータスバーには触れない。
 */
object NavigationBars {

    /** ナビゲーションバーを非表示にする。画面端からのスワイプでは一時的に表示される */
    fun hide(window: Window) {
        controller(window).hide(WindowInsetsCompat.Type.navigationBars())
    }

    /** ナビゲーションバーの表示・非表示を切り替える */
    fun toggle(window: Window) {
        val visible = ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.navigationBars())
            ?: false
        if (visible) hide(window) else controller(window).show(WindowInsetsCompat.Type.navigationBars())
    }

    private fun controller(window: Window): WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
}

/**
 * 子要素（ボタン・入力欄・スクロールなど）が処理しなかったタップで、ナビゲーションバーの表示を切り替える。
 * 子要素がタップを消費した場合は何もしない。
 *
 * @param window 対象のウィンドウ。省略時は Activity のウィンドウ
 */
@Composable
fun NavigationBarTapToggle(
    modifier: Modifier = Modifier,
    window: Window? = LocalActivity.current?.window,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(window) {
                if (window != null) detectTapGestures { NavigationBars.toggle(window) }
            },
    ) {
        content()
    }
}

/**
 * Compose の Dialog 内で使う。ダイアログのウィンドウでもナビゲーションバーを既定で非表示にし、
 * 背景のタップで表示を切り替えられるようにする。
 */
@Composable
fun DialogNavigationBarTapToggle(content: @Composable () -> Unit) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    LaunchedEffect(window) {
        window?.let(NavigationBars::hide)
    }
    NavigationBarTapToggle(window = window, content = content)
}
