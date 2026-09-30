package app.memorygate.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.memorygate.domain.TargetType

@Composable
fun TargetTypeIcon(type: TargetType, modifier: Modifier = Modifier) {
    when (type) {
        TargetType.URL -> Icon(Icons.Outlined.Language, contentDescription = "Web", modifier = modifier)
        TargetType.APP -> Icon(Icons.Outlined.Apps, contentDescription = "アプリ", modifier = modifier)
    }
}
