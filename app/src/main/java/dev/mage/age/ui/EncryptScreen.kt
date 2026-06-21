/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.mage.age.ui

import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.mage.age.AppContainer
import dev.mage.age.crypto.Identities
import dev.mage.age.crypto.Passphrase
import dev.mage.age.io.SafIO
import dev.mage.age.store.SavedRecipient
import dev.mage.age.store.VaultIdentity
import dev.mage.age.ui.components.FilePickCard
import dev.mage.age.ui.components.PrimaryActionButton
import dev.mage.age.ui.components.SecurePasswordField
import dev.mage.age.ui.components.SegmentedButtonGroup
import dev.mage.age.ui.components.readPasswordChars
import kage.Recipient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays

private enum class EncMode { RECIPIENTS, PASSPHRASE }

@Composable
fun EncryptScreen(
    container: AppContainer,
    pending: PendingInput,
    unlock: suspend () -> Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(EncMode.RECIPIENTS) }
    val chosen = remember { mutableStateListOf<String>() } // canonical age1 recipients
    var recipientInput by remember { mutableStateOf("") }
    var pwField by remember { mutableStateOf<EditText?>(null) }
    var confirmField by remember { mutableStateOf<EditText?>(null) }
    var showPw by remember { mutableStateOf(false) }
    var armor by remember { mutableStateOf(container.settings.defaultArmor) }
    var status by remember { mutableStateOf<OpStatus>(OpStatus.Idle) }

    var savedIdentities by remember { mutableStateOf<List<VaultIdentity>>(emptyList()) }
    var savedRecipients by remember { mutableStateOf<List<SavedRecipient>>(emptyList()) }

    // Input file(s): from a share intent, or picked here.
    var inputUris by remember { mutableStateOf(pending.uris) }
    var inputLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        savedIdentities = withContext(Dispatchers.IO) { container.identities.list() }
        savedRecipients = withContext(Dispatchers.IO) { container.recipients.list() }
    }
    LaunchedEffect(inputUris) {
        inputLabel =
            when {
                inputUris.isEmpty() -> null
                inputUris.size == 1 -> withContext(Dispatchers.IO) { SafIO.displayName(context, inputUris.first()) }
                else -> "${inputUris.size} files"
            }
    }

    // Build the kage recipient list from the current selection. For a passphrase, the chars are read
    // straight off the native field and wiped immediately after kage has copied them to its own bytes.
    fun buildRecipients(): Result<List<Recipient>> =
        runCatching {
            when (mode) {
                EncMode.PASSPHRASE -> {
                    val pw = readPasswordChars(pwField) ?: throw IllegalArgumentException("Enter a passphrase")
                    val confirm = readPasswordChars(confirmField)
                    try {
                        require(confirm != null && pw.contentEquals(confirm)) { "Passphrases do not match" }
                        listOf(Passphrase.recipient(pw))
                    } finally {
                        Arrays.fill(pw, ' ')
                        if (confirm != null) Arrays.fill(confirm, ' ')
                    }
                }
                EncMode.RECIPIENTS -> {
                    require(chosen.isNotEmpty()) { "Add at least one recipient" }
                    chosen.map { Identities.parseRecipient(it) }
                }
            }
        }

    val pickInput =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) inputUris = uris
        }

    val saveOutput =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { output ->
            val src = inputUris.firstOrNull()
            if (output == null || src == null) return@rememberLauncherForActivityResult
            val recipients =
                buildRecipients().getOrElse {
                    status = OpStatus.Error(it.message ?: "Invalid recipients")
                    return@rememberLauncherForActivityResult
                }
            status = OpStatus.Working("Encrypting…")
            scope.launch {
                status =
                    runCatching {
                        CryptoRunner.encryptToUri(context, recipients, src, output, armor)
                    }.fold(
                        onSuccess = { OpStatus.Success("Encrypted to ${SafIO.displayName(context, output) ?: "file"}") },
                        onFailure = { OpStatus.Error("Encryption failed: ${it.message ?: it}") },
                    )
            }
        }

    // Batch: encrypt every selected file into a chosen folder.
    val pickFolder =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { tree ->
            if (tree == null) return@rememberLauncherForActivityResult
            val recipients =
                buildRecipients().getOrElse {
                    status = OpStatus.Error(it.message ?: "Invalid recipients")
                    return@rememberLauncherForActivityResult
                }
            status = OpStatus.Working("Encrypting…")
            scope.launch {
                val result =
                    CryptoRunner.encryptBatch(context, recipients, inputUris, tree, armor) { i, n ->
                        status = OpStatus.Working("Encrypting $i of $n…")
                    }
                status =
                    if (result.failed.isEmpty()) {
                        OpStatus.Success("Encrypted ${result.ok} files")
                    } else {
                        OpStatus.Error("Encrypted ${result.ok}/${result.total}; failed: ${result.failed.joinToString()}")
                    }
            }
        }

    fun startEncrypt() {
        if (inputUris.isEmpty()) {
            status = OpStatus.Error("Choose a file to encrypt first")
            return
        }
        val r = buildRecipients()
        if (r.isFailure) {
            status = OpStatus.Error(r.exceptionOrNull()?.message ?: "Invalid recipients")
            return
        }
        if (inputUris.size > 1) pickFolder.launch(null) else saveOutput.launch(SafIO.suggestEncryptedName(inputLabel))
    }

    val busy = status is OpStatus.Working

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Lock a file so only the people you choose — or anyone with the passphrase — can open it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SegmentedButtonGroup(
            options = listOf("Recipients", "Passphrase"),
            selectedIndex = if (mode == EncMode.RECIPIENTS) 0 else 1,
            onSelect = { mode = if (it == 0) EncMode.RECIPIENTS else EncMode.PASSPHRASE },
        )

        if (mode == EncMode.RECIPIENTS) {
            SectionCard("Recipients") {
                Text(
                    "Add the public keys (age1…) of who should be able to open this file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        modifier = Modifier.heightIn(min = 48.dp),
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
                                modifier =
                                Modifier.semantics {
                                    contentDescription = "Recipient ${shortKey(key)}, tap to remove"
                                },
                            )
                        }
                    }
                }

                val addable =
                    savedIdentities.map { it.label to it.recipient } +
                        savedRecipients.map { it.label to it.recipient }
                if (addable.isNotEmpty()) {
                    Text("Add a saved key:", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        addable.forEach { (label, key) ->
                            OutlinedButton(
                                onClick = { if (key !in chosen) chosen.add(key) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(label) }
                        }
                    }
                } else {
                    Text(
                        "No saved keys yet — add recipients under the Keys tab to pick them here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            SecurePasswordField(
                label = "Passphrase",
                show = showPw,
                onToggleShow = { showPw = !showPw },
                onViewCreated = { pwField = it },
            )
            SecurePasswordField(
                label = "Confirm passphrase",
                show = showPw,
                onToggleShow = { showPw = !showPw },
                onViewCreated = { confirmField = it },
            )
            Text(
                "Anyone with this passphrase can open the file. Choose something strong and share it safely.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Options") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Switch(
                    checked = armor,
                    onCheckedChange = { armor = it },
                    modifier =
                    Modifier.semantics {
                        stateDescription = if (armor) "ASCII armor on" else "ASCII armor off"
                    },
                )
                Column {
                    Text("ASCII armor")
                    Text(
                        "Text-safe output you can paste anywhere",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        FilePickCard(
            label = "File to encrypt",
            chosen = inputLabel,
            hint = "No file selected",
            onPick = { pickInput.launch(arrayOf("*/*")) },
        )

        PrimaryActionButton(
            label = if (inputUris.size > 1) "Encrypt ${inputUris.size} → folder" else "Encrypt & save",
            busy = busy,
            enabled = inputUris.isNotEmpty(),
            onClick = { startEncrypt() },
        )

        StatusBanner(status)
    }
}
