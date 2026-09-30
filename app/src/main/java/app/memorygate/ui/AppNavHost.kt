package app.memorygate.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.memorygate.ui.apps.GuardedAppsScreen
import app.memorygate.ui.apps.GuardedAppsViewModel
import app.memorygate.ui.common.appViewModel
import app.memorygate.ui.edit.TargetEditScreen
import app.memorygate.ui.edit.TargetEditViewModel
import app.memorygate.ui.home.HomeScreen
import app.memorygate.ui.home.HomeViewModel

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = appViewModel { container, _ -> HomeViewModel(container) },
                onAddTarget = { navController.navigate(Routes.targetEdit(0)) },
                onEditTarget = { navController.navigate(Routes.targetEdit(it)) },
                onOpenGuardedApps = { navController.navigate(Routes.GUARDED_APPS) },
                onOpenOnboarding = { },
            )
        }
        composable(
            Routes.TARGET_EDIT,
            arguments = listOf(navArgument(TargetEditViewModel.ARG_TARGET_ID) { type = NavType.LongType }),
        ) {
            TargetEditScreen(
                viewModel = appViewModel { container, handle -> TargetEditViewModel(container, handle) },
                onClose = { navController.popBackStack() },
            )
        }
        composable(Routes.GUARDED_APPS) {
            GuardedAppsScreen(
                viewModel = appViewModel { container, _ -> GuardedAppsViewModel(container) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
