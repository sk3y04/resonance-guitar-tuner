package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.presentation.StringSelectionUi

/**
 * The tuning's string targets as ONE row of equal, quiet cells directly
 * on the control surface — no enclosing rail, no keycaps, no brass borders.
 *
 * One selection language, same as every other control: the parchment fill means
 * "you chose this" (animated both ways, with the shared cap edge light). A
 * merely detected string is text emphasis + a short foot marker (observation,
 * not selection) — the marker stays immediate because detected notes change
 * frequently and must not become a distracting animation. Pressing any cell
 * dips to the warm tonal layer at press speed, and a deliberate lock confirms
 * with one subtle platform haptic (never on automatic note changes).
 */
@Composable
fun StringTargets(
    targetLabels: List<String>,
    activeStringIndex: Int?,
    stringLocked: Boolean,
    onStringSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val n = targetLabels.size
        val slots = ControlDeckLayout.perRowSlots(maxWidth.value, n).coerceAtLeast(1)
        val chunks = targetLabels.withIndex().toList().chunked(slots)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ControlDeckLayout.RAIL_ROW_GAP_DP.dp),
        ) {
            chunks.forEach { chunk ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ControlDeckLayout.RAIL_ROW_DP.dp),
                    horizontalArrangement = Arrangement.spacedBy(ControlDeckLayout.STRING_GAP_DP.dp),
                ) {
                    chunk.forEach { indexed ->
                        val i = indexed.index
                        val active = activeStringIndex == i
                        val locked = active && stringLocked
                        val detected = active && !stringLocked
                        StringTarget(
                            index = i,
                            label = indexed.value,
                            locked = locked,
                            detected = detected,
                            onClick = { onStringSelect(i) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    repeat(slots - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StringTarget(
    index: Int,
    label: String,
    locked: Boolean,
    detected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val root = label.dropLast(1)
    val octave = label.lastOrNull()?.toString().orEmpty()
    val description = StringSelectionUi.stringDescription(index, label, locked, detected)
    val shape = RoundedCornerShape(InstrumentTokens.cornerSm)
    // Two layers, two speeds: the selection fill settles at selection speed;
    // the press dip responds at press speed even on the locked cell.
    val fill = selectionFill(locked, p.accent)
    val press = pressLayerColor(
        pressed = pressed,
        pressedColor = if (locked) p.accentPressed else p.segmentCap,
    )
    val textColor = when {
        locked -> p.accentInk
        detected -> p.textPrimary
        else -> p.textSecondary
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .background(press)
            .drawBehind {
                if (locked) selectedCapEdge()
                // Observation, not selection: the string the tracker is hearing
                // gets a short marker at the cell's foot. Immediate on purpose.
                if (detected) {
                    val barW = size.width * 0.30f
                    drawRoundRect(
                        color = p.brass,
                        topLeft = Offset((size.width - barW) / 2f, size.height - 2.dp.toPx()),
                        size = Size(barW, 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx()),
                    )
                }
            }
            .focusHairline(InstrumentTokens.cornerSm)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Select string ${index + 1}, $label",
                onClick = {
                    // One subtle confirmation for a DELIBERATE lock. The
                    // platform haptic honours the system haptics setting; no
                    // haptic ever fires for automatic detection changes.
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .testTag("resonance.string.$index")
            .semantics {
                contentDescription = description
                selected = locked
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = root,
                style = InstrumentType.label.copy(
                    fontSize = 16.sp,
                    fontWeight = if (locked || detected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor,
                ),
                maxLines = 1,
            )
            Text(
                text = octave,
                style = InstrumentType.caption.copy(
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = if (locked) 1f else 0.85f),
                ),
                maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}
