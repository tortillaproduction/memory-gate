package app.memorygate.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.memorygate.ui.apps.GuardedAppsScreen
import app.memorygate.ui.apps.GuardedAppsViewModel
import app.memorygate.ui.common.appViewModel
import app.memorygate.ui.home.HomeScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenGuardedApps = { navController.navigate(Routes.GUARDED_APPS) },
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
