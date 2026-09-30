package app.memorygate.gate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.memorygate.ui.common.TargetTypeIcon

@Composable
fun GateScreen(
    state: GateUiState,
    onOpen: (GateItem) -> Unit,
    onEscape: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Spacer(Modifier.height(48.dp))
            Text(
                "やることがあります",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Text(
                "どれか 1 つを開くと、今日はもうゲートを表示しません",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.target.id }) { item ->
                    GateTargetCard(item = item, onOpen = { onOpen(item) })
                }
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                EmergencyEscapeButton(onTrigger = onEscape)
            }
        }
    }
}

@Composable
private fun GateTargetCard(item: GateItem, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { TargetTypeIcon(item.target.type) },
            headlineContent = { Text(item.target.title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Column {
                    Text(item.lastVisited)
                    if (!item.launchable) {
                        Text("アプリが見つかりません", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            trailingContent = {
                Button(onClick = onOpen, enabled = item.launchable) { Text("開く") }
            },
        )
    }
}
