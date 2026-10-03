package app.memorygate.ui.snooze

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.memorygate.domain.SnoozeFormat
import app.memorygate.domain.SnoozeSettings

/**
 * スヌーズの ON/OFF スイッチ（SPEC 7.6）。左に見出しと説明文を置く。
 * 説明文は 1 行に収め、文字の列は残りの幅を取り、スイッチとの間に 16dp あける。
 */
@Composable
fun SnoozeSwitchRow(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text("スヌーズ", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "時間帯の間、指定の間隔で表示します",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

/** 間隔のチップ（「1分おき」「5分おき」「30分おき」「1時間おき」） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnoozeIntervalChips(selected: Int, onSelect: (Int) -> Unit) {
    Text("間隔", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SnoozeSettings.ALLOWED_INTERVAL_MINUTES.forEach { minutes ->
            FilterChip(
                selected = selected == minutes,
                onClick = { onSelect(minutes) },
                label = { Text(SnoozeFormat.interval(minutes)) },
            )
        }
    }
}

/** 時間帯（開始・終了の TimePicker）と補足 */
@Composable
fun SnoozeWindowPickers(
    startMinutes: Int,
    endMinutes: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    var editingTime by rememberSaveable { mutableStateOf<TimeField?>(null) }
    Text("時間帯", style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { editingTime = TimeField.START }) { Text(SnoozeFormat.time(startMinutes)) }
        Text("〜")
        OutlinedButton(onClick = { editingTime = TimeField.END }) { Text(SnoozeFormat.time(endMinutes)) }
    }
    Text(
        SnoozeFormat.windowNote(startMinutes, endMinutes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    editingTime?.let { field ->
        TimePickerDialog(
            initialMinutes = if (field == TimeField.START) startMinutes else endMinutes,
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
