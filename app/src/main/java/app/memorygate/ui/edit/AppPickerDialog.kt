package app.memorygate.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.memorygate.apps.InstalledApp
import app.memorygate.ui.apps.AppListRow
import app.memorygate.ui.apps.AppSearchField
import app.memorygate.ui.common.DialogNavigationBarTapToggle

/** 誘導先（type=APP）のアプリ選択ダイアログ（単一選択） */
@Composable
fun AppPickerDialog(
    state: AppPickerUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogNavigationBarTapToggle {
            Surface(Modifier.fillMaxSize().padding(16.dp), shape = MaterialTheme.shapes.large) {
                Column {
                    Text("アプリを選択", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                    AppSearchField(query = state.query, onQueryChange = onQueryChange)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            state.rows.isEmpty() -> Text("該当するアプリがありません", Modifier.align(Alignment.Center))
                            else -> LazyColumn {
                                items(state.rows, key = { it.app.packageName }) { row ->
                                    AppListRow(
                                        row = row,
                                        modifier = Modifier.clickable(enabled = row.enabled) { onSelect(row.app) },
                                    ) {
                                        RadioButton(selected = row.selected, onClick = null, enabled = row.enabled)
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) {
                        Text("キャンセル")
                    }
                }
            }
        }
    }
}
