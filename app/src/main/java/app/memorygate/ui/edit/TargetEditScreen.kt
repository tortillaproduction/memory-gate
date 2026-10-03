package app.memorygate.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.memorygate.domain.TargetFormat
import app.memorygate.domain.TargetType
import app.memorygate.ui.common.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetEditScreen(
    viewModel: TargetEditViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickerState by viewModel.appPickerState.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "誘導先を追加" else "誘導先を編集") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "削除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("タイトル（必須）") },
                placeholder = { Text("例: 確定申告、Duolingo") },
                singleLine = true,
                isError = state.titleError != null,
                supportingText = state.titleError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("種別")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val types = listOf(TargetType.URL to "Web サイト", TargetType.APP to "アプリ")
                types.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size),
                    ) { Text(label) }
                }
            }

            when (state.type) {
                TargetType.URL -> OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::setUrl,
                    label = { Text("URL") },
                    placeholder = { Text("https://") },
                    singleLine = true,
                    isError = state.urlError != null,
                    supportingText = state.urlError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                TargetType.APP -> Column {
                    OutlinedButton(
                        onClick = {
                            viewModel.openAppPicker()
                            showAppPicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val pkg = state.packageName
                        if (pkg != null) {
                            AppIcon(pkg, size = 24.dp)
                            Spacer(Modifier.padding(4.dp))
                            Text(state.appLabel ?: pkg)
                        } else {
                            Text("アプリを選択")
                        }
                    }
                    state.appError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SectionTitle("スケジュール")
            Column(Modifier.selectableGroup()) {
                ScheduleOption.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.schedule == option,
                                onClick = { viewModel.setSchedule(option) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.schedule == option, onClick = null)
                        Text(option.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (state.schedule == ScheduleOption.WEEKLY) {
                DayOfWeekChips(selected = state.dayOfWeek, onSelect = viewModel::setDayOfWeek)
            }

            HorizontalDivider()
            SnoozeSection(
                state = state,
                onImagePicked = viewModel::importSnoozeImage,
                onImageRemoved = viewModel::removeSnoozeImage,
            )

            if (!state.isNew) {
                HorizontalDivider()
                SectionTitle("最終訪問日")
                Text(state.lastVisitedLabel)
                if (state.hasVisited) {
                    OutlinedButton(onClick = viewModel::resetVisited) { Text("未訪問に戻す") }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::save, enabled = !state.importingImage, modifier = Modifier.fillMaxWidth()) { Text("保存") }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("誘導先を削除") },
            text = { Text("「${state.title}」を削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") } },
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            state = pickerState,
            onQueryChange = viewModel::setPickerQuery,
            onSelect = {
                viewModel.selectApp(it)
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfWeekChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..7).forEach { day ->
            FilterChip(
                selected = selected == day,
                onClick = { onSelect(day) },
                label = { Text(TargetFormat.dayOfWeekShort(day)) },
            )
        }
    }
}
