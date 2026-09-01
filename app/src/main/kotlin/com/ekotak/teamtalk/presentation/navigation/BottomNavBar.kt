package com.ekotak.teamtalk.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem("home",     "Pulpit",      Icons.Default.GridView),
    BottomNavItem("calllogs", "Nieodebrane", Icons.Default.PhoneMissed),
    BottomNavItem("history",  "Historia",   Icons.Default.History),
    BottomNavItem("settings", "Ustawienia", Icons.Default.Settings),
)

@Composable
fun BottomNavBar(
    navController: NavController,
    currentRoute: String?,
) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        // Pulpit jest korzeniem grafu — zakładki wracają do niego,
                        // zamiast piętrzyć stos.
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}
