package app.memorygate.gate

import android.content.Intent
import android.os.Bundle
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
import app.memorygate.ui.common.NavigationBarTapToggle
import app.memorygate.ui.common.NavigationBars
import app.memorygate.ui.theme.MemoryGateTheme
import kotlinx.coroutines.launch

/**
 * スヌーズ用ゲート（SPEC 7.5）。通常のゲートとは別の Activity・別のタスクにする。
 * 「開く」・緊急退避・戻る操作は通常のゲートと同じ（[GateActions]）。「スヌーズを止める」はスヌーズ用ゲートだけ。
 */
class SnoozeGateActivity : ComponentActivity() {

    private val viewModel: SnoozeGateViewModel by viewModels {
        viewModelFactory { initializer { SnoozeGateViewModel(appContainer) } }
    }

    /** 閉じる処理の途中か。二重タップを防ぎ、「開く」と「スヌーズを止める」のどちらかを押したらもう一方は受け付けない */
    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 背景をステータスバー・ナビゲーションバーの裏まで表示する
        enableEdgeToEdge()

        // 戻るボタン / 戻るジェスチャーは緊急退避と同じくホームへ移動する
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = GateActions.goHome(this@SnoozeGateActivity)
            },
        )

        showTarget(intent)
        setContent {
            MemoryGateTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                // 誘導先が削除された場合は閉じる
                LaunchedEffect(state) {
                    if (!state.loading && state.target == null && !closing) finish()
                }
                NavigationBarTapToggle {
                    SnoozeGateScreen(
                        state = state,
                        onOpen = ::openTarget,
                        onStopSnooze = ::stopSnooze,
                        onEscape = { GateActions.goHome(this) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ナビゲーションバーは既定で非表示（SPEC 7.0）
        NavigationBars.hide(window)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showTarget(intent)
    }

    private fun showTarget(intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TARGET_ID, -1L)
        if (id < 0) finish() else viewModel.show(id)
    }

    /** 4.4 と同じ: lastVisitedAt と gatePassedDate を更新して誘導先を開く */
    private fun openTarget() {
        if (closing) return
        val target = viewModel.uiState.value.target ?: return
        if (GateActions.openTarget(this, target)) {
            closing = true
        } else {
            viewModel.show(target.id)
        }
    }

    /**
     * 「スヌーズを止める」→ 確認ダイアログで「OFF にする」: アプリ全体のスヌーズを OFF にし、
     * スヌーズ用ゲートを閉じて元のアプリに戻る（ホームへは移動しない。lastVisitedAt と gatePassedDate は更新しない）
     */
    private fun stopSnooze() {
        if (closing) return
        closing = true
        lifecycleScope.launch {
            appContainer.gateInteractor.onSnoozeStopped()
            finish()
        }
    }

    companion object {
        const val EXTRA_TARGET_ID = "targetId"
    }
}
