/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.mage.age.AppContainer
import dev.mage.age.store.BiometricGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var biometricLock by remember { mutableStateOf(container.settings.biometricLock) }
    var defaultArmor by remember { mutableStateOf(container.settings.defaultArmor) }
    var identityCount by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<OpStatus>(OpStatus.Idle) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        identityCount = withContext(Dispatchers.IO) { container.identities.list().size }
    }

    val biometricAvailable = BiometricGate.canAuthenticate(context)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        SectionCard("Security") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(
                    checked = biometricLock,
                    enabled = identityCount == 0 && biometricAvailable,
                    onCheckedChange = { value ->
                        biometricLock = value
                        container.settings.biometricLock = value
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                container.vault.deleteKey()
                                container.vault.ensureKey(value)
                            }
                        }
                    },
                )
                Column {
                    Text("Biometric lock")
                    Text(
                        when {
                            !biometricAvailable -> "No biometric or device PIN enrolled on this device"
                            identityCount > 0 -> "Locked in while identities exist — reset the vault to change"
                            else -> "Require fingerprint, face, or PIN to use your identities"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        SectionCard("Defaults") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(
                    checked = defaultArmor,
                    onCheckedChange = {
                        defaultArmor = it
                        container.settings.defaultArmor = it
                    },
                )
                Column {
                    Text("ASCII armor by default")
                    Text("Start new encryptions with text-safe output on", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SectionCard("Vault") {
            Text("$identityCount stored ${if (identityCount == 1) "identity" else "identities"}")
            TextButton(onClick = { confirmReset = true }) { Text("Reset vault (delete all identities)") }
        }

        SectionCard("About") {
            Text("Mage — Mobile age", style = MaterialTheme.typography.titleMedium)
            Text(versionName(context), style = MaterialTheme.typography.bodySmall)
            Text(
                "A GUI for the age encryption format, built on the kage library " +
                    "(android-password-store/kage). age, in your pocket.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        StatusBanner(status)
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset vault?") },
            text = {
                Text(
                    "This permanently deletes all stored identities and the device key that protects " +
                        "them. Files already encrypted to those identities can no longer be decrypted " +
                        "unless you have a backup of the private keys. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            container.identities.list().forEach { container.identities.delete(it.id) }
                            container.vault.deleteKey()
                            container.vault.ensureKey(container.settings.biometricLock)
                        }
                        identityCount = 0
                        status = OpStatus.Success("Vault reset")
                    }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

private fun versionName(context: android.content.Context): String =
    runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "Version ${info.versionName}"
    }.getOrDefault("Version 0.1.0")
