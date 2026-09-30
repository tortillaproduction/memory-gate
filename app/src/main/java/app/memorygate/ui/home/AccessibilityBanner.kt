package app.memorygate.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.memorygate.service.AccessibilityStatus

/** サービス状態のバナー。ユーザー補助が無効なら赤で警告する（onResume で再確認） */
@Composable
fun AccessibilityBanner(onClick: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        enabled = AccessibilityStatus.isGateServiceEnabled(context)
        onPauseOrDispose { }
    }
    if (enabled) return
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            headlineColor = MaterialTheme.colorScheme.onErrorContainer,
            supportingColor = MaterialTheme.colorScheme.onErrorContainer,
            leadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        leadingContent = { Icon(Icons.Filled.Warning, contentDescription = null) },
        headlineContent = { Text("ユーザー補助が無効です") },
        supportingContent = { Text("ゲートが表示されません。タップして設定してください") },
    )
}
