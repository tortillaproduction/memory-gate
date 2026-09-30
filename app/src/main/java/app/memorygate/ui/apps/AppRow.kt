package app.memorygate.ui.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.memorygate.apps.InstalledApp
import app.memorygate.ui.common.AppIcon

/** アプリ一覧の 1 行。`disabledReason` があれば選択不可として理由を小さく表示する */
data class AppRowState(
    val app: InstalledApp,
    val selected: Boolean,
    val disabledReason: String? = null,
) {
    val enabled: Boolean get() = disabledReason == null
}

/** アプリ名・パッケージ名で絞り込む */
fun List<InstalledApp>.filterByQuery(query: String): List<InstalledApp> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
}

@Composable
fun AppListRow(
    row: AppRowState,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    ListItem(
        modifier = modifier.alpha(if (row.enabled) 1f else 0.5f),
        leadingContent = { AppIcon(row.app.packageName) },
        headlineContent = { Text(row.app.label) },
        supportingContent = {
            Column {
                Text(row.app.packageName, style = MaterialTheme.typography.bodySmall)
                row.disabledReason?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        trailingContent = trailing,
    )
}

@Composable
fun AppSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        placeholder = { Text("アプリを検索") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "検索をクリア")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}
