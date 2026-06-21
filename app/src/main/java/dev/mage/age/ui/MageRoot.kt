/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.mage.age.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mage.age.AppContainer
import dev.mage.age.io.LaunchTarget
import dev.mage.age.ui.components.FloatingNavBar
import dev.mage.age.ui.components.GradientBackground
import dev.mage.age.ui.components.NavBarItem

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
    var showAbout by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    val start =
        when (initialTarget.destination) {
            LaunchTarget.Destination.DECRYPT -> Dest.DECRYPT
            LaunchTarget.Destination.ENCRYPT -> Dest.ENCRYPT
            LaunchTarget.Destination.HOME -> Dest.ENCRYPT
        }

    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    val currentDest =
        Dest.entries.firstOrNull { dest ->
            current?.hierarchy?.any { it.route == dest.route } == true
        } ?: start

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(currentDest.label) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "About Mage")
                    }
                    DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                        DropdownMenuItem(
                            text = { Text("About Mage") },
                            leadingIcon = {
                                Icon(Icons.Filled.Info, contentDescription = null)
                            },
                            onClick = {
                                showOverflow = false
                                showAbout = true
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            FloatingNavBar(
                items =
                Dest.entries.map { dest ->
                    NavBarItem(
                        label = dest.label,
                        icon = dest.icon,
                        selected = dest == currentDest,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        GradientBackground(modifier = Modifier.fillMaxSize()) {
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
                    SettingsScreen(container = container, unlock = unlock)
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version =
        remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: ""
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mage") },
        text = {
            Text(
                "A modern Android GUI for age file encryption.\n\n" +
                    "Encrypt and decrypt files for the people you trust, or with a passphrase. " +
                    "Your private keys stay sealed in this device's keystore." +
                    if (version.isNotEmpty()) "\n\nVersion $version" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
