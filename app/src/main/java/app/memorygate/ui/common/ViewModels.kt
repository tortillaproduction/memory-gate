package app.memorygate.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.memorygate.AppContainer
import app.memorygate.MemoryGateApp

/** [AppContainer] から ViewModel を生成する（手動 DI 用のヘルパー） */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (container: AppContainer, savedStateHandle: SavedStateHandle) -> VM,
): VM = viewModel {
    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MemoryGateApp
    create(app.container, createSavedStateHandle())
}
