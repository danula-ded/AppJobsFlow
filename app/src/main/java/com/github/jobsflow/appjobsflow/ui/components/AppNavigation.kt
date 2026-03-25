package com.github.jobsflow.appjobsflow.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.jobsflow.appjobsflow.api.ApiClient

@Composable
fun AppNavigation(client: ApiClient, sharedPrefs: android.content.SharedPreferences) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    // Начальный экран зависит от авторизации
    val startDestination = if (client.isAuthenticated) "home" else "auth"
    NavHost(navController = navController, startDestination = startDestination) {
        composable("auth") {
            AuthScreen(client, sharedPrefs, snackbarHostState, onAuthorized = {
                navController.navigate("home") {
                    popUpTo("auth") { inclusive = true }
                }
            })
        }
        composable("home") {
            HomeScreen(client, sharedPrefs, navController, snackbarHostState)
        }
        composable("advancedOptions") {
            AdvancedOptionsScreen(client, sharedPrefs, navController, snackbarHostState)
        }
        composable("appliedVacancies") {
            AppliedVacanciesScreen(client, sharedPrefs, navController, snackbarHostState)
        }
        composable(
            route = "templateEditor/{templateId}",
            arguments = listOf(navArgument("templateId") { type = NavType.StringType })
        ) { backStackEntry ->
            TemplateEditorScreen(
                client = client,
                sharedPrefs = sharedPrefs,
                navController = navController,
                snackbarHostState = snackbarHostState,
                templateId = backStackEntry.arguments?.getString("templateId")
            )
        }
    }
}
