/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.mage.age.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.mage.age.AppContainer
import dev.mage.age.crypto.Identities
import dev.mage.age.crypto.Passphrase
import dev.mage.age.io.SafIO
import dev.mage.age.store.SavedRecipient
import dev.mage.age.store.VaultIdentity
import kage.Recipient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EncMode { RECIPIENTS, PASSPHRASE }

@Composable
fun EncryptScreen(container: AppContainer, pending: PendingInput, unlock: suspend () -> Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(EncMode.RECIPIENTS) }
    val chosen = remember { mutableStateListOf<String>() } // canonical age1 recipients
    var recipientInput by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var armor by remember { mutableStateOf(container.settings.defaultArmor) }
    var status by remember { mutableStateOf<OpStatus>(OpStatus.Idle) }

    var savedIdentities by remember { mutableStateOf<List<VaultIdentity>>(emptyList()) }
    var savedRecipients by remember { mutableStateOf<List<SavedRecipient>>(emptyList()) }

    // Input file: from a share intent, or picked here.
    var inputUri by remember { mutableStateOf(pending.uris.firstOrNull()) }
    var inputName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        savedIdentities = withContext(Dispatchers.IO) { container.identities.list() }
        savedRecipients = withContext(Dispatchers.IO) { container.recipients.list() }
    }
    LaunchedEffect(inputUri) {
        inputName = inputUri?.let { withContext(Dispatchers.IO) { SafIO.displayName(context, it) } }
    }

    // Build the kage recipient list from the current selection.
    fun buildRecipients(): Result<List<Recipient>> = runCatching {
        when (mode) {
            EncMode.PASSPHRASE -> {
                require(passphrase.isNotEmpty()) { "Enter a passphrase" }
                require(passphrase == confirm) { "Passphrases do not match" }
                listOf(Passphrase.recipient(passphrase.toCharArray()))
            }
            EncMode.RECIPIENTS -> {
                require(chosen.isNotEmpty()) { "Add at least one recipient" }
                chosen.map { Identities.parseRecipient(it) }
            }
        }
    }

    val pickInput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) inputUri = uri
    }

    val saveOutput = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { output ->
        val src = inputUri
        if (output == null || src == null) return@rememberLauncherForActivityResult
        val recipients = buildRecipients().getOrElse {
            status = OpStatus.Error(it.message ?: "Invalid recipients"); return@rememberLauncherForActivityResult
        }
        status = OpStatus.Working("Encrypting…")
        scope.launch {
            status = runCatching {
                CryptoRunner.encryptToUri(context, recipients, src, output, armor)
            }.fold(
                onSuccess = { OpStatus.Success("Encrypted to ${SafIO.displayName(context, output) ?: "file"}") },
                onFailure = { OpStatus.Error("Encryption failed: ${it.message ?: it}") },
            )
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Encrypt", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)

        // Mode selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == EncMode.RECIPIENTS,
                onClick = { mode = EncMode.RECIPIENTS },
                label = { Text("Recipients") },
            )
            FilterChip(
                selected = mode == EncMode.PASSPHRASE,
                onClick = { mode = EncMode.PASSPHRASE },
                label = { Text("Passphrase") },
            )
        }

        if (mode == EncMode.RECIPIENTS) {
            SectionCard("Recipients") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = recipientInput,
                        onValueChange = { recipientInput = it },
                        label = { Text("age1… public key") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val text = recipientInput.trim()
                            val parsed = runCatching { Identities.parseRecipient(text) }
                            if (parsed.isSuccess) {
                                val canonical = Identities.encode(parsed.getOrThrow())
                                if (canonical !in chosen) chosen.add(canonical)
                                recipientInput = ""
                                status = OpStatus.Idle
                            } else {
                                status = OpStatus.Error("Not a valid age recipient")
                            }
                        },
                    ) { Text("Add") }
                }

                if (chosen.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chosen.forEach { key ->
                            InputChip(
                                selected = true,
                                onClick = { chosen.remove(key) },
                                label = { Text(shortKey(key)) },
                                trailingIcon = { Text("✕") },
                            )
                        }
                    }
                }

                val addable = savedIdentities.map { it.label to it.recipient } +
                    savedRecipients.map { it.label to it.recipient }
                if (addable.isNotEmpty()) {
                    Text("Add a saved key:", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        addable.forEach { (label, key) ->
                            OutlinedButton(onClick = { if (key !in chosen) chosen.add(key) }) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        } else {
            SectionCard("Passphrase") {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
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
            }
        }

        SectionCard("Options") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = armor, onCheckedChange = { armor = it })
                Column {
                    Text("ASCII armor")
                    Text(
                        "Text-safe output you can paste anywhere",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        SectionCard("File") {
            Text(inputName ?: "No file selected", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickInput.launch(arrayOf("*/*")) }) { Text("Select file") }
                Button(
                    enabled = inputUri != null,
                    onClick = {
                        val r = buildRecipients()
                        if (r.isFailure) {
                            status = OpStatus.Error(r.exceptionOrNull()?.message ?: "Invalid recipients")
                        } else {
                            saveOutput.launch(SafIO.suggestEncryptedName(inputName))
                        }
                    },
                ) { Text("Encrypt & save") }
            }
        }

        StatusBanner(status)
    }
}

private fun shortKey(key: String): String =
    if (key.length <= 16) key else key.take(10) + "…" + key.takeLast(4)
