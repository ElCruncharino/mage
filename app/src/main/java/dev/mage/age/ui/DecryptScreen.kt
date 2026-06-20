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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.mage.age.AppContainer
import dev.mage.age.crypto.Passphrase
import dev.mage.age.io.SafIO
import kage.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DecMode { IDENTITY, PASSPHRASE }

@Composable
fun DecryptScreen(container: AppContainer, pending: PendingInput, unlock: suspend () -> Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(DecMode.IDENTITY) }
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<OpStatus>(OpStatus.Idle) }
    var inputUri by remember { mutableStateOf(pending.uris.firstOrNull()) }
    var inputName by remember { mutableStateOf<String?>(null) }
    var identityCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        identityCount = withContext(Dispatchers.IO) { container.identities.list().size }
    }
    LaunchedEffect(inputUri) {
        inputName = inputUri?.let { withContext(Dispatchers.IO) { SafIO.displayName(context, it) } }
    }

    val pickInput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) inputUri = uri
    }

    val saveOutput = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { output ->
        val src = inputUri
        if (output == null || src == null) return@rememberLauncherForActivityResult
        status = OpStatus.Working("Decrypting…")
        scope.launch {
            status = runCatching {
                val identities: List<Identity> = withContext(Dispatchers.IO) {
                    when (mode) {
                        DecMode.PASSPHRASE -> listOf(Passphrase.identity(passphrase.toCharArray()))
                        DecMode.IDENTITY -> container.identities.list().map { container.identities.open(it) }
                    }
                }
                CryptoRunner.decryptToUri(context, identities, src, output)
                output
            }.fold(
                onSuccess = { OpStatus.Success("Decrypted to ${SafIO.displayName(context, it) ?: "file"}") },
                onFailure = { OpStatus.Error(decryptError(it)) },
            )
        }
    }

    fun startDecrypt() {
        val src = inputUri
        if (src == null) {
            status = OpStatus.Error("Select a .age file first"); return
        }
        when (mode) {
            DecMode.PASSPHRASE -> {
                if (passphrase.isEmpty()) {
                    status = OpStatus.Error("Enter the passphrase"); return
                }
                saveOutput.launch(SafIO.suggestDecryptedName(inputName))
            }
            DecMode.IDENTITY -> {
                if (identityCount == 0) {
                    status = OpStatus.Error("No identities yet — add one under Keys"); return
                }
                scope.launch {
                    if (unlock()) {
                        saveOutput.launch(SafIO.suggestDecryptedName(inputName))
                    } else {
                        status = OpStatus.Error("Unlock cancelled")
                    }
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Decrypt", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == DecMode.IDENTITY,
                onClick = { mode = DecMode.IDENTITY },
                label = { Text("My identities") },
            )
            FilterChip(
                selected = mode == DecMode.PASSPHRASE,
                onClick = { mode = DecMode.PASSPHRASE },
                label = { Text("Passphrase") },
            )
        }

        if (mode == DecMode.PASSPHRASE) {
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
            }
        } else {
            SectionCard("Identities") {
                Text(
                    if (identityCount == 0) {
                        "No identities saved yet. Add one under the Keys tab."
                    } else {
                        "Mage will try your $identityCount saved " +
                            if (identityCount == 1) "identity." else "identities."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SectionCard("File") {
            Text(inputName ?: "No file selected", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickInput.launch(arrayOf("*/*")) }) { Text("Select .age file") }
                Button(enabled = inputUri != null, onClick = { startDecrypt() }) { Text("Decrypt & save") }
            }
        }

        StatusBanner(status)
    }
}

private fun decryptError(t: Throwable): String {
    val name = t::class.simpleName ?: "Error"
    return when {
        name.contains("UserNotAuthenticated") -> "Vault locked — unlock and try again"
        name.contains("NoIdentities") || name.contains("IncorrectHMAC") || name.contains("Identity") ->
            "None of your keys (or this passphrase) can open this file"
        else -> "Decryption failed: ${t.message ?: name}"
    }
}
