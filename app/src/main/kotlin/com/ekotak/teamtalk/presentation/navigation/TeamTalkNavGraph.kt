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
import android.net.Uri
import java.net.URLEncoder
import com.ekotak.teamtalk.presentation.auth.LoginScreen
import com.ekotak.teamtalk.presentation.auth.RegisterScreen
import com.ekotak.teamtalk.presentation.calllog.CallLogDetailScreen
import com.ekotak.teamtalk.presentation.calllog.CallLogListScreen
import com.ekotak.teamtalk.presentation.client.ClientDetailScreen
import com.ekotak.teamtalk.presentation.client.ClientFormScreen
import com.ekotak.teamtalk.presentation.client.ClientListScreen
import com.ekotak.teamtalk.presentation.client.ClientMergeScreen
import com.ekotak.teamtalk.presentation.client.ClientTimelineScreen
import com.ekotak.teamtalk.presentation.crm.DealDetailScreen
import com.ekotak.teamtalk.presentation.crm.DealEditScreen
import com.ekotak.teamtalk.presentation.crm.DealListScreen
import com.ekotak.teamtalk.presentation.crm.KnowledgeArticleScreen
import com.ekotak.teamtalk.presentation.history.HistoryScreen
import com.ekotak.teamtalk.presentation.home.HomeScreen
import com.ekotak.teamtalk.presentation.home.ModulePlaceholderScreen
import com.ekotak.teamtalk.presentation.home.homeModule
import com.ekotak.teamtalk.presentation.postcallnote.PostCallNoteScreen
import com.ekotak.teamtalk.presentation.settings.SettingsScreen
import com.ekotak.teamtalk.presentation.task.CreateTaskScreen
import com.ekotak.teamtalk.presentation.task.CreateTaskViewModel
import com.ekotak.teamtalk.presentation.discussion.DiscussionListScreen
import com.ekotak.teamtalk.presentation.task.TaskDetailScreen
import com.ekotak.teamtalk.presentation.task.TaskListScreen
import com.ekotak.teamtalk.presentation.voicereport.VoiceReportScreen

/** Klucz komunikatu wracającego do kartoteki z ekranów potomnych. */
private const val CLIENT_MESSAGE_KEY = "clientMessage"

/**
 * Id klienta założonego na formularzu, wracające do ekranu wywołującego. Kreator
 * po rozmowie potrzebuje go, żeby wrócić na planszę streszczenia z podpiętym
 * świeżym kontaktem — sam komunikat tekstowy by na to nie wystarczył.
 */
private const val NEW_CLIENT_ID_KEY = "newClientId"

@Composable
fun TeamTalkNavGraph(
    viewModel: MainViewModel = hiltViewModel(),
    deepLinkCallLogId: String? = null,
    deepLinkPostCallPhone: String? = null,
    deepLinkTaskId: String? = null,
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
                deepLinkTaskId = deepLinkTaskId,
            )
        }
    }
}

@Composable
private fun MainScreen(
    deepLinkCallLogId: String? = null,
    deepLinkPostCallPhone: String? = null,
    deepLinkTaskId: String? = null,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarRoutes = setOf("home", "calllogs", "history", "settings")

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

    // Powiadomienie o wywołaniu (@) prowadzi wprost w kartę zadania — tam jest
    // dyskusja, a nie do skrzynki, przez którą trzeba by się przeklikiwać.
    LaunchedEffect(deepLinkTaskId) {
        if (deepLinkTaskId != null) {
            navController.navigate("task/$deepLinkTaskId") { launchSingleTop = true }
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
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Pulpit (ekran startowy) ────────────────────────────────────────
            composable("home") {
                HomeScreen(
                    onOpenModule = { module ->
                        // Moduły mające już ekran mobilny prowadzą wprost do niego;
                        // reszta na zaślepkę z opisem modułu.
                        val route = when (module.key) {
                            "clients" -> "clients"
                            "crm" -> "crm"
                            "tasks" -> "tasks"
                            "communication" -> "discussions"
                            else -> "module/${module.key}"
                        }
                        navController.navigate(route)
                    },
                )
            }

            composable(
                route = "module/{moduleKey}",
                arguments = listOf(navArgument("moduleKey") { type = NavType.StringType }),
            ) { backStackEntry ->
                val module = homeModule(backStackEntry.arguments?.getString("moduleKey"))
                if (module == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    ModulePlaceholderScreen(
                        module = module,
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
            }

            // ── Zadania zespołu (kafelek pulpitu) ──────────────────────────────
            composable("tasks") {
                TaskListScreen(
                    onCreateTask = { navController.navigate("create_task") },
                    onOpenTask = { taskId -> navController.navigate("task/$taskId") },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            // Karta zadania — także cel powiadomienia o wywołaniu (@) i wejścia
            // z Komunikatora: dyskusja to komentarze tego zadania.
            composable(
                route = "task/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
            ) {
                TaskDetailScreen(onNavigateBack = { navController.popBackStack() })
            }

            // ── Komunikator wewnętrzny (kafelek „Komunikacja") ─────────────────
            composable("discussions") {
                DiscussionListScreen(
                    onOpenTask = { taskId -> navController.navigate("task/$taskId") },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            // ── CRM / lejek sprzedaży (kafelek pulpitu) ────────────────────────
            composable("crm") {
                DealListScreen(
                    onNavigateToDeal = { dealId -> navController.navigate("deal/$dealId") },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "deal/{dealId}",
                arguments = listOf(navArgument("dealId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val dealId = backStackEntry.arguments!!.getString("dealId")!!
                DealDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCreateTask = { phone, name ->
                        val ph = Uri.encode(phone)
                        val nm = Uri.encode(name ?: "")
                        navController.navigate("create_task?phone=$ph&name=$nm")
                    },
                    onEdit = { navController.navigate("deal/$dealId/edit") },
                    onOpenArticle = { categoryId, pathLabel ->
                        val cat = Uri.encode(categoryId)
                        val label = Uri.encode(pathLabel)
                        navController.navigate("deal/$dealId/article/$cat?path=$label")
                    },
                )
            }

            composable(
                route = "deal/{dealId}/edit",
                arguments = listOf(navArgument("dealId") { type = NavType.StringType }),
            ) {
                DealEditScreen(onNavigateBack = { navController.popBackStack() })
            }

            // Ścieżka instalacji („Ogrzewanie › Pompa ciepła") idzie parametrem,
            // a nie drugim odczytem katalogu: karta ma ją już policzoną, a ekran
            // artykułu potrzebuje jej wyłącznie jako nagłówka.
            composable(
                route = "deal/{dealId}/article/{categoryId}?path={path}",
                arguments = listOf(
                    navArgument("dealId") { type = NavType.StringType },
                    navArgument("categoryId") { type = NavType.StringType },
                    navArgument("path") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStackEntry ->
                KnowledgeArticleScreen(
                    pathLabel = backStackEntry.arguments?.getString("path").orEmpty()
                        .ifBlank { "Instalacja" },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            // ── Klienci (kartoteka z pulpitu) ──────────────────────────────────
            composable("clients") { entry ->
                // Komunikat z ekranu potomnego (dodano / scalono) wraca przez
                // savedStateHandle — inaczej po powrocie nie byłoby widać, że
                // zapis się udał.
                val message by entry.savedStateHandle
                    .getStateFlow<String?>(CLIENT_MESSAGE_KEY, null)
                    .collectAsState()
                ClientListScreen(
                    onNavigateToDetail = { clientId -> navController.navigate("client/$clientId") },
                    onNavigateToNew = { category ->
                        navController.navigate("client_form?category=${category.wire}")
                    },
                    onNavigateToMerge = { navController.navigate("client_merge") },
                    onNavigateBack = { navController.popBackStack() },
                    message = message,
                    onMessageShown = { entry.savedStateHandle.set<String?>(CLIENT_MESSAGE_KEY, null) },
                )
            }

            composable(
                route = "client/{clientId}",
                arguments = listOf(navArgument("clientId") { type = NavType.StringType }),
            ) {
                ClientDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDeal = { dealId -> navController.navigate("deal/$dealId") },
                    onNavigateToEdit = { clientId ->
                        navController.navigate("client_form?clientId=$clientId")
                    },
                    onNavigateToCallDetail = { callLogId ->
                        navController.navigate("calllog/$callLogId")
                    },
                    onCreateTask = { phone, name ->
                        val ph = Uri.encode(phone)
                        val nm = Uri.encode(name ?: "")
                        navController.navigate("create_task?phone=$ph&name=$nm")
                    },
                )
            }

            composable(
                route = "client_form?clientId={clientId}&category={category}&phone={phone}&name={name}",
                arguments = listOf(
                    navArgument("clientId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("category") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("phone") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("name") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                ),
            ) {
                ClientFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = { message, clientId ->
                        navController.previousBackStackEntry?.savedStateHandle?.apply {
                            set(CLIENT_MESSAGE_KEY, message)
                            if (clientId != null) set(NEW_CLIENT_ID_KEY, clientId)
                        }
                        navController.popBackStack()
                    },
                )
            }

            composable("client_merge") {
                ClientMergeScreen(onNavigateBack = { navController.popBackStack() })
            }

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
                    onNavigateToPostCallNote = { phone ->
                        // Z karty połączenia rozmówca jest już znany — kreator
                        // startuje od razu od streszczenia.
                        val encoded = URLEncoder.encode(phone, "UTF-8")
                        navController.navigate("post_call_note?phone=$encoded&skipContact=1")
                    },
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
                    onNavigateToTimeline = { clientId, phone ->
                        val route = when {
                            clientId != null -> "client_timeline?clientId=$clientId"
                            phone != null -> "client_timeline?phone=${URLEncoder.encode(phone, "UTF-8")}"
                            else -> return@HistoryScreen
                        }
                        navController.navigate(route)
                    },
                )
            }

            composable(
                route = "client_timeline?clientId={clientId}&phone={phone}",
                arguments = listOf(
                    navArgument("clientId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("phone") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                ),
            ) {
                ClientTimelineScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCallDetail = { callLogId -> navController.navigate("calllog/$callLogId") },
                    onCreateTask = { phone, name ->
                        val ph = Uri.encode(phone)
                        val nm = Uri.encode(name ?: "")
                        navController.navigate("create_task?phone=$ph&name=$nm")
                    },
                )
            }

            // ── Settings (includes profile) ────────────────────────────────────
            composable("settings") {
                SettingsScreen()
            }

            // ── Kreator po rozmowie (rozmówca → streszczenie → zadanie) ────────
            composable(
                route = "post_call_note?phone={phone}&skipContact={skipContact}",
                arguments = listOf(
                    navArgument("phone") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("skipContact") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                ),
            ) { entry ->
                // Klient założony na formularzu wraca tu przez savedStateHandle —
                // kreator ma wtedy wznowić od planszy ze streszczeniem.
                val newClientId by entry.savedStateHandle
                    .getStateFlow<String?>(NEW_CLIENT_ID_KEY, null)
                    .collectAsState()
                PostCallNoteScreen(
                    onNavigateBack = { navController.popBackStack() },
                    newClientId = newClientId,
                    onNewClientConsumed = {
                        entry.savedStateHandle.set<String?>(NEW_CLIENT_ID_KEY, null)
                    },
                    onAddContact = { phone, suggestedName ->
                        val ph = Uri.encode(phone)
                        val nm = Uri.encode(suggestedName ?: "")
                        navController.navigate("client_form?phone=$ph&name=$nm")
                    },
                    onCreateTask = { handoff ->
                        val ph = Uri.encode(handoff.phone)
                        val nm = Uri.encode(handoff.name ?: "")
                        val cid = Uri.encode(handoff.clientId ?: "")
                        val note = Uri.encode(handoff.note)
                        // Kreator notatki znika ze stosu: po zapisaniu zadania
                        // nie ma sensu wracać na planszę „czy utworzyć zadanie?".
                        navController.navigate(
                            "create_task?phone=$ph&name=$nm&clientId=$cid&note=$note" +
                                "&mode=${CreateTaskViewModel.MODE_SHORT}",
                        ) {
                            popUpTo(entry.destination.id) { inclusive = true }
                        }
                    },
                )
            }

            // ── Nowe zadanie (pełny kreator albo skrót po rozmowie) ─────────────
            composable(
                route = "create_task?phone={phone}&name={name}&clientId={clientId}" +
                    "&note={note}&mode={mode}",
                arguments = listOf(
                    navArgument("phone") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("name") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("clientId") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("note") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                    navArgument("mode") {
                        type = NavType.StringType; nullable = true; defaultValue = null
                    },
                ),
            ) {
                CreateTaskScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
