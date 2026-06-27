/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.mage.age.qr.QrEncoder

/** Shows a public key as a scannable QR plus its text, with a copy action. */
@Composable
fun QrDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val bitmap = remember(value) { QrEncoder.encode(value).asImageBitmap() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "QR code for $title",
                    modifier = Modifier.size(240.dp),
                )
                Text(value, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(value))
                onDismiss()
            }) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
