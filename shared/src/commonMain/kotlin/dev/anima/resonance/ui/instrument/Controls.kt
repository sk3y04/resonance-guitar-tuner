package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The whole action system, in one place — one primary, one secondary,
 * one text action, one stepper key and one switch style. Controls differ by
 * hierarchy, never by a new colour or corner radius.
 *
 * Indication: every standard surface inherits the warm cream state layer from
 * ResonanceTheme (never the petrol fallback scheme). The parchment primary
 * scopes a DARK-ink ripple so press feedback is visible on the light fill.
 * Keyboard focus draws the shared brass hairline. Sizes never change on
 * press/selection — only tone.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    val p = InstrumentTheme.palette
    CompositionLocalProvider(
        androidx.compose.foundation.LocalIndication provides ripple(color = p.accentInk),
    ) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .heightIn(min = 52.dp)
                .focusHairline(InstrumentTokens.cornerMd)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
            shape = RoundedCornerShape(InstrumentTokens.cornerMd),
            color = p.accent,
            contentColor = p.accentInk,
        ) {
            // Content-sized unless the caller asks for a full-width action. A
            // fillMaxWidth() here used to expand inside any Row and starve its
            // siblings (that is what made the settings title wrap one letter per
            // line and "Done" span the sheet).
            Box(
                Modifier
                    .wrapContentSize()
                    .padding(horizontal = InstrumentTokens.space4, vertical = InstrumentTokens.space3),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = InstrumentType.label.copy(
                        fontWeight = FontWeight.SemiBold,
                        // The style carries textPrimary; on a parchment fill that is
                        // invisible, so the fill's ink is stated explicitly.
                        color = p.accentInk,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    val p = InstrumentTheme.palette
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .focusHairline(InstrumentTokens.cornerMd)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        shape = RoundedCornerShape(InstrumentTokens.cornerMd),
        color = p.segmentCap,
        contentColor = p.textPrimary,
    ) {
        Box(
            Modifier
                .wrapContentSize()
                .padding(horizontal = InstrumentTokens.space4, vertical = InstrumentTokens.space2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = InstrumentType.label.copy(color = p.textPrimary),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    color: Color? = null,
) {
    val p = InstrumentTheme.palette
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .focusHairline(InstrumentTokens.cornerSm)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        shape = RoundedCornerShape(InstrumentTokens.cornerSm),
        color = Color.Transparent,
        contentColor = color ?: p.textSecondary,
    ) {
        Box(
            Modifier.padding(horizontal = InstrumentTokens.space3, vertical = InstrumentTokens.space2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = InstrumentType.label.copy(color = color ?: p.textSecondary),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * A numeric stepper key: square, tonal, 48dp, with a glyph and a spoken label.
 * Used by the reference-pitch control so − and + are unmistakable.
 */
@Composable
fun StepperKey(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    val p = InstrumentTheme.palette
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(InstrumentTokens.touchMin)
            .focusHairline(InstrumentTokens.cornerMd)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(InstrumentTokens.cornerMd),
        color = p.segmentCap,
        contentColor = p.textPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, style = InstrumentType.numeric.copy(fontSize = 22.sp), maxLines = 1)
        }
    }
}

/**
 * The one switch style: brass track when on, quiet recess when off. The WHOLE
 * row is the 48dp toggle target with the same warm tonal press layer as every
 * other deck row; the switch itself is the state indicator (no duplicate
 * "On/Off" text). Content sits on the shared gutter line while the press layer
 * runs full-bleed.
 */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    val p = InstrumentTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = InstrumentTokens.touchMin)
            .clip(RoundedCornerShape(InstrumentTokens.cornerSm))
            .background(pressLayerColor(pressed))
            .focusHairline(InstrumentTokens.cornerSm)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
            )
            .padding(horizontal = InstrumentTokens.gutter)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = InstrumentType.label, maxLines = 1)
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.textPrimary,
                checkedTrackColor = p.brass,
                checkedBorderColor = p.brass,
                uncheckedThumbColor = p.textSecondary,
                uncheckedTrackColor = p.deckDeep,
                uncheckedBorderColor = p.deckEdge,
            ),
        )
    }
}
