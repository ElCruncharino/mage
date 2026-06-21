/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mage.age.ui.components.ExpressiveLoadingIndicator
import dev.mage.age.ui.theme.StatusLevel
import dev.mage.age.ui.theme.statusColors

/** A titled card section used to group related controls. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** Outcome banner shown after an operation. */
sealed interface OpStatus {
    data object Idle : OpStatus

    data class Working(val message: String) : OpStatus

    data class Success(val message: String) : OpStatus

    data class Error(val message: String) : OpStatus
}

/**
 * Outcome banner. Each state carries a distinct leading icon (so meaning never rests on colour
 * alone, WCAG 1.4.1) and a contrast-guaranteed colour pair. The banner is an assertive live region
 * so TalkBack announces the outcome as soon as it appears.
 */
@Composable
fun StatusBanner(
    status: OpStatus,
    modifier: Modifier = Modifier,
) {
    val (level, message) =
        when (status) {
            is OpStatus.Idle -> return
            is OpStatus.Working -> StatusLevel.WORKING to status.message
            is OpStatus.Success -> StatusLevel.SUCCESS to status.message
            is OpStatus.Error -> StatusLevel.ERROR to status.message
        }
    val colors = statusColors(level)
    Card(
        modifier =
        modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.container),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (level) {
                StatusLevel.WORKING ->
                    ExpressiveLoadingIndicator(diameter = 20.dp, strokeWidth = 2.dp, color = colors.content)
                StatusLevel.SUCCESS ->
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Success", tint = colors.content, modifier = Modifier.size(20.dp))
                StatusLevel.ERROR ->
                    Icon(Icons.Filled.Error, contentDescription = "Error", tint = colors.content, modifier = Modifier.size(20.dp))
            }
            Text(message, color = colors.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Abbreviate a long age key (`age1abc…wxyz`) for compact display in chips and cards. */
internal fun shortKey(key: String): String = if (key.length <= 16) key else key.take(10) + "…" + key.takeLast(4)
