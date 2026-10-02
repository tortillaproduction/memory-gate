package app.memorygate.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.memorygate.domain.SnoozeFormat
import app.memorygate.domain.Target
import app.memorygate.ui.common.FileImage

/** 誘導先の編集画面の「スヌーズ」設定（SPEC 7.3） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnoozeSection(
    state: TargetEditUiState,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    onImagePicked: (android.net.Uri) -> Unit,
    onImageRemoved: () -> Unit,
) {
    var editingTime by remember { mutableStateOf<TimeField?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = state.snoozeEnabled, role = Role.Switch, onValueChange = onEnabledChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("スヌーズ", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "ゲートを通過した日も、時間帯の間は指定した間隔で知らせます",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.snoozeEnabled, onCheckedChange = null)
    }
    if (!state.snoozeEnabled) return

    Text("間隔", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Target.ALLOWED_SNOOZE_INTERVAL_MINUTES.forEach { minutes ->
            FilterChip(
                selected = state.snoozeIntervalMinutes == minutes,
                onClick = { onIntervalChange(minutes) },
                label = { Text(SnoozeFormat.interval(minutes)) },
            )
        }
    }

    Text("時間帯", style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { editingTime = TimeField.START }) { Text(SnoozeFormat.time(state.snoozeStartMinutes)) }
        Text("〜")
        OutlinedButton(onClick = { editingTime = TimeField.END }) { Text(SnoozeFormat.time(state.snoozeEndMinutes)) }
    }
    Text(
        SnoozeFormat.windowNote(state.snoozeStartMinutes, state.snoozeEndMinutes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("画像（任意）", style = MaterialTheme.typography.labelLarge)
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
                path != null -> FileImage(path, Modifier.size(96.dp)) { ImagePlaceholder() }
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

    editingTime?.let { field ->
        TimePickerDialog(
            initialMinutes = if (field == TimeField.START) state.snoozeStartMinutes else state.snoozeEndMinutes,
            title = if (field == TimeField.START) "開始時刻" else "終了時刻",
            onDismiss = { editingTime = null },
            onConfirm = { minutes ->
                if (field == TimeField.START) onStartChange(minutes) else onEndChange(minutes)
                editingTime = null
            },
        )
    }
}

private enum class TimeField { START, END }

@Composable
private fun ImagePlaceholder() {
    Icon(Icons.Outlined.Image, contentDescription = null, tint = Color.Gray)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMinutes: Int, title: String, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val pickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = pickerState) },
        confirmButton = { TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
