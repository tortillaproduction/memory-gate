package app.memorygate.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memorygate.BuildConfig
import app.memorygate.appContainer
import app.memorygate.ui.common.DISCLAIMER_TEXT
import app.memorygate.ui.common.TargetTypeIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAddTarget: () -> Unit,
    onEditTarget: (id: Long) -> Unit,
    onOpenGuardedApps: () -> Unit,
    onOpenOnboarding: () -> Unit,
    banner: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        viewModel.checkForUpdate()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory Gate") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("監視対象アプリ（${state.guardedCount}）") },
                            onClick = {
                                menuExpanded = false
                                onOpenGuardedApps()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("オンボーディングを再表示") },
                            onClick = {
                                menuExpanded = false
                                onOpenOnboarding()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("アプリ情報") },
                            onClick = {
                                menuExpanded = false
                                showAbout = true
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTarget,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("誘導先を追加") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            banner()
            state.update?.let { update ->
                UpdateBanner(
                    state = update,
                    onOpen = { context.appContainer.targetLauncher.openUrl(context, update.htmlUrl) },
                    onDismiss = { viewModel.dismissUpdate(update.version) },
                )
            }
            if (state.gatePassedToday) {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    headlineContent = { Text("今日はゲート通過済み") },
                    supportingContent = { Text("明日以降、期限切れの誘導先があれば再びゲートを表示します") },
                )
            }
            if (!state.loading && state.targets.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "誘導先がまだありません。\n右下のボタンから「やるべきこと」を追加してください。",
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.targets, key = { it.target.id }) { item ->
                        TargetCard(item = item, onClick = { onEditTarget(item.target.id) })
                    }
                }
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("閉じる") } },
            title = { Text("Memory Gate") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("バージョン ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
                    Text("免責事項", style = MaterialTheme.typography.titleSmall)
                    Text(DISCLAIMER_TEXT, style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}

/** 新しいバージョンのお知らせ（サービス状態のバナーの下に表示する） */
@Composable
private fun UpdateBanner(state: UpdateBannerState, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null)
            Text(
                "新しいバージョン v${state.version} が公開されています",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "閉じる")
            }
        }
        TextButton(onClick = onOpen, modifier = Modifier.padding(start = 52.dp, bottom = 4.dp)) {
            Text("ダウンロードページを開く")
        }
    }
}

@Composable
private fun TargetCard(item: HomeTargetItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            leadingContent = { TargetTypeIcon(item.target.type) },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.target.title, modifier = Modifier.weight(1f, fill = false))
                    if (item.isDue) {
                        Badge(
                            modifier = Modifier.padding(start = 8.dp),
                            containerColor = MaterialTheme.colorScheme.error,
                        ) { Text("期限切れ") }
                    }
                }
            },
            supportingContent = {
                Column {
                    Text("${item.schedule} ・ ${item.lastVisited}")
                    item.nextDue?.let { Text("次の期限: $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
        )
    }
}
