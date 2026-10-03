package app.memorygate.ui.snooze

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * スヌーズ設定（SPEC 7.6）。アプリ全体で 1 つの設定。ON/OFF スイッチ・間隔・時間帯。
 * 見た目と動作は v0.1.6 までの誘導先の編集画面のスヌーズ設定と同じ（OFF にすると間隔・時間帯を隠す。値は保持する）。
 * 変更はすぐに保存する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeSettingsScreen(viewModel: SnoozeSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("スヌーズ設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        val settings = state.settings
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SnoozeSwitchRow(enabled = settings.enabled, onEnabledChange = viewModel::setEnabled)
            if (settings.enabled) {
                SnoozeIntervalChips(selected = settings.intervalMinutes, onSelect = viewModel::setInterval)
                SnoozeWindowPickers(
                    startMinutes = settings.startMinutes,
                    endMinutes = settings.endMinutes,
                    onStartChange = viewModel::setStart,
                    onEndChange = viewModel::setEnd,
                )
            }
            Text(
                "スヌーズ用ゲートには、誘導先のうち最後に開いてから最も時間がたっているもの（未訪問が最優先）を表示します。" +
                    "表示する画像は、誘導先の編集画面で設定できます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
