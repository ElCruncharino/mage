/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui.components

import android.provider.Settings
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A soft full-screen gradient drawn from the theme's primary/tertiary accents into the surface
 * colour. Because the accents track the active seed, the whole backdrop gently recolours when the
 * accent changes. Colours are animated so theme/seed changes cross-fade rather than snap.
 */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val topColor by animateColorAsState(
        targetValue = scheme.primaryContainer.copy(alpha = 0.55f),
        animationSpec = tween(600),
        label = "gradientTop",
    )
    val midColor by animateColorAsState(
        targetValue = scheme.tertiaryContainer.copy(alpha = 0.30f),
        animationSpec = tween(600),
        label = "gradientMid",
    )
    val brush =
        Brush.linearGradient(
            colors = listOf(topColor, midColor, scheme.surface),
            start = Offset.Zero,
            end = Offset(0f, Float.POSITIVE_INFINITY),
        )
    Box(modifier = modifier.fillMaxSize().background(scheme.surface).background(brush)) {
        content()
    }
}

/** One destination in the [FloatingNavBar]. */
data class NavBarItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * A floating, pill-shaped navigation toolbar — the expressive replacement for the bottom
 * `NavigationBar`. The selected pill shows its label; the rest are icon-only to save space. Each
 * pill is a ≥48dp tap target exposing the tab role and its selected state to TalkBack.
 */
@Composable
fun FloatingNavBar(
    items: List<NavBarItem>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item -> NavPill(item) }
            }
        }
    }
}

@Composable
private fun NavPill(item: NavBarItem) {
    val bg = if (item.selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val fg =
        if (item.selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier =
            Modifier
                // A single merged tab node: TalkBack reads "<label>, tab, selected/not selected".
                .clearAndSetSemantics {
                    contentDescription = item.label
                    role = Role.Tab
                    stateDescription = if (item.selected) "Selected" else "Not selected"
                }.selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick)
                .heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(item.icon, contentDescription = null, tint = fg)
            if (item.selected) {
                Text(
                    item.label,
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * A connected segmented selector (a stand-in for the expressive `ButtonGroup`, which is still
 * `internal` in the pinned release). The selected segment fills with the primary colour; the rest
 * sit on the surface-variant track. Each segment is a selectable exposing the radio role and its
 * selected state.
 */
@Composable
fun SegmentedButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(4.dp).selectableGroup()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val bg by animateColorAsState(
                    targetValue =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    animationSpec = tween(220),
                    label = "segBg",
                )
                val fg =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .height(48.dp)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) }
                            .semantics { stateDescription = if (selected) "Selected" else "Not selected" },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {}
                    Text(
                        label,
                        color = fg,
                        textAlign = TextAlign.Center,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * A file-selection card: a label, the current selection (or a hint), and a Choose/Change button.
 * The label + selection are merged so TalkBack announces the card as one unit, and the button keeps
 * its own action label.
 */
@Composable
fun FilePickCard(
    label: String,
    chosen: String?,
    hint: String,
    onPick: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        // Merge the label + current selection so TalkBack reads them as one phrase.
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$label. ${chosen ?: hint}"
                        },
            ) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(
                    chosen ?: hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onPick,
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = (if (chosen == null) "Choose " else "Change ") + label
                        },
            ) { Text(if (chosen == null) "Choose" else "Change") }
        }
    }
}

/**
 * The full-width primary action button shared by Encrypt / Decrypt. Shows the expressive loading
 * indicator and a "Working…" label while [busy]; otherwise the [label].
 */
@Composable
fun PrimaryActionButton(
    label: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(20.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { stateDescription = if (busy) "Working" else "" },
    ) {
        if (busy) {
            ExpressiveLoadingIndicator(
                diameter = 20.dp,
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.size(12.dp))
            Text("Working…")
        } else {
            Text(label)
        }
    }
}

/**
 * True when the user has turned animations off system-wide (animator duration scale 0 — set via
 * Accessibility "Remove animations" or Developer options). Read once and remembered; this setting
 * doesn't change mid-session in practice. Used to suppress continuous motion for vestibular safety.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * A small custom progress indicator with an expressive feel: a rounded-cap arc whose sweep length
 * breathes while the whole thing spins. The stable Material 3 `LoadingIndicator` (expressive) is
 * still `internal` in the pinned release, so this is hand-rolled from the animation primitives.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    diameter: Dp = 22.dp,
    strokeWidth: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    // Respect the system "remove animations" setting (Accessibility / Developer options). When motion
    // is disabled we draw a static arc so the control still reads as a progress indicator — busy state
    // is also announced via the status banner's live region, so no information is lost.
    if (rememberReduceMotion()) {
        Canvas(modifier = modifier.size(diameter)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx()
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset / 2f, inset / 2f),
                size = Size(size.width - inset, size.height - inset),
                style = stroke,
            )
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rotation",
    )
    val sweep by transition.animateFloat(
        initialValue = 30f,
        targetValue = 290f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    Canvas(modifier = modifier.size(diameter)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val inset = strokeWidth.toPx()
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset / 2f, inset / 2f),
            size = Size(size.width - inset, size.height - inset),
            style = stroke,
        )
    }
}

/**
 * Reads the password directly from the EditText as a char[], without ever creating a String, so it
 * can be wiped after use. Returns null when empty.
 */
internal fun readPasswordChars(editText: EditText?): CharArray? {
    val editable = editText?.text ?: return null
    val length = editable.length
    if (length == 0) {
        return null
    }
    // Copy out char-by-char via CharSequence.get rather than Editable.getChars: equivalent, avoids
    // ever materializing a String (so the result can be wiped), and not subject to platform-method
    // resolution quirks.
    val chars = CharArray(length)
    for (i in 0 until length) chars[i] = editable[i]
    return chars
}

/**
 * A password field backed by a native EditText. Unlike a Compose TextField (whose value is a String
 * that cannot be wiped), this lets the password be read out as a char[] via [readPasswordChars] and
 * erased after use. The [label] is used both as the visible header and the TalkBack description.
 */
@Composable
fun SecurePasswordField(
    label: String,
    show: Boolean,
    onToggleShow: () -> Unit,
    onViewCreated: (EditText) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val accentColor = MaterialTheme.colorScheme.primary.toArgb()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onToggleShow,
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = if (show) "Hide $label" else "Show $label"
                            },
                ) { Text(if (show) "Hide" else "Show") }
            }
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                factory = { ctx ->
                    EditText(ctx).apply {
                        setSingleLine(true)
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        transformationMethod = PasswordTransformationMethod.getInstance()
                        // Label the native field for TalkBack; the visual header above is a separate
                        // composable and is not otherwise associated.
                        hint = label
                        contentDescription = label
                        onViewCreated(this)
                    }
                },
                update = { et ->
                    et.setTextColor(textColor)
                    et.setHintTextColor(hintColor)
                    et.highlightColor = accentColor
                    et.transformationMethod =
                        if (show) null else PasswordTransformationMethod.getInstance()
                    et.setSelection(et.text.length)
                },
            )
        }
    }
}
