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
import java.net.URLEncoder
import com.ekotak.teamtalk.presentation.auth.LoginScreen
import com.ekotak.teamtalk.presentation.auth.RegisterScreen
import com.ekotak.teamtalk.presentation.calllog.CallLogDetailScreen
import com.ekotak.teamtalk.presentation.calllog.CallLogListScreen
import com.ekotak.teamtalk.presentation.client.ClientDetailScreen
import com.ekotak.teamtalk.presentation.client.ClientFormScreen
import com.ekotak.teamtalk.presentation.client.ClientGroupListScreen
import com.ekotak.teamtalk.presentation.client.ClientTimelineScreen
import com.ekotak.teamtalk.presentation.client.ClientsInGroupScreen
import com.ekotak.teamtalk.presentation.history.HistoryScreen
import com.ekotak.teamtalk.presentation.postcallnote.PostCallNoteScreen
import com.ekotak.teamtalk.presentation.settings.SettingsScreen
import com.ekotak.teamtalk.presentation.voicereport.VoiceReportScreen

@Composable
fun TeamTalkNavGraph(
    viewModel: MainViewModel = hiltViewModel(),
    deepLinkCallLogId: String? = null,
    deepLinkPostCallPhone: String? = null,
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
        composable("splash") { SplashScreen() }

        navigation(startDestination = "auth/login", route = "auth") {
            composable("auth/login") {
                LoginScreen(onNavigateToRegister = { navController.navigate("auth/register") })
            }
            composable("auth/register") {
                RegisterScreen(onNavigateToLogin = { navController.popBackStack() })
            }
        }

        composable("main") {
            MainScreen(
                deepLinkCallLogId = deepLinkCallLogId,
                deepLinkPostCallPhone = deepLinkPostCallPhone,
            )
        }
    }
}

@Composable
private fun MainScreen(deepLinkCallLogId: String? = null, deepLinkPostCallPhone: String? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarRoutes = setOf("calllogs", "history", "clients", "settings")

    LaunchedEffect(deepLinkCallLogId) {
        if (deepLinkCallLogId != null) {
            navController.navigate("calllog/$deepLinkCallLogId") { launchSingleTop = true }
        }
    }

    LaunchedEffect(deepLinkPostCallPhone) {
        if (deepLinkPostCallPhone != null) {
            val encoded = URLEncoder.encode(deepLinkPostCallPhone, "UTF-8")
            navController.navigate("post_call_note?phone=$encoded") { launchSingleTop = true }
        }
    }

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

            // ── History ────────────────────────────────────────────────────────
            composable("history") {
                HistoryScreen(
                    onNavigateToDetail = { id -> navController.navigate("calllog/$id") },
                )
            }

            // ── Clients / Groups ───────────────────────────────────────────────
            composable("clients") {
                ClientGroupListScreen(
                    onNavigateToGroup = { groupId, groupName ->
                        navController.navigate("clients/group/$groupId?groupName=$groupName")
                    },
                )
            }

            composable(
                route = "clients/group/{groupId}?groupName={groupName}",
                arguments = listOf(
                    navArgument("groupId") { type = NavType.StringType },
                    navArgument("groupName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments!!.getString("groupId")!!
                val groupName = backStackEntry.arguments?.getString("groupName") ?: ""
                ClientsInGroupScreen(
                    groupId = groupId,
                    groupName = groupName,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate("client/$id") },
                    onNavigateToNewForm = { phone, name ->
                        var route = "client_form?groupId=$groupId"
                        if (phone != null) route += "&phone=${URLEncoder.encode(phone, "UTF-8")}"
                        if (name != null) route += "&name=${URLEncoder.encode(name, "UTF-8")}"
                        navController.navigate(route)
                    },
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
                    onNavigateToTimeline = { navController.navigate("client_timeline/$clientId") },
                )
            }

            composable(
                route = "client_timeline/{clientId}",
                arguments = listOf(navArgument("clientId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val clientId = backStackEntry.arguments!!.getString("clientId")!!
                ClientTimelineScreen(
                    clientId = clientId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCallDetail = { callLogId -> navController.navigate("calllog/$callLogId") },
                )
            }

            composable(
                route = "client_form?clientId={clientId}&groupId={groupId}&phone={phone}&name={name}",
                arguments = listOf(
                    navArgument("clientId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("groupId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("phone") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("name") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                ClientFormScreen(
                    clientId = backStackEntry.arguments?.getString("clientId"),
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            // ── Settings (includes profile) ────────────────────────────────────
            composable("settings") {
                SettingsScreen()
            }

            // ── Post-Call Note ─────────────────────────────────────────────────
            composable(
                route = "post_call_note?phone={phone}",
                arguments = listOf(
                    navArgument("phone") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                ),
            ) {
                PostCallNoteScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
