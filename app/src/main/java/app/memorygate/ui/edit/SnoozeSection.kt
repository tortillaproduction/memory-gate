package app.memorygate.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.memorygate.ui.common.FileImage

/**
 * 誘導先の編集画面の「スヌーズ用ゲートの画像（任意）」（SPEC 7.3）。
 * スヌーズの ON/OFF・間隔・時間帯はアプリ全体の「スヌーズ設定」画面にある（v0.1.7）。スヌーズが OFF でも画像は設定できる。
 */
@Composable
fun SnoozeSection(
    state: TargetEditUiState,
    onImagePicked: (android.net.Uri) -> Unit,
    onImageRemoved: () -> Unit,
) {
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    Text("スヌーズ用ゲートの画像（任意）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val path = state.snoozeImagePath
            when {
                state.importingImage -> CircularProgressIndicator(Modifier.size(32.dp))
                // スヌーズ用ゲートの丸い画像と同じ表示方法（縦横比を保って円いっぱい、中央合わせ）
                path != null -> FileImage(
                    path,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                ) { ImagePlaceholder() }
                else -> ImagePlaceholder()
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
                onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !state.importingImage,
            ) { Text(if (state.snoozeImagePath == null) "画像を選ぶ" else "画像を変更") }
            if (state.snoozeImagePath != null) {
                TextButton(onClick = onImageRemoved, enabled = !state.importingImage) { Text("画像を削除") }
            }
        }
    }
    state.imageError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    if (state.snoozeImagePath == null) {
        Text(
            "画像がない場合は、アプリのアイコン（Web サイトは地球のアイコン）を表示します",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImagePlaceholder() {
    Icon(Icons.Outlined.Image, contentDescription = null, tint = Color.Gray)
}
