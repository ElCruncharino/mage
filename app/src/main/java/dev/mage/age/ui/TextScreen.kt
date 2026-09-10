/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui

import android.widget.EditText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.mage.age.AppContainer
import dev.mage.age.crypto.Identities
import dev.mage.age.crypto.Passphrase
import dev.mage.age.crypto.Recipients
import dev.mage.age.io.ShareSheet
import dev.mage.age.store.VaultIdentity
import dev.mage.age.ui.components.PrimaryActionButton
import dev.mage.age.ui.components.SecurePasswordField
import dev.mage.age.ui.components.SegmentedButtonGroup
import dev.mage.age.ui.components.readPasswordChars
import kage.Identity
import kage.Recipient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays

private enum class TextTopMode { HOME, ENCRYPT, DECRYPT }

// Caps input/output text boxes so a long paste scrolls within the box instead of pushing the
// action buttons off-screen below it.
private val boundedFieldModifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)

/**
 * Quick, in-memory text encrypt/decrypt: no file picker, output is always ASCII-armored so it can
 * be pasted into chat, email, or anywhere a binary attachment is awkward. Also the landing screen
 * for a text share into Mage (see [IntentRouter][dev.mage.age.io.IntentRouter]).
 */
@Composable
fun TextScreen(
    container: AppContainer,
    pending: PendingInput,
    startInDecrypt: Boolean,
    unlock: suspend () -> Boolean,
) {
    val initialText = remember { pending.sharedText }
    var mode by
        rememberSaveable {
            mutableStateOf(
                when {
                    initialText == null -> TextTopMode.HOME
                    startInDecrypt -> TextTopMode.DECRYPT
                    else -> TextTopMode.ENCRYPT
                },
            )
        }

    when (mode) {
        TextTopMode.HOME -> {
            TextHome(
                onEncrypt = { mode = TextTopMode.ENCRYPT },
                onDecrypt = { mode = TextTopMode.DECRYPT },
            )
        }

        TextTopMode.ENCRYPT -> {
            TextEncrypt(
                container = container,
                initialText = initialText,
                unlock = unlock,
                onClose = { mode = TextTopMode.HOME },
            )
        }

        TextTopMode.DECRYPT -> {
            TextDecrypt(
                container = container,
                initialText = initialText,
                unlock = unlock,
                onClose = { mode = TextTopMode.HOME },
            )
        }
    }
}

@Composable
private fun TextHome(
    onEncrypt: () -> Unit,
    onDecrypt: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Encrypt or decrypt a block of text, handy for chat, email, or anywhere a file " +
                "attachment is awkward.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        PrimaryActionButton(label = "Encrypt text", busy = false, onClick = onEncrypt)
        OutlinedButton(onClick = onDecrypt, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("Decrypt text")
        }
    }
}

// MARK: - Encrypt

private enum class TextEncMode { RECIPIENTS, PASSPHRASE }

@Composable
private fun TextEncrypt(
    container: AppContainer,
    initialText: String?,
    unlock: suspend () -> Boolean,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(TextEncMode.RECIPIENTS) }
    var input by rememberSaveable { mutableStateOf(initialText ?: "") }
    val chosen = remember { mutableStateListOf<String>() }
    var recipientInput by remember { mutableStateOf("") }
    var pwField by remember { mutableStateOf<EditText?>(null) }
    var confirmField by remember { mutableStateOf<EditText?>(null) }
    var showPw by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<OpStatus>(OpStatus.Idle) }

    var savedIdentities by remember { mutableStateOf<List<VaultIdentity>>(emptyList()) }
    var savedRecipients by remember { mutableStateOf<List<dev.mage.age.store.SavedRecipient>>(emptyList()) }
    LaunchedEffect(Unit) {
        savedIdentities = withContext(Dispatchers.IO) { container.identities.list() }
        savedRecipients = withContext(Dispatchers.IO) { container.recipients.list() }
    }

    fun buildRecipients(): Result<List<Recipient>> =
        runCatching {
            when (mode) {
                TextEncMode.PASSPHRASE -> {
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

                TextEncMode.RECIPIENTS -> {
                    require(chosen.isNotEmpty()) { "Add at least one recipient" }
                    val kinds = chosen.map { Recipients.kindOf(it) }.toSet()
                    require(Recipients.Kind.PQ !in kinds || kinds.size == 1) {
                        "A post-quantum recipient can't be mixed with other recipients"
                    }
                    chosen.map { Recipients.parse(it) }
                }
            }
        }

    if (result != null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Encrypt text", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            CopyableResult(label = "Armored output", value = result!!)
            Button(
                onClick = {
                    result = null
                    input = ""
                    chosen.clear()
                    status = OpStatus.Idle
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Encrypt another") }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Encrypt text", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Text to encrypt") },
            minLines = 6,
            modifier = boundedFieldModifier,
        )

        SegmentedButtonGroup(
            options = listOf("Recipients", "Passphrase"),
            selectedIndex = if (mode == TextEncMode.RECIPIENTS) 0 else 1,
            onSelect = { mode = if (it == 0) TextEncMode.RECIPIENTS else TextEncMode.PASSPHRASE },
        )

        if (mode == TextEncMode.RECIPIENTS) {
            RecipientsPicker(
                chosen = chosen,
                recipientInput = recipientInput,
                onRecipientInputChange = { recipientInput = it },
                onStatusChange = { status = it },
                savedIdentities = savedIdentities,
                savedRecipients = savedRecipients,
                description =
                    "Add the public keys of who should be able to read this message — age (age1…) or " +
                        "SSH (ssh-ed25519 / ssh-rsa) keys.",
            )
        } else {
            SecurePasswordField(label = "Passphrase", show = showPw, onToggleShow = { showPw = !showPw }, onViewCreated = { pwField = it })
            SecurePasswordField(
                label = "Confirm passphrase",
                show = showPw,
                onToggleShow = { showPw = !showPw },
                onViewCreated = { confirmField = it },
            )
            GeneratePassphraseButton(pwField = pwField, confirmField = confirmField, onGenerated = { showPw = true })
        }

        if (status is OpStatus.Error) StatusBanner(status)

        val busy = status is OpStatus.Working
        PrimaryActionButton(
            label = if (busy) "Encrypting…" else "Encrypt",
            busy = busy,
            enabled = input.isNotEmpty(),
            onClick = {
                if (!busy) {
                    val recipients =
                        buildRecipients().getOrElse {
                            status = OpStatus.Error(it.message ?: "Invalid recipients")
                            return@PrimaryActionButton
                        }
                    status = OpStatus.Working("Encrypting…")
                    scope.launch {
                        if (mode == TextEncMode.PASSPHRASE && !unlock()) {
                            status = OpStatus.Error("Unlock cancelled")
                            return@launch
                        }
                        status =
                            runCatching { CryptoRunner.encryptText(recipients, input, armor = true) }
                                .fold(
                                    onSuccess = {
                                        result = String(it, Charsets.UTF_8)
                                        OpStatus.Idle
                                    },
                                    onFailure = { t ->
                                        if (t is OutOfMemoryError) {
                                            OpStatus.Error("Not enough memory to encrypt with a passphrase on this device.")
                                        } else {
                                            OpStatus.Error("Encryption failed: ${t.message ?: t}")
                                        }
                                    },
                                )
                    }
                }
            },
        )
        TextButton(onClick = onClose) { Text("Cancel") }
    }
}

// MARK: - Decrypt

private enum class DecStage { FORM, NEED_PASSPHRASE }

@Composable
private fun TextDecrypt(
    container: AppContainer,
    initialText: String?,
    unlock: suspend () -> Boolean,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(DecStage.FORM) }
    var input by rememberSaveable { mutableStateOf(initialText ?: "") }
    var pwField by remember { mutableStateOf<EditText?>(null) }
    var showPw by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<OpStatus>(OpStatus.Idle) }

    suspend fun tryIdentities(): List<Identity>? {
        if (!unlock()) return null
        return withContext(Dispatchers.IO) { container.identities.list().map { container.identities.open(it) } }
    }

    fun decrypt(identities: List<Identity>) {
        // Same guard BackupManager uses before its own fully-buffering decrypt: reject an oversized
        // input before spending the memory/CPU on a scrypt pass and a second full-size byte copy.
        if (input.length > CryptoRunner.maxDecryptInputBytes()) {
            status = OpStatus.Error("This text is too large to decrypt on this device")
            return
        }
        status = OpStatus.Working("Decrypting…")
        scope.launch {
            status =
                runCatching { CryptoRunner.decryptText(identities, input) }
                    .fold(
                        onSuccess = {
                            result = String(it, Charsets.UTF_8)
                            stage = DecStage.FORM
                            OpStatus.Idle
                        },
                        onFailure = { t ->
                            if (t is OutOfMemoryError) {
                                stage = DecStage.NEED_PASSPHRASE
                                OpStatus.Error("Not enough memory to derive the key, the work factor is very high.")
                            } else if (stage == DecStage.NEED_PASSPHRASE) {
                                OpStatus.Error(decryptError(t))
                            } else {
                                stage = DecStage.NEED_PASSPHRASE
                                OpStatus.Idle
                            }
                        },
                    )
        }
    }

    if (result != null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Decrypt text", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            CopyableResult(label = "Decrypted text", value = result!!)
            Button(
                onClick = {
                    result = null
                    input = ""
                    stage = DecStage.FORM
                    status = OpStatus.Idle
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Decrypt another") }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Decrypt text", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Paste armored age text (-----BEGIN AGE ENCRYPTED FILE-----)") },
            minLines = 6,
            enabled = stage != DecStage.NEED_PASSPHRASE,
            modifier = boundedFieldModifier,
        )

        if (stage == DecStage.NEED_PASSPHRASE) {
            Text(
                "No matching identity. If this was encrypted with a passphrase, enter it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecurePasswordField(label = "Passphrase", show = showPw, onToggleShow = { showPw = !showPw }, onViewCreated = { pwField = it })
        }

        if (status is OpStatus.Error) StatusBanner(status)

        val busy = status is OpStatus.Working
        if (stage == DecStage.NEED_PASSPHRASE) {
            PrimaryActionButton(
                label = if (busy) "Decrypting…" else "Decrypt with passphrase",
                busy = busy,
                onClick = {
                    val chars = readPasswordChars(pwField)
                    if (chars == null) {
                        status = OpStatus.Error("Enter the passphrase")
                        return@PrimaryActionButton
                    }
                    try {
                        decrypt(listOf(Passphrase.identity(chars)))
                    } finally {
                        Arrays.fill(chars, ' ')
                    }
                },
            )
        } else {
            PrimaryActionButton(
                label = if (busy) "Decrypting…" else "Decrypt",
                busy = busy,
                enabled = input.isNotBlank(),
                onClick = {
                    status = OpStatus.Working("Decrypting…")
                    scope.launch {
                        // Opening a saved identity can throw (e.g. biometric enrollment changed and
                        // invalidated the Keystore key) — runCatching so that surfaces as an error
                        // instead of crashing, same as DecryptScreen's equivalent identity-opening call.
                        runCatching { tryIdentities() }.fold(
                            onSuccess = { identities ->
                                when {
                                    identities == null -> {
                                        status = OpStatus.Error("Unlock cancelled")
                                    }

                                    identities.isEmpty() -> {
                                        stage = DecStage.NEED_PASSPHRASE
                                        status = OpStatus.Idle
                                    }

                                    else -> {
                                        decrypt(identities)
                                    }
                                }
                            },
                            onFailure = { t -> status = OpStatus.Error(decryptError(t)) },
                        )
                    }
                },
            )
        }
        TextButton(onClick = onClose) { Text("Cancel") }
    }
}

// MARK: - Shared

@Composable
private fun CopyableResult(
    label: String,
    value: String,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1500)
            copied = false
        }
    }
    SectionCard(label) {
        // Capped so a long armored blob scrolls within this field instead of pushing the
        // Copy/Share buttons off-screen below it.
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            minLines = 6,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = boundedFieldModifier,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    ClipboardGuard.copySensitive(context, label, value)
                    copied = true
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (copied) "Copied ✓" else "Copy") }
            OutlinedButton(
                onClick = { context.startActivity(ShareSheet.shareText(value)) },
                modifier = Modifier.weight(1f),
            ) { Text("Share") }
        }
    }
}
