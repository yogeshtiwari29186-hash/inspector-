package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.navigation.Routes
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LiveTrafficScreen
import com.example.ui.screens.OverlaySetupScreen
import com.example.ui.screens.RequestDetailsScreen
import com.example.ui.screens.RequestEditorScreen
import com.example.ui.screens.ResponseDetailsScreen
import com.example.ui.screens.ResponseEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DevTrafficTheme
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.TrafficViewModel

class MainActivity : ComponentActivity() {

    private var targetRequestId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        targetRequestId = intent.getStringExtra("TARGET_REQUEST_ID")

        setContent {
            DevTrafficTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF020617)
                ) {
                    val navController = rememberNavController()
                    val trafficViewModel: TrafficViewModel = viewModel()
                    val settingsViewModel: SettingsViewModel = viewModel()

                    // Request notification permission on Android 13+
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }

                    // Navigate to target request if opened from overlay
                    LaunchedEffect(targetRequestId) {
                        targetRequestId?.let { reqId ->
                            navController.navigate(Routes.requestDetails(reqId))
                            targetRequestId = null
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Routes.DASHBOARD
                    ) {
                        composable(Routes.DASHBOARD) {
                            DashboardScreen(
                                trafficViewModel = trafficViewModel,
                                settingsViewModel = settingsViewModel,
                                onNavigate = { route -> navController.navigate(route) }
                            )
                        }

                        composable(Routes.LIVE) {
                            LiveTrafficScreen(
                                viewModel = trafficViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToDetails = { id -> navController.navigate(Routes.requestDetails(id)) }
                            )
                        }

                        composable(
                            route = Routes.REQUEST_DETAILS,
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: ""
                            RequestDetailsScreen(
                                requestId = id,
                                viewModel = trafficViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToEditor = { reqId -> navController.navigate(Routes.requestEditor(reqId)) },
                                onNavigateToResponseDetails = { reqId -> navController.navigate(Routes.responseDetails(reqId)) }
                            )
                        }

                        composable(
                            route = Routes.REQUEST_EDITOR,
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: ""
                            RequestEditorScreen(
                                requestId = id,
                                viewModel = trafficViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Routes.RESPONSE_DETAILS,
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: ""
                            ResponseDetailsScreen(
                                requestId = id,
                                viewModel = trafficViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToEditor = { reqId -> navController.navigate(Routes.responseEditor(reqId)) }
                            )
                        }

                        composable(
                            route = Routes.RESPONSE_EDITOR,
                            arguments = listOf(navArgument("id") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: ""
                            ResponseEditorScreen(
                                requestId = id,
                                viewModel = trafficViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.HISTORY) {
                            HistoryScreen(
                                viewModel = trafficViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToDetails = { id -> navController.navigate(Routes.requestDetails(id)) }
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                settingsViewModel = settingsViewModel,
                                trafficViewModel = trafficViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToOverlaySetup = { navController.navigate(Routes.OVERLAY_SETUP) }
                            )
                        }

                        composable(Routes.OVERLAY_SETUP) {
                            OverlaySetupScreen(
                                settingsViewModel = settingsViewModel,
                                trafficViewModel = trafficViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
                            )
                        }

                        composable(Routes.ABOUT) {
                            AboutScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetRequestId = intent.getStringExtra("TARGET_REQUEST_ID")
    }
}
