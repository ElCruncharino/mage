/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Copies sensitive text (decrypted plaintext, private keys) to the clipboard and clears it again
 * ~60s later, so it doesn't linger. Android 10+ only lets a foreground app read/write the
 * clipboard, so the clear can only run while Mage is foreground; [onForeground] catches up on a
 * clear that was due while the app was backgrounded. Tracks a hash of what was copied, not the
 * text itself, so a clear never wipes something newer the user copied elsewhere in the meantime.
 */
object ClipboardGuard {
    private const val CLEAR_DELAY_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Job? = null
    private var armedDigest: ByteArray? = null
    private var armedAt: Long = 0L

    fun copySensitive(
        context: Context,
        label: String,
        text: String,
    ) {
        val cm = context.clipboard() ?: return
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras =
                PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
        }
        cm.setPrimaryClip(clip)

        armedDigest = sha256(text)
        armedAt = SystemClock.elapsedRealtime()
        pending?.cancel()
        val appContext = context.applicationContext
        pending =
            scope.launch {
                delay(CLEAR_DELAY_MS)
                attemptClear(appContext)
            }
    }

    /** Call from an Activity's onResume: clears an overdue armed copy missed while backgrounded. */
    fun onForeground(context: Context) {
        if (armedDigest != null && SystemClock.elapsedRealtime() - armedAt >= CLEAR_DELAY_MS) {
            attemptClear(context.applicationContext)
        }
    }

    private fun attemptClear(appContext: Context) {
        val want = armedDigest ?: return
        val cm = appContext.clipboard() ?: return
        val current =
            try {
                cm.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(appContext)
                    ?.toString()
            } catch (e: Exception) {
                null
            } ?: return

        if (MessageDigest.isEqual(sha256(current), want)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm.clearPrimaryClip()
            } else {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        disarm()
    }

    private fun disarm() {
        pending?.cancel()
        pending = null
        armedDigest = null
        armedAt = 0L
    }

    private fun Context.clipboard(): ClipboardManager? = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private fun sha256(s: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
}
