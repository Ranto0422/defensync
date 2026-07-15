package com.example.defensync.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.defensync.ui.chat.MessagesViewModel
import com.example.defensync.ui.components.InitialsAvatar
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.defensync.domain.model.UserProfile
import com.example.defensync.ui.defense.CreateDefenseScreen
import com.example.defensync.ui.defense.DefenseDetailScreen
import com.example.defensync.ui.defense.DefenseListScreen
import com.example.defensync.ui.defense.DefenseViewModel
import com.example.defensync.ui.defense.EditGroupScreen
import com.example.defensync.ui.chat.ChatScreen
import com.example.defensync.ui.chat.DirectChatScreen
import com.example.defensync.ui.chat.MessagesScreen
import com.example.defensync.domain.model.Role
import com.example.defensync.ui.calendar.CalendarScreen
import com.example.defensync.ui.schedule.GroupScheduleScreen
import com.example.defensync.ui.schedule.ScheduleScreen

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Defenses : Tab("defenses", "Defenses", Icons.AutoMirrored.Filled.EventNote)
    data object Calendar : Tab("calendar", "Calendar", Icons.Default.CalendarMonth)
    data object Messages : Tab("messages", "Messages", Icons.AutoMirrored.Filled.Chat)
    data object Schedule : Tab("schedule", "My Schedule", Icons.Default.DateRange)
    data object Profile : Tab("profile", "Profile", Icons.Default.Person)
}

private val tabs = listOf(Tab.Defenses, Tab.Calendar, Tab.Messages, Tab.Schedule, Tab.Profile)

/** Slim bar showing the app wordmark, the signed-in account/role, and their initials avatar. */
@Composable
private fun SignedInHeader(profile: UserProfile) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "DefenSync",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Signed in · ${profile.role.label}".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            InitialsAvatar(
                name = profile.name.ifBlank { profile.email },
                size = 40.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MainScaffold(
    profile: UserProfile,
    accounts: List<com.example.defensync.core.network.SavedAccount>,
    switchMessage: String?,
    onSwitchMessageShown: () -> Unit,
    onSwitchAccount: (com.example.defensync.core.network.SavedAccount) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val defenseVm: DefenseViewModel = viewModel()
    val messagesVm: MessagesViewModel = viewModel()
    val messagesUnread by messagesVm.totalUnread.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    // Show the "Switched to …" confirmation without leaving the current screen.
    LaunchedEffect(switchMessage) {
        val msg = switchMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onSwitchMessageShown()
    }

    // An in-place account switch keeps these ViewModels alive (the nav host stays mounted), so their
    // cached data would be the previous account's. Refresh account-scoped tabs when the account changes.
    LaunchedEffect(profile.uid) {
        defenseVm.reload()
        messagesVm.reload()
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        // Insets are handled by the header/nav bar (tabs) or each pushed screen's own TopAppBar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            if (showBottomBar) SignedInHeader(profile)
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val destination = backStack?.destination
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab == Tab.Messages && messagesUnread > 0) {
                                    BadgedBox(badge = { Badge { Text(messagesUnread.toString()) } }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Defenses.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.Defenses.route) {
                DefenseListScreen(
                    profile = profile,
                    vm = defenseVm,
                    onOpen = { id -> navController.navigate("detail/$id") },
                    onCreate = { navController.navigate("create") }
                )
            }
            composable(Tab.Messages.route) {
                MessagesScreen(
                    vm = messagesVm,
                    onOpenGroup = { key, title ->
                        navController.navigate("chat/$key/${Uri.encode(title.ifBlank { "Group chat" })}")
                    },
                    onOpenDm = { key, title -> navController.navigate("dm/$key/${Uri.encode(title)}") }
                )
            }
            composable(Tab.Calendar.route) {
                CalendarScreen(vm = defenseVm)
            }
            composable(Tab.Schedule.route) {
                ScheduleScreen(accountKey = profile.uid)
            }
            composable(Tab.Profile.route) {
                ProfileScreen(
                    profile = profile,
                    accounts = accounts,
                    onSwitch = onSwitchAccount,
                    onAddAccount = onAddAccount,
                    onSignOut = onSignOut
                )
            }
            composable("create") {
                CreateDefenseScreen(
                    student = profile,
                    vm = defenseVm,
                    onBack = { navController.popBackStack() },
                    onCreated = { navController.popBackStack() }
                )
            }
            composable("detail/{id}") { entry ->
                DefenseDetailScreen(
                    defenseId = entry.arguments?.getString("id").orEmpty(),
                    currentUid = profile.uid,
                    vm = defenseVm,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("edit/$id") },
                    onOpenChat = { id, title ->
                        navController.navigate("chat/$id/${Uri.encode(title.ifBlank { "Group chat" })}")
                    },
                    onOpenDm = { uid, name -> navController.navigate("dm/$uid/${Uri.encode(name)}") },
                    onOpenSchedules = { id -> navController.navigate("schedules/$id") }
                )
            }
            composable("schedules/{id}") { entry ->
                GroupScheduleScreen(
                    defenseId = entry.arguments?.getString("id").orEmpty(),
                    currentUid = profile.uid,
                    isStudentViewer = profile.role == Role.STUDENT,
                    defenseVm = defenseVm,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("chat/{id}/{title}") { entry ->
                ChatScreen(
                    defenseId = entry.arguments?.getString("id").orEmpty(),
                    title = entry.arguments?.getString("title").orEmpty(),
                    currentUid = profile.uid,
                    onBack = { navController.popBackStack() },
                    onOpenDm = { uid, name -> navController.navigate("dm/$uid/${Uri.encode(name)}") }
                )
            }
            composable("dm/{uid}/{name}") { entry ->
                DirectChatScreen(
                    otherId = entry.arguments?.getString("uid").orEmpty(),
                    otherName = entry.arguments?.getString("name").orEmpty(),
                    currentUid = profile.uid,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("edit/{id}") { entry ->
                EditGroupScreen(
                    defenseId = entry.arguments?.getString("id").orEmpty(),
                    vm = defenseVm,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
        }
    }
}
