package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one segmented-control primitive used by mode selection,
 * calibration precision, note names and the settings destination strip.
 *
 * Physical model: keys sit in a track; the chosen key is a parchment cap with a
 * restrained top catch-light / bottom shade (the same implied light as the
 * faceplate). Idle keys are flat; pressing dips to a warm tonal layer; keyboard
 * focus draws the shared brass hairline. Selection and press are TWO animated
 * layers, so pressing an already-selected key responds at press speed without
 * waiting for the selection tween. All keys keep their full touch height.
 *
 * [recessed] milled-track treatment is reserved for the mode selector — the
 * settings strip stays visually quiet on the sheet.
 */
@Composable
fun SegmentTrack(
    modifier: Modifier = Modifier,
    height: Dp = ControlDeckLayout.TOUCH_MIN_DP.dp,
    shape: Shape = RoundedCornerShape(InstrumentTokens.cornerMd),
    recessed: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val p = InstrumentTheme.palette
    val trackModifier = if (recessed) {
        Modifier
            .clip(shape)
            .background(p.deckDeep)
            .drawBehind {
                // A shallow milled recess: shadow along the top wall, a faint
                // catch-light on the bottom lip. Light comes from above.
                val depth = 3.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Transparent,
                        startY = 0f,
                        endY = depth,
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width, depth),
                )
                drawLine(
                    color = p.deckTop.copy(alpha = 0.55f),
                    start = Offset(0f, size.height - 0.5.dp.toPx()),
                    end = Offset(size.width, size.height - 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            // Keys keep the 48dp touch floor: the track grows around them.
            .padding(3.dp)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(trackModifier),
        horizontalArrangement = Arrangement.spacedBy(InstrumentTokens.rowGap),
        content = content,
    )
}

/**
 * One key inside a [SegmentTrack]. [selected] drives the parchment cap + edge
 * light; the semantic `selected` state is always exposed, and the label weight
 * changes with selection so state is never carried by colour alone.
 */
@Composable
fun RowScope.InstrumentSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    description: String? = null,
    role: Role = Role.Tab,
    enabled: Boolean = true,
) {
    val p = InstrumentTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(InstrumentTokens.cornerMd)
    // Selection layer: parchment when chosen, transparent otherwise.
    val selectionFill = selectionFill(selected && enabled, p.accent)
    // Press layer: a warm dip for idle keys; a slightly deeper parchment for
    // the chosen key. Disabled keys never respond.
    val pressFill = pressLayerColor(
        pressed = pressed && enabled,
        pressedColor = if (selected) p.accentPressed else p.segmentCap,
    )
    val disabledWash = if (!enabled) p.deckDeep.copy(alpha = 0.6f) else Color.Transparent
    Box(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            // Horizontal inset only: a key must keep the full touch height.
            .padding(horizontal = 2.dp)
            .clip(shape)
            .background(disabledWash)
            .background(selectionFill)
            .background(pressFill)
            .drawBehind { if (selected && enabled) selectedCapEdge() }
            .focusHairline(InstrumentTokens.cornerMd)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = role,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics {
                if (description != null) contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = InstrumentType.label.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = when {
                    !enabled -> p.textSecondary.copy(alpha = 0.45f)
                    selected -> p.accentInk
                    else -> p.textPrimary
                },
            ),
            maxLines = 1,
        )
    }
}
