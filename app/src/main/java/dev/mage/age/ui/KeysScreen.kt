/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.mage.age.AppContainer

/** The "Keys" destination: two tabs for your own identities and your recipient address book. */
@Composable
fun KeysScreen(
    container: AppContainer,
    unlock: suspend () -> Boolean,
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        // Transparent track so the gradient backdrop shows through the tab bar.
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Identities") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Recipients") })
        }
        when (tab) {
            0 -> IdentitiesScreen(container = container, unlock = unlock)
            else -> RecipientsScreen(container = container)
        }
    }
}
