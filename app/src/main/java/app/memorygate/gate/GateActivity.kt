package app.memorygate.gate

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.memorygate.appContainer
import app.memorygate.ui.theme.MemoryGateTheme
import kotlinx.coroutines.launch

/**
 * ゲート画面（SPEC 4.4〜4.6、7.5）。
 * singleTask のため、表示中に再度起動された場合は [onNewIntent] で内容だけ更新する。
 */
class GateActivity : ComponentActivity() {

    private val viewModel: GateViewModel by viewModels {
        viewModelFactory { initializer { GateViewModel(appContainer) } }
    }

    /** 二重タップで複数の誘導先を開かないようにする */
    private var opening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 戻るボタン / 戻るジェスチャーは緊急退避と同じくホームへ移動する
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = goHome()
            },
        )

        viewModel.refresh()
        setContent {
            MemoryGateTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                // 表示中に期限切れがなくなった場合（誘導先の削除など）は閉じる
                LaunchedEffect(state) {
                    if (!state.loading && state.items.isEmpty() && !opening) finish()
                }
                GateScreen(state = state, onOpen = ::openTarget, onEscape = ::goHome)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.refresh()
    }

    /** 4.4 ゲートのボタンを押したとき */
    private fun openTarget(item: GateItem) {
        if (opening) return
        val launcher = appContainer.targetLauncher
        if (!launcher.canLaunch(item.target)) {
            // 起動できない場合は lastVisitedAt / gatePassedDate を更新しない
            viewModel.refresh()
            return
        }
        opening = true
        lifecycleScope.launch {
            appContainer.gateInteractor.onTargetOpened(item.target.id)
            finish()
            if (!launcher.open(this@GateActivity, item.target)) {
                Toast.makeText(applicationContext, "開けませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 4.5 緊急退避 / 4.6 戻る: ホームへ移動してゲートを閉じる（データは更新しない） */
    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        const val EXTRA_SOURCE_PACKAGE = "sourcePackage"
    }
}
