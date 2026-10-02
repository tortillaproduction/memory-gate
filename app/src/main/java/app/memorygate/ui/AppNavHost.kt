package app.memorygate.ui

import androidx.activity.compose.LocalActivity
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
import app.memorygate.ui.home.AccessibilityBanner
import app.memorygate.ui.home.HomeScreen
import app.memorygate.ui.home.HomeViewModel
import app.memorygate.ui.onboarding.OnboardingScreen
import app.memorygate.ui.onboarding.OnboardingStep
import app.memorygate.ui.onboarding.OnboardingViewModel

@Composable
fun AppNavHost(startWithOnboarding: Boolean) {
    val navController = rememberNavController()
    val activity = LocalActivity.current
    NavHost(
        navController = navController,
        startDestination = if (startWithOnboarding) Routes.ONBOARDING else Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = appViewModel { container, _ -> HomeViewModel(container) },
                onAddTarget = { navController.navigate(Routes.targetEdit(0)) },
                onEditTarget = { navController.navigate(Routes.targetEdit(it)) },
                onOpenGuardedApps = { navController.navigate(Routes.GUARDED_APPS) },
                onOpenOnboarding = { navController.navigate(Routes.onboarding()) },
                banner = {
                    AccessibilityBanner(
                        onClick = { navController.navigate(Routes.onboarding(OnboardingStep.ACCESSIBILITY)) },
                    )
                },
            )
        }
        composable(
            Routes.ONBOARDING,
            arguments = listOf(
                navArgument(OnboardingViewModel.ARG_STEP) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) {
            OnboardingScreen(
                viewModel = appViewModel { container, handle -> OnboardingViewModel(container, handle) },
                guardedAppsViewModel = appViewModel { container, _ -> GuardedAppsViewModel(container) },
                onAddTarget = { navController.navigate(Routes.targetEdit(0)) },
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onExitToHome = {
                    // メニューから開いた場合はホームが下にある。念のため、なければホームへ移動する
                    if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                },
                onExitApp = { activity?.finish() },
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
