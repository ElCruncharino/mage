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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.mage.age.AppContainer
import dev.mage.age.crypto.Identities
import dev.mage.age.qr.QrScanner
import dev.mage.age.store.SavedRecipient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecipientsScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var recipients by remember { mutableStateOf<List<SavedRecipient>>(emptyList()) }
    var status by remember { mutableStateOf<OpStatus>(OpStatus.Idle) }
    var scanning by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var prefillKey by remember { mutableStateOf("") }
    var qrFor by remember { mutableStateOf<SavedRecipient?>(null) }

    suspend fun reload() {
        recipients = withContext(Dispatchers.IO) { container.recipients.list() }
    }
    LaunchedEffect(Unit) { reload() }

    // Camera scanner takes over the whole content area while active.
    if (scanning) {
        QrScanner(
            onResult = { text ->
                scanning = false
                val trimmed = text.trim()
                if (Identities.looksLikeRecipient(trimmed)) {
                    prefillKey = trimmed
                    showAdd = true
                } else {
                    status = OpStatus.Error("That QR isn't an age recipient")
                }
            },
            onClose = { scanning = false },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { prefillKey = ""; showAdd = true }) { Text("Add") }
            OutlinedButton(onClick = { status = OpStatus.Idle; scanning = true }) { Text("Scan QR") }
        }

        if (recipients.isEmpty()) {
            SectionCard("No recipients yet") {
                Text(
                    "Save the public keys (age1…) of people you encrypt to. Add one by pasting a key " +
                        "or scanning their QR code — then pick them on the Encrypt screen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        recipients.forEach { recipient ->
            SectionCard(recipient.label) {
                Text(shortKey(recipient.recipient), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { qrFor = recipient }) { Text("Show QR") }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(recipient.recipient))
                        status = OpStatus.Success("Copied")
                    }) { Text("Copy") }
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { container.recipients.delete(recipient.id) }
                            reload()
                            status = OpStatus.Success("Removed ${recipient.label}")
                        }
                    }) { Text("Delete") }
                }
            }
        }

        StatusBanner(status)
    }

    if (showAdd) {
        AddRecipientDialog(
            initialKey = prefillKey,
            onDismiss = { showAdd = false },
            onConfirm = { label, key ->
                showAdd = false
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { container.recipients.add(label, key) } }
                        .onSuccess { reload(); status = OpStatus.Success("Recipient saved") }
                        .onFailure { status = OpStatus.Error("Not a valid age recipient") }
                }
            },
        )
    }

    qrFor?.let { recipient ->
        QrDialog(title = recipient.label, value = recipient.recipient, onDismiss = { qrFor = null })
    }
}

@Composable
private fun AddRecipientDialog(
    initialKey: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(initialKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add recipient") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g. Alice)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("age1… public key") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(label, key) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
