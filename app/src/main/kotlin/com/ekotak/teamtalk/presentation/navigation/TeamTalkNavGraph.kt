package com.ekotak.teamtalk.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ekotak.teamtalk.presentation.auth.LoginScreen
import com.ekotak.teamtalk.presentation.auth.RegisterScreen
import com.ekotak.teamtalk.presentation.calllog.CallLogDetailScreen
import com.ekotak.teamtalk.presentation.calllog.CallLogListScreen
import com.ekotak.teamtalk.presentation.client.ClientDetailScreen
import com.ekotak.teamtalk.presentation.client.ClientFormScreen
import com.ekotak.teamtalk.presentation.client.ClientListScreen
import com.ekotak.teamtalk.presentation.profile.ProfileScreen
import com.ekotak.teamtalk.presentation.voicereport.VoiceReportScreen

@Composable
fun TeamTalkNavGraph(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val sessionState by viewModel.sessionState.collectAsState()

    LaunchedEffect(sessionState) {
        when (sessionState) {
            MainViewModel.SessionState.Loading -> {}
            MainViewModel.SessionState.Unauthenticated -> navController.navigate("auth") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            MainViewModel.SessionState.Authenticated -> navController.navigate("main") {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "splash",
        modifier = Modifier.fillMaxSize(),
    ) {
        composable("splash") {
            SplashScreen()
        }

        navigation(startDestination = "auth/login", route = "auth") {
            composable("auth/login") {
                LoginScreen(
                    onNavigateToRegister = { navController.navigate("auth/register") },
                )
            }
            composable("auth/register") {
                RegisterScreen(
                    onNavigateToLogin = { navController.popBackStack() },
                )
            }
        }

        composable("main") {
            MainScreen()
        }
    }
}

@Composable
private fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarRoutes = setOf("calllogs", "clients", "profile")

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                BottomNavBar(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "calllogs",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Call Logs ──────────────────────────────────────────────────────
            composable("calllogs") {
                CallLogListScreen(
                    onNavigateToDetail = { id -> navController.navigate("calllog/$id") },
                )
            }

            composable(
                route = "calllog/{callLogId}",
                arguments = listOf(navArgument("callLogId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val callLogId = backStackEntry.arguments!!.getString("callLogId")!!
                CallLogDetailScreen(
                    callLogId = callLogId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToVoiceReport = { navController.navigate("voicereport/$callLogId") },
                )
            }

            composable(
                route = "voicereport/{callLogId}",
                arguments = listOf(navArgument("callLogId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val callLogId = backStackEntry.arguments!!.getString("callLogId")!!
                VoiceReportScreen(
                    callLogId = callLogId,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            // ── Clients ────────────────────────────────────────────────────────
            composable("clients") {
                ClientListScreen(
                    onNavigateToDetail = { id -> navController.navigate("client/$id") },
                    onNavigateToCreate = { navController.navigate("client_form") },
                )
            }

            composable(
                route = "client/{clientId}",
                arguments = listOf(navArgument("clientId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val clientId = backStackEntry.arguments!!.getString("clientId")!!
                ClientDetailScreen(
                    clientId = clientId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { navController.navigate("client_form?clientId=$clientId") },
                )
            }

            // Single form route for both create (no arg) and edit (clientId arg)
            composable(
                route = "client_form?clientId={clientId}",
                arguments = listOf(
                    navArgument("clientId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                ClientFormScreen(
                    clientId = backStackEntry.arguments?.getString("clientId"),
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            // ── Profile ────────────────────────────────────────────────────────
            composable("profile") {
                ProfileScreen()
            }
        }
    }
}
