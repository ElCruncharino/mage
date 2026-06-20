/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import dev.mage.age.backup.BackupManager
import dev.mage.age.store.BiometricGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(container: AppContainer, unlock: suspend () -> Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var biometricLock by remember { mutableStateOf(container.settings.biometricLock) }
    var defaultArmor by remember { mutableStateOf(container.settings.defaultArmor) }
    var identityCount by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<OpStatus>(OpStatus.Idle) }
    var confirmReset by remember { mutableStateOf(false) }

    // Backup / restore state.
    var showExportPass by remember { mutableStateOf(false) }
    var showRestorePass by remember { mutableStateOf(false) }
    var exportPass by remember { mutableStateOf("") }
    var exportArmor by remember { mutableStateOf(true) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    suspend fun refreshCount() {
        identityCount = withContext(Dispatchers.IO) { container.identities.list().size }
    }
    LaunchedEffect(Unit) { refreshCount() }

    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { output ->
        if (output == null) return@rememberLauncherForActivityResult
        val pass = exportPass.toCharArray()
        exportPass = ""
        status = OpStatus.Working("Backing up…")
        scope.launch {
            status = runCatching {
                BackupManager.export(context, container, pass, exportArmor, output)
            }.fold(
                onSuccess = { OpStatus.Success("Backed up $identityCount identities") },
                onFailure = {
                    val name = it::class.simpleName ?: "Error"
                    if (name.contains("UserNotAuthenticated")) {
                        OpStatus.Error("Vault lock expired — try again")
                    } else {
                        OpStatus.Error("Backup failed: ${it.message ?: name}")
                    }
                },
            )
        }
    }

    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreUri = uri
            showRestorePass = true
        }
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
                                container.ensureVaultKey()
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

        SectionCard("Backup & restore") {
            Text(
                "A backup is your identities age-encrypted to a passphrase — a normal .age file any " +
                    "age tool can open. Keep the passphrase safe: with it and the file, anyone has your " +
                    "private keys.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = identityCount > 0,
                    onClick = { showExportPass = true },
                ) { Text("Export identities") }
                OutlinedButton(onClick = { openBackup.launch(arrayOf("*/*")) }) { Text("Restore") }
            }
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
                            container.ensureVaultKey()
                        }
                        identityCount = 0
                        status = OpStatus.Success("Vault reset")
                    }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }

    if (showExportPass) {
        ExportPassphraseDialog(
            onDismiss = { showExportPass = false },
            onConfirm = { pass, armor ->
                showExportPass = false
                exportPass = pass
                exportArmor = armor
                scope.launch {
                    if (unlock()) {
                        saveBackup.launch("mage-identities-backup.age")
                    } else {
                        status = OpStatus.Error("Unlock cancelled")
                    }
                }
            },
        )
    }

    if (showRestorePass) {
        RestorePassphraseDialog(
            onDismiss = { showRestorePass = false },
            onConfirm = { pass ->
                showRestorePass = false
                val uri = restoreUri
                if (uri == null) {
                    status = OpStatus.Error("No backup file selected")
                } else {
                    scope.launch {
                        if (!unlock()) {
                            status = OpStatus.Error("Unlock cancelled"); return@launch
                        }
                        status = OpStatus.Working("Restoring…")
                        val result = runCatching {
                            BackupManager.import(context, container, pass.toCharArray(), uri)
                        }
                        status = result.fold(
                            onSuccess = {
                                OpStatus.Success("Imported ${it.imported}, skipped ${it.skipped}")
                            },
                            onFailure = { OpStatus.Error("Wrong passphrase or invalid backup") },
                        )
                        refreshCount()
                    }
                }
            },
        )
    }
}

@Composable
private fun ExportPassphraseDialog(onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var armor by remember { mutableStateOf(true) }
    val valid = pass.isNotEmpty() && pass == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup passphrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = armor, onCheckedChange = { armor = it })
                    Text("Text-safe (armored) file", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(pass, armor) }) { Text("Export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RestorePassphraseDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup") },
        text = {
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Backup passphrase") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = pass.isNotEmpty(), onClick = { onConfirm(pass) }) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun versionName(context: android.content.Context): String =
    runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "Version ${info.versionName}"
    }.getOrDefault("Version 0.1.0")
