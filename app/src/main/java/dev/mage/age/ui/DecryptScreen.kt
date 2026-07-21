/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui

import android.provider.DocumentsContract
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.mage.age.AppContainer
import dev.mage.age.crypto.Passphrase
import dev.mage.age.io.SafIO
import dev.mage.age.ui.components.FilePickCard
import dev.mage.age.ui.components.PrimaryActionButton
import dev.mage.age.ui.components.SecurePasswordField
import dev.mage.age.ui.components.SegmentedButtonGroup
import dev.mage.age.ui.components.readPasswordChars
import kage.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Arrays

private enum class DecMode { IDENTITY, PASSPHRASE }

@Composable
fun DecryptScreen(
    container: AppContainer,
    pending: PendingInput,
    unlock: suspend () -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(DecMode.IDENTITY) }
    var pwField by remember { mutableStateOf<EditText?>(null) }
    var showPw by remember { mutableStateOf(false) }
    // Holds the validated plaintext (decrypted into private cache) between a successful single-file
    // decrypt and the user picking where to save it. Null when there's nothing pending.
    var pendingPlain by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf<OpStatus>(OpStatus.Idle) }
    var inputUris by remember { mutableStateOf(pending.uris) }
    var inputLabel by remember { mutableStateOf<String?>(null) }
    var identityCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        identityCount = withContext(Dispatchers.IO) { container.identities.list().size }
    }
    LaunchedEffect(inputUris) {
        inputLabel =
            when {
                inputUris.isEmpty() -> null
                inputUris.size == 1 -> withContext(Dispatchers.IO) { SafIO.displayName(context, inputUris.first()) }
                else -> "${inputUris.size} files"
            }
    }

    // Decrypted plaintext must never linger on disk. If we leave the screen while a plaintext is still
    // staged in cache (e.g. the user backs out before picking a save location), delete it on dispose.
    DisposableEffect(Unit) {
        onDispose { pendingPlain?.delete() }
    }

    // Build the identities to try, either from the unlocked vault or from a passphrase. The passphrase
    // chars are read on the main thread (View access) and wiped once kage has copied them.
    suspend fun buildIdentities(): List<Identity> =
        when (mode) {
            DecMode.PASSPHRASE -> {
                val chars = readPasswordChars(pwField) ?: throw IllegalArgumentException("Enter the passphrase")
                try {
                    listOf(Passphrase.identity(chars))
                } finally {
                    Arrays.fill(chars, ' ')
                }
            }

            DecMode.IDENTITY -> {
                withContext(Dispatchers.IO) {
                    container.identities.list().map { container.identities.open(it) }
                }
            }
        }

    val pickInput =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            // The system picker can't filter by extension, so restrict to .age files here: keep the
            // age files, report any others, and never select a non-age file for decryption.
            scope.launch {
                val named = withContext(Dispatchers.IO) { uris.map { it to SafIO.displayName(context, it) } }
                val accepted = named.filter { SafIO.isAgeName(it.second) }
                val rejected = named.filterNot { SafIO.isAgeName(it.second) }
                if (accepted.isNotEmpty()) inputUris = accepted.map { it.first }
                status =
                    when {
                        rejected.isEmpty() -> OpStatus.Idle
                        accepted.isEmpty() -> OpStatus.Error("Mage only decrypts .age files.")
                        else -> OpStatus.Error("Ignored non-.age file(s): ${rejected.mapNotNull { it.second }.joinToString()}")
                    }
            }
        }

    // The decrypt already happened (into [pendingPlain]) before this dialog opened, so saving is just
    // a copy — by this point we know the file is decryptable, so the destination is only ever created
    // for a good result.
    val saveOutput =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { output ->
            val temp = pendingPlain
            pendingPlain = null
            if (output == null || temp == null) {
                // Save dialog cancelled — drop the validated plaintext from cache, leave no file.
                temp?.delete()
                if (status is OpStatus.Working) status = OpStatus.Idle
                return@rememberLauncherForActivityResult
            }
            status = OpStatus.Working("Saving…")
            scope.launch {
                status =
                    runCatching {
                        CryptoRunner.copyFileToUri(context, temp, output)
                        output
                    }.fold(
                        onSuccess = { OpStatus.Success("Decrypted to ${SafIO.displayName(context, it) ?: "file"}") },
                        onFailure = {
                            // The copy failed partway — remove the partial file the picker created so we
                            // never leave a truncated "decrypted" output behind.
                            runCatching { DocumentsContract.deleteDocument(context.contentResolver, output) }
                            OpStatus.Error(decryptError(it))
                        },
                    )
                temp.delete()
            }
        }

    val pickFolder =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { tree ->
            if (tree == null) return@rememberLauncherForActivityResult
            status = OpStatus.Working("Decrypting…")
            scope.launch {
                status =
                    runCatching {
                        val identities = buildIdentities()
                        CryptoRunner.decryptBatch(context, identities, inputUris, tree) { i, n ->
                            status = OpStatus.Working("Decrypting $i of $n…")
                        }
                    }.fold(
                        onSuccess = { result ->
                            if (result.failed.isEmpty()) {
                                OpStatus.Success("Decrypted ${result.ok} files")
                            } else {
                                OpStatus.Error("Decrypted ${result.ok}/${result.total}; failed: ${result.failed.joinToString()}")
                            }
                        },
                        onFailure = { OpStatus.Error(decryptError(it)) },
                    )
            }
        }

    fun startDecrypt() {
        if (inputUris.isEmpty()) {
            status = OpStatus.Error("Choose a .age file first")
            return
        }
        val batch = inputUris.size > 1

        // For a single file, decrypt into private cache first (validating the identity/passphrase) and
        // only open the save dialog if that succeeds — so a wrong passphrase never creates a 0-byte
        // file. Batch keeps its own per-file handling (failures are reported and cleaned up).
        suspend fun proceed() {
            if (batch) {
                pickFolder.launch(null)
                return
            }
            val src = inputUris.first()
            status = OpStatus.Working("Decrypting…")
            val temp = withContext(Dispatchers.IO) { File.createTempFile("mage-dec", ".tmp", context.cacheDir) }
            runCatching {
                CryptoRunner.decryptToFile(context, buildIdentities(), src, temp)
            }.fold(
                onSuccess = {
                    pendingPlain = temp
                    saveOutput.launch(SafIO.suggestDecryptedName(inputLabel))
                },
                onFailure = {
                    temp.delete()
                    status = OpStatus.Error(decryptError(it))
                },
            )
        }

        scope.launch {
            // Restrict decryption to age files. This also covers files arriving via the "Decrypt with
            // Mage" share target, which the manifest cannot filter by extension.
            val nonAge =
                withContext(Dispatchers.IO) {
                    inputUris.filterNot { SafIO.isAgeName(SafIO.displayName(context, it)) }
                }
            if (nonAge.isNotEmpty()) {
                status = OpStatus.Error("Mage only decrypts .age files.")
                return@launch
            }

            // Guard against files too large for kage's in-memory decrypt before attempting.
            val limit = CryptoRunner.maxDecryptInputBytes()
            val oversized =
                withContext(Dispatchers.IO) {
                    inputUris.firstOrNull { (SafIO.sizeBytes(context, it) ?: 0L) > limit }
                }
            if (oversized != null) {
                val name = withContext(Dispatchers.IO) { SafIO.displayName(context, oversized) } ?: "This file"
                status =
                    OpStatus.Error(
                        "$name is too large to decrypt on this device (~${limit / (1024 * 1024)} MB max). " +
                            "The age library must load the whole file into memory to decrypt.",
                    )
                return@launch
            }

            when (mode) {
                DecMode.PASSPHRASE -> {
                    if ((pwField?.text?.length ?: 0) == 0) {
                        status = OpStatus.Error("Enter the passphrase")
                    } else {
                        proceed()
                    }
                }

                DecMode.IDENTITY -> {
                    if (identityCount == 0) {
                        status = OpStatus.Error("No identities yet — add one under Keys")
                    } else if (unlock()) {
                        proceed()
                    } else {
                        status = OpStatus.Error("Unlock cancelled")
                    }
                }
            }
        }
    }

    val busy = status is OpStatus.Working

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Open an encrypted .age file with one of your keys, or with its passphrase.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SegmentedButtonGroup(
            options = listOf("My identities", "Passphrase"),
            selectedIndex = if (mode == DecMode.IDENTITY) 0 else 1,
            onSelect = { mode = if (it == 0) DecMode.IDENTITY else DecMode.PASSPHRASE },
        )

        if (mode == DecMode.PASSPHRASE) {
            SecurePasswordField(
                label = "Passphrase",
                show = showPw,
                onToggleShow = { showPw = !showPw },
                onViewCreated = { pwField = it },
            )
        } else {
            SectionCard("Identities") {
                Text(
                    if (identityCount == 0) {
                        "No identities saved yet. Add one under the Keys tab, then come back to decrypt."
                    } else {
                        "Mage will try your $identityCount saved " +
                            if (identityCount == 1) "identity." else "identities."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        FilePickCard(
            label = "File to decrypt",
            chosen = inputLabel,
            hint = "No .age file selected",
            onPick = { pickInput.launch(arrayOf("*/*")) },
        )

        PrimaryActionButton(
            label = if (inputUris.size > 1) "Decrypt ${inputUris.size} → folder" else "Decrypt & save",
            busy = busy,
            enabled = inputUris.isNotEmpty(),
            onClick = { startDecrypt() },
        )

        StatusBanner(status)
    }
}

private fun decryptError(t: Throwable): String {
    if (t is OutOfMemoryError) {
        return "Not enough memory to decrypt this file on this device — it's too large for the " +
            "age library, which loads the whole file into memory."
    }
    vaultInvalidatedMessage(t)?.let { return it }
    val name = t::class.simpleName ?: "Error"
    return when {
        name.contains("UserNotAuthenticated") -> {
            "Vault locked — unlock and try again"
        }

        name.contains("NoIdentities") || name.contains("IncorrectHMAC") || name.contains("Identity") -> {
            "None of your keys (or this passphrase) can open this file"
        }

        else -> {
            "Decryption failed: ${t.message ?: name}"
        }
    }
}
