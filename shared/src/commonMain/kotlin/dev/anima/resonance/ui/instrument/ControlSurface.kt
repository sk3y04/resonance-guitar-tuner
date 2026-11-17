package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The lower half of the instrument as ONE full-bleed matte faceplate.
 *
 * The deck is the app's own surface (edge to edge, no floating card): a warm
 * espresso panel with a gentle top-lit gradient, a static satin grain and a
 * soft contact shadow where it meets the measurement band — one continuous
 * horizontal seam, nothing doubled. Rows run full-bleed so their press layers
 * span the deck like a native list, while every row's CONTENT sits on the one
 * shared gutter line (the deck itself no longer pads horizontally).
 */
@Composable
fun ControlDeck(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            // Square top edge: the deck joins the readout through ONE continuous
            // horizontal seam. No top radii and no divider line of its own — the
            // faceplate's contact shadow is the only boundary treatment.
            .faceplateFinish(grain = true, seamShadow = true),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Top,
        content = content,
    )
}

/** A small chevron: "there is a destination behind this row". */
@Composable
fun ChevronGlyph(modifier: Modifier = Modifier, color: Color? = null) {
    val p = InstrumentTheme.palette
    Canvas(modifier = modifier.size(width = 10.dp, height = 16.dp)) {
        val stroke = 1.8.dp.toPx()
        val c = color ?: p.textSecondary
        drawLine(c, Offset(2.dp.toPx(), 3.dp.toPx()), Offset(size.width - 3.dp.toPx(), size.height / 2f), stroke)
        drawLine(c, Offset(size.width - 3.dp.toPx(), size.height / 2f), Offset(2.dp.toPx(), size.height - 3.dp.toPx()), stroke)
    }
}

/**
 * Active tuning: a two-line row (small label, then the name) with a chevron.
 * The whole row is the destination; a gear is deliberately NOT parked beside it.
 * Press feedback is a warm tonal layer across the full row, animated both ways.
 */
@Composable
fun TuningSummaryRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(InstrumentTokens.cornerMd))
            .background(pressLayerColor(pressed))
            .focusHairline(InstrumentTokens.cornerMd)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Change tuning",
                onClick = onClick,
            )
            .padding(horizontal = InstrumentTokens.gutter)
            .testTag("resonance.openSheet")
            .semantics { contentDescription = "Tuning, $name" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Tuning", style = InstrumentType.caption)
            Spacer(Modifier.size(2.dp))
            Text(
                text = name,
                style = InstrumentType.title.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("resonance.preset"),
            )
        }
        ChevronGlyph()
    }
}

/**
 * Configuration summary: the value is the affordance (with a chevron) and opens
 * the calibration destination. Same press language as the tuning row.
 */
@Composable
fun ConfigSummaryRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    valueTag: String,
    modifier: Modifier = Modifier,
    rowTag: String = "resonance.openReference",
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(InstrumentTokens.cornerMd))
            .background(pressLayerColor(pressed))
            .focusHairline(InstrumentTokens.cornerMd)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open calibration",
                onClick = onClick,
            )
            .padding(horizontal = InstrumentTokens.gutter)
            .testTag(rowTag)
            .semantics { contentDescription = "$label, $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        Text(label, style = InstrumentType.body, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = InstrumentType.body.copy(color = InstrumentTheme.palette.textPrimary),
                maxLines = 1,
                modifier = Modifier.testTag(valueTag),
            )
            Spacer(Modifier.width(InstrumentTokens.space2))
            ChevronGlyph()
        }
    }
}

/**
 * Chromatic mode's single configuration entry: what applies, plus the two facts
 * worth surfacing, all in one row that clearly leads somewhere.
 */
@Composable
fun ChromaticConfigRow(
    headline: String,
    a4: String,
    spelling: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(InstrumentTokens.cornerMd))
            .background(pressLayerColor(pressed))
            .focusHairline(InstrumentTokens.cornerMd)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open calibration",
                onClick = onClick,
            )
            .padding(horizontal = InstrumentTokens.gutter)
            .testTag("resonance.openSheet")
            .semantics { contentDescription = "$headline, $a4, $spelling" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = headline,
                style = InstrumentType.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("resonance.preset"),
            )
            Spacer(Modifier.size(3.dp))
            Row {
                Text(a4, style = InstrumentType.caption, modifier = Modifier.testTag("resonance.concert"))
                Text("  ·  ", style = InstrumentType.caption)
                Text(spelling, style = InstrumentType.caption, modifier = Modifier.testTag("resonance.spelling"))
            }
        }
        ChevronGlyph()
    }
}
