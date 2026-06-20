/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mage.age.AppContainer
import dev.mage.age.io.LaunchTarget

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    ENCRYPT("encrypt", "Encrypt", Icons.Filled.Lock),
    DECRYPT("decrypt", "Decrypt", Icons.Filled.LockOpen),
    KEYS("keys", "Keys", Icons.Filled.VpnKey),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

/** Holds files handed in by a share/open intent so the target screen can consume them once. */
class PendingInput(initial: List<android.net.Uri>) {
    var uris by mutableStateOf(initial)
}

@Composable
fun MageRoot(
    container: AppContainer,
    initialTarget: LaunchTarget,
    unlock: suspend () -> Boolean,
) {
    val navController = rememberNavController()
    val pending = remember { PendingInput(initialTarget.fileUris) }

    val start = when (initialTarget.destination) {
        LaunchTarget.Destination.DECRYPT -> Dest.DECRYPT
        LaunchTarget.Destination.ENCRYPT -> Dest.ENCRYPT
        LaunchTarget.Destination.HOME -> Dest.ENCRYPT
    }

    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination
            NavigationBar {
                Dest.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = start.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Dest.ENCRYPT.route) {
                EncryptScreen(container = container, pending = pending, unlock = unlock)
            }
            composable(Dest.DECRYPT.route) {
                DecryptScreen(container = container, pending = pending, unlock = unlock)
            }
            composable(Dest.KEYS.route) {
                KeysScreen(container = container, unlock = unlock)
            }
            composable(Dest.SETTINGS.route) {
                SettingsScreen(container = container)
            }
        }
    }
}
