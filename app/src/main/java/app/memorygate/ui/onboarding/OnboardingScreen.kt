package app.memorygate.ui.onboarding

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memorygate.service.SystemStatus
import app.memorygate.ui.common.DISCLAIMER_TEXT
import app.memorygate.ui.apps.GuardedAppsContent
import app.memorygate.ui.apps.GuardedAppsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    guardedAppsViewModel: GuardedAppsViewModel,
    onAddTarget: () -> Unit,
    onFinished: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.updateSystemStatus(SystemStatus.read(context))
        guardedAppsViewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("セットアップ（${state.index + 1}/${state.steps.size}）") })
        },
        bottomBar = {
            // ナビゲーションバーが表示されているときも、ボタンが帯に重ならないようにする
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = viewModel::back, enabled = state.index > 0) { Text("戻る") }
                if (state.isLast) {
                    Button(onClick = { viewModel.complete(onFinished) }) { Text("完了") }
                } else {
                    val skippable = state.current in setOf(OnboardingStep.TARGETS, OnboardingStep.GUARDED_APPS)
                    val label = if (skippable && state.current !in state.done) "スキップ" else "次へ"
                    Button(onClick = viewModel::next) { Text(label) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LinearProgressIndicator(
                progress = { (state.index + 1f) / state.steps.size },
                modifier = Modifier.fillMaxWidth(),
            )
            StepHeader(title = state.current.title, done = state.current in state.done)
            val scrollable = state.current != OnboardingStep.GUARDED_APPS
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.current) {
                    OnboardingStep.INTRO -> IntroStep()
                    OnboardingStep.RESTRICTED_SETTINGS -> RestrictedSettingsStep(context)
                    OnboardingStep.ACCESSIBILITY -> AccessibilityStep(context, state.current in state.done)
                    OnboardingStep.BATTERY -> BatteryStep(context, state.current in state.done)
                    OnboardingStep.MANUFACTURER -> ManufacturerStep(
                        manufacturer = state.manufacturer,
                        done = state.current in state.done,
                        onDoneChange = viewModel::setManufacturerStepDone,
                    )
                    OnboardingStep.TARGETS -> TargetsStep(state.targetTitles, onAddTarget)
                    OnboardingStep.GUARDED_APPS -> GuardedAppsStep(guardedAppsViewModel)
                }
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, done: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (done) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "完了", tint = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = "未完了")
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun IntroStep() {
    Text("Memory Gate は、SNS などの「つい開いてしまうアプリ」を開いたときに、期限切れの「やるべきこと」があればゲート画面を表示して、そちらへ誘導するアプリです。")
    Text("しくみ", style = MaterialTheme.typography.titleMedium)
    Text("・「誘導先」として、定期的に開きたい Web サイトやアプリと、その頻度を登録します")
    Text("・「監視対象アプリ」として、暇つぶしに開いてしまうアプリを選びます")
    Text("・監視対象アプリを開いたとき、期限切れの誘導先があればゲートが表示されます。どれか 1 つを開けば、その日はもうゲートは出ません")
    Text("・どうしても今すぐ離れたいときは、ゲート下部のボタンを 3 秒長押しするとホーム画面へ退避できます")
    Text(
        "データはこの端末内にだけ保存され、外部へ送信されることはありません。新しいバージョンの確認のため、GitHub に最新のバージョン番号を問い合わせます。",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(DISCLAIMER_TEXT, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun RestrictedSettingsStep(context: Context) {
    Text("ストア外からインストールしたアプリはユーザー補助を有効にできないことがあります。アプリ情報 → 右上の︙ → 『制限付き設定を許可』をタップしてください")
    Text(
        "メニューに項目が見つからない場合は、先に次のステップでユーザー補助を一度オンにしようとしてから、もう一度この画面を開いてください。ユーザー補助を有効にできれば、このステップは完了です。",
        style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = {
        context.startSettings(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
        )
    }) { Text("アプリ情報を開く") }
}

@Composable
private fun AccessibilityStep(context: Context, enabled: Boolean) {
    Text("監視対象アプリが開かれたことを検知するために、ユーザー補助で「Memory Gate」をオンにしてください。")
    Text("アプリの起動を検知する目的にのみ使用し、画面の内容は読み取りません。", style = MaterialTheme.typography.bodySmall)
    StatusText(enabled, doneText = "有効になっています", notDoneText = "まだ有効になっていません")
    Button(onClick = { context.startSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
        Text("ユーザー補助の設定を開く")
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun BatteryStep(context: Context, done: Boolean) {
    Text("バックグラウンドで検知を続けられるように、電池の最適化の対象から外してください。")
    StatusText(done, doneText = "除外されています", notDoneText = "まだ除外されていません")
    Button(onClick = {
        context.startSettings(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
        )
    }) { Text("電池の最適化から除外する") }
}

@Composable
private fun ManufacturerStep(manufacturer: Manufacturer?, done: Boolean, onDoneChange: (Boolean) -> Unit) {
    Text("この端末では、アプリが停止されないように追加の設定が必要です。")
    when (manufacturer) {
        Manufacturer.OPPO -> Text("設定 → バッテリー → アプリのバッテリー管理 → Memory Gate で『バックグラウンド実行を許可』『自動起動を許可』をオン")
        Manufacturer.MOTOROLA -> Text("設定 → アプリ → Memory Gate → アプリのバッテリー使用量 → 『制限なし』を選択")
        null -> Unit
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = done, onCheckedChange = onDoneChange)
        Text("設定しました")
    }
}

@Composable
private fun TargetsStep(titles: List<String>, onAddTarget: () -> Unit) {
    Text("定期的に開きたい Web サイトやアプリ（誘導先）を 1 件以上登録してください。あとからホーム画面でも追加できます。")
    if (titles.isEmpty()) {
        Text("まだ登録されていません", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        titles.forEach { Text("・$it") }
    }
    OutlinedButton(onClick = onAddTarget) { Text("誘導先を追加") }
}

@Composable
private fun ColumnScope.GuardedAppsStep(viewModel: GuardedAppsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Text("ゲートを表示したいアプリ（監視対象アプリ）を 1 つ以上選んでください。")
    GuardedAppsContent(
        state = state,
        onQueryChange = viewModel::setQuery,
        onOnlySelectedChange = viewModel::setOnlySelected,
        onShowSystemAppsChange = viewModel::setShowSystemApps,
        onToggle = viewModel::setGuarded,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun StatusText(done: Boolean, doneText: String, notDoneText: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (done) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(doneText, color = MaterialTheme.colorScheme.primary)
        } else {
            Text(notDoneText, color = MaterialTheme.colorScheme.error)
        }
    }
    Spacer(Modifier.size(4.dp))
}

private fun Context.startSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // 端末によっては画面が存在しないため、アプリ情報画面にフォールバックする
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }
}

