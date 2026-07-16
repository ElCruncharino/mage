/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.mage.age.ui

import android.os.Build
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.mage.age.AppContainer
import dev.mage.age.backup.BackupManager
import dev.mage.age.store.BiometricGate
import dev.mage.age.store.ThemeMode
import dev.mage.age.ui.components.SecurePasswordField
import dev.mage.age.ui.components.SegmentedButtonGroup
import dev.mage.age.ui.components.readPasswordChars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays

// A small curated set of accent seeds. Each drives the whole palette through schemeFromSeed.
private val accentOptions =
    listOf(
        "Indigo" to 0xFF2E4BA6.toInt(),
        "Teal" to 0xFF1E7D74.toInt(),
        "Violet" to 0xFF6750A4.toInt(),
        "Rose" to 0xFFB3324F.toInt(),
        "Amber" to 0xFFB26A00.toInt(),
        "Forest" to 0xFF2E6B3F.toInt(),
    )

@Composable
fun SettingsScreen(
    container: AppContainer,
    unlock: suspend () -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = container.theme

    var biometricLock by remember { mutableStateOf(container.settings.biometricLock) }
    var defaultArmor by remember { mutableStateOf(container.settings.defaultArmor) }
    var identityCount by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<OpStatus>(OpStatus.Idle) }
    var confirmReset by remember { mutableStateOf(false) }

    // Backup / restore state. The export passphrase is held as a wipeable char[] from the moment the
    // dialog confirms until the backup file has been written, then zeroed.
    var showExportPass by remember { mutableStateOf(false) }
    var showRestorePass by remember { mutableStateOf(false) }
    var exportPass by remember { mutableStateOf<CharArray?>(null) }
    var exportArmor by remember { mutableStateOf(true) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    suspend fun refreshCount() {
        identityCount = withContext(Dispatchers.IO) { container.identities.list().size }
    }
    LaunchedEffect(Unit) { refreshCount() }

    val saveBackup =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { output ->
            val pass = exportPass
            if (output == null || pass == null) {
                pass?.let { Arrays.fill(it, ' ') }
                exportPass = null
                return@rememberLauncherForActivityResult
            }
            status = OpStatus.Working("Backing up…")
            scope.launch {
                status =
                    runCatching {
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
                Arrays.fill(pass, ' ')
                exportPass = null
            }
        }

    val openBackup =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
        SectionCard("Appearance") {
            Text("Theme", style = MaterialTheme.typography.bodyMedium)
            SegmentedButtonGroup(
                options = listOf("System", "Light", "Dark"),
                selectedIndex =
                    when (theme.themeMode) {
                        ThemeMode.SYSTEM -> 0
                        ThemeMode.LIGHT -> 1
                        ThemeMode.DARK -> 2
                    },
                onSelect = {
                    theme.themeMode =
                        when (it) {
                            0 -> ThemeMode.SYSTEM
                            1 -> ThemeMode.LIGHT
                            else -> ThemeMode.DARK
                        }
                },
            )

            val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val dynamicActive = theme.dynamicColor && theme.accentSeed == null
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(
                    checked = theme.dynamicColor,
                    enabled = dynamicSupported && theme.accentSeed == null,
                    onCheckedChange = { theme.dynamicColor = it },
                    modifier =
                        Modifier.semantics {
                            stateDescription = if (theme.dynamicColor) "On" else "Off"
                        },
                )
                Column {
                    Text("Material You colours")
                    Text(
                        when {
                            !dynamicSupported -> "Needs Android 12 or newer"
                            theme.accentSeed != null -> "Off while a custom accent is chosen"
                            dynamicActive -> "Colours follow your wallpaper"
                            else -> "Use a colour drawn from your wallpaper"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text("Accent colour", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accentOptions.forEach { (name, argb) ->
                    AccentSwatch(
                        color = Color(argb),
                        label = name,
                        selected = theme.accentSeed == argb,
                        onClick = { theme.accentSeed = argb },
                    )
                }
            }
            TextButton(
                enabled = theme.accentSeed != null,
                onClick = { theme.accentSeed = null },
            ) { Text("Use default colours") }
        }

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
                    modifier =
                        Modifier.semantics {
                            stateDescription = if (biometricLock) "On" else "Off"
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier =
                        Modifier.semantics {
                            stateDescription = if (defaultArmor) "On" else "Off"
                        },
                )
                Column {
                    Text("ASCII armor by default")
                    Text(
                        "Start new encryptions with text-safe output on",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                exportArmor = armor
                exportPass = pass
                scope.launch {
                    if (unlock()) {
                        saveBackup.launch("mage-identities-backup.age")
                    } else {
                        Arrays.fill(pass, ' ')
                        exportPass = null
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
                    Arrays.fill(pass, ' ')
                    status = OpStatus.Error("No backup file selected")
                } else {
                    scope.launch {
                        if (!unlock()) {
                            Arrays.fill(pass, ' ')
                            status = OpStatus.Error("Unlock cancelled")
                            return@launch
                        }
                        status = OpStatus.Working("Restoring…")
                        val result =
                            runCatching {
                                BackupManager.import(context, container, pass, uri)
                            }
                        Arrays.fill(pass, ' ')
                        status =
                            result.fold(
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

/** A round accent-colour chip. The 48dp box is the tap target; the inner 36dp circle is the swatch. */
@Composable
private fun AccentSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = "$label accent colour"
                    stateDescription = if (selected) "Selected" else "Not selected"
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                // Pick a check tint that contrasts with the swatch.
                val tint = if (color.luminance() < 0.5f) Color.White else Color.Black
                Icon(Icons.Filled.Check, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ExportPassphraseDialog(
    onDismiss: () -> Unit,
    onConfirm: (CharArray, Boolean) -> Unit,
) {
    var passField by remember { mutableStateOf<EditText?>(null) }
    var confirmField by remember { mutableStateOf<EditText?>(null) }
    var show by remember { mutableStateOf(false) }
    var armor by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup passphrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecurePasswordField(
                    label = "Passphrase",
                    show = show,
                    onToggleShow = { show = !show },
                    onViewCreated = { passField = it },
                )
                SecurePasswordField(
                    label = "Confirm passphrase",
                    show = show,
                    onToggleShow = { show = !show },
                    onViewCreated = { confirmField = it },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = armor, onCheckedChange = { armor = it })
                    Text("Text-safe (armored) file", style = MaterialTheme.typography.bodySmall)
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pass = readPasswordChars(passField)
                val confirm = readPasswordChars(confirmField)
                when {
                    pass == null -> {
                        confirm?.let { Arrays.fill(it, ' ') }
                        error = "Enter a passphrase"
                    }

                    confirm == null || !pass.contentEquals(confirm) -> {
                        Arrays.fill(pass, ' ')
                        confirm?.let { Arrays.fill(it, ' ') }
                        error = "Passphrases do not match"
                    }

                    else -> {
                        Arrays.fill(confirm, ' ')
                        onConfirm(pass, armor)
                    }
                }
            }) { Text("Export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RestorePassphraseDialog(
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var passField by remember { mutableStateOf<EditText?>(null) }
    var show by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecurePasswordField(
                    label = "Backup passphrase",
                    show = show,
                    onToggleShow = { show = !show },
                    onViewCreated = { passField = it },
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pass = readPasswordChars(passField)
                if (pass == null) {
                    error = "Enter the backup passphrase"
                } else {
                    onConfirm(pass)
                }
            }) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun versionName(context: android.content.Context): String =
    runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "Version ${info.versionName}"
    }.getOrDefault("Version 0.1.0")
