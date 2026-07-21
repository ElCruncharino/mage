/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.mage.age.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import dev.mage.age.AppContainer
import dev.mage.age.store.VaultIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun IdentitiesScreen(
    container: AppContainer,
    unlock: suspend () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    var identities by remember { mutableStateOf<List<VaultIdentity>>(emptyList()) }
    var status by remember { mutableStateOf<OpStatus>(OpStatus.Idle) }

    var showGenerate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf<Pair<String, String>?>(null) } // label to private key
    var qrFor by remember { mutableStateOf<VaultIdentity?>(null) }

    suspend fun reload() {
        identities = withContext(Dispatchers.IO) { container.identities.list() }
    }
    LaunchedEffect(Unit) { reload() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showGenerate = true }) { Text("Generate") }
            OutlinedButton(onClick = { showImport = true }) { Text("Import") }
        }

        if (identities.isEmpty()) {
            SectionCard("No identities yet") {
                Text(
                    "Generate a new keypair or import an existing AGE-SECRET-KEY. Your public key " +
                        "(age1…) is what others encrypt to; the private key stays sealed in this device's " +
                        "secure hardware.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        identities.forEach { identity ->
            SectionCard(identity.label) {
                Text(shortKey(identity.recipient), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Created ${DateFormat.getDateInstance().format(Date(identity.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { qrFor = identity }) { Text("Show QR") }

                    OutlinedButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("age recipient", identity.recipient)))
                            status = OpStatus.Success("Public key copied")
                        }
                    }) { Text("Copy public") }

                    OutlinedButton(onClick = {
                        scope.launch {
                            if (!unlock()) {
                                status = OpStatus.Error("Unlock cancelled")
                                return@launch
                            }
                            runCatching { withContext(Dispatchers.IO) { container.identities.open(identity) } }
                                .onSuccess {
                                    reveal = identity.label to
                                        dev.mage.age.crypto.Identities
                                            .encode(it)
                                }.onFailure {
                                    status = OpStatus.Error(vaultInvalidatedMessage(it) ?: "Could not unlock: ${it.message ?: it}")
                                }
                        }
                    }) { Text("Reveal private") }

                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { container.identities.delete(identity.id) }
                            reload()
                            status = OpStatus.Success("Deleted ${identity.label}")
                        }
                    }) { Text("Delete") }
                }
            }
        }

        StatusBanner(status)
    }

    if (showGenerate) {
        LabelDialog(
            title = "Generate identity",
            confirmLabel = "Generate",
            onDismiss = { showGenerate = false },
            onConfirm = { label ->
                showGenerate = false
                scope.launch {
                    if (!unlock()) {
                        status = OpStatus.Error("Unlock cancelled")
                        return@launch
                    }
                    runCatching {
                        withContext(Dispatchers.IO) {
                            container.ensureVaultKey()
                            container.identities.generate(label)
                        }
                    }.onSuccess {
                        reload()
                        status = OpStatus.Success("Identity created")
                    }.onFailure {
                        status = OpStatus.Error(vaultInvalidatedMessage(it) ?: "Failed: ${it.message ?: it}")
                    }
                }
            },
        )
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onConfirm = { label, key ->
                showImport = false
                scope.launch {
                    if (!unlock()) {
                        status = OpStatus.Error("Unlock cancelled")
                        return@launch
                    }
                    runCatching {
                        withContext(Dispatchers.IO) {
                            container.ensureVaultKey()
                            container.identities.import(label, key)
                        }
                    }.onSuccess {
                        reload()
                        status = OpStatus.Success("Identity imported")
                    }.onFailure {
                        status = OpStatus.Error(vaultInvalidatedMessage(it) ?: "Not a valid identity key")
                    }
                }
            },
        )
    }

    reveal?.let { (label, key) ->
        AlertDialog(
            onDismissRequest = { reveal = null },
            title = { Text("Private key — $label") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Anyone with this key can decrypt your files. Never share it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(key, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("age identity", key)))
                        reveal = null
                        status = OpStatus.Success("Private key copied — handle with care")
                    }
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { reveal = null }) { Text("Close") } },
        )
    }

    qrFor?.let { identity ->
        QrDialog(title = identity.label, value = identity.recipient, onDismiss = { qrFor = null })
    }
}

@Composable
private fun LabelDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (e.g. Personal)") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(label) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("AGE-SECRET-KEY-1…") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(label, key) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
