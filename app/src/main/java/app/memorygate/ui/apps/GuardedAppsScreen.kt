package app.memorygate.ui.apps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardedAppsScreen(
    viewModel: GuardedAppsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("監視対象アプリ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            GuardedAppsContent(
                state = state,
                onQueryChange = viewModel::setQuery,
                onOnlySelectedChange = viewModel::setOnlySelected,
                onToggle = viewModel::setGuarded,
            )
        }
    }
}

/** 検索・フィルタ・一覧（オンボーディングからも使う） */
@Composable
fun GuardedAppsContent(
    state: GuardedAppsUiState,
    onQueryChange: (String) -> Unit,
    onOnlySelectedChange: (Boolean) -> Unit,
    onToggle: (packageName: String, guarded: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        AppSearchField(query = state.query, onQueryChange = onQueryChange)
        FilterChip(
            selected = state.onlySelected,
            onClick = { onOnlySelectedChange(!state.onlySelected) },
            label = { Text("選択中のみ表示（${state.selectedCount}）") },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("該当するアプリがありません") }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.rows, key = { it.app.packageName }) { row ->
                    AppListRow(
                        row = row,
                        modifier = Modifier.toggleable(
                            value = row.selected,
                            enabled = row.enabled,
                            role = Role.Checkbox,
                            onValueChange = { onToggle(row.app.packageName, it) },
                        ),
                    ) {
                        Checkbox(checked = row.selected, onCheckedChange = null, enabled = row.enabled)
                    }
                }
            }
        }
    }
}
