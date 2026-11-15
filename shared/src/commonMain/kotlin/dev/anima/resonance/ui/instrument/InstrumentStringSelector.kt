package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The tuning's strings as a COMPACT supporting control strip — the meter
 * is the primary instrument, these are secondary. Six strings sit in one
 * horizontal row of equal keys when usable touch targets fit (≥48dp each);
 * otherwise a tight grid (fixed ~6dp row gap, normal control height, never
 * stretched through the available height). Adaptive to any string count.
 *
 * The note name is the main label; the string number is a small secondary.
 * Automatic vs manual selection is shown compactly: a jewel on the active key
 * (green = AUTO-detected, amber = LOCKED) plus a small group-level caption —
 * no separate AUTO text line reserved in every button. Semantics are
 * unchanged: only a manual lock is `selected`; the spoken description still
 * carries "detected"/"locked".
 */
@Composable
fun InstrumentStringSelector(
    targetLabels: List<String>,
    activeStringIndex: Int?,
    stringLocked: Boolean,
    onStringSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    Column(modifier = modifier.fillMaxWidth()) {
        // Compact group-level selection state (AUTO follows the nearest string;
        // a manual lock overrides it). One small caption, not per-button text.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JewelLamp(
                lit = activeStringIndex != null,
                color = if (stringLocked) p.lampAmber else p.lampGreen,
                size = 7.dp,
            )
            Spacer(Modifier.size(5.dp))
            Text(
                text = if (stringLocked) "LOCKED" else "AUTO",
                style = InstrumentType.engraved.copy(
                    fontSize = 9.sp,
                    color = if (stringLocked) p.lampAmber else p.textMuted,
                ),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(5.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val n = targetLabels.size
            val minKey = 48.dp
            val rowGap = 4.dp
            // One row when every key keeps a usable touch target; else a grid.
            val singleRowFits = n in 1..8 &&
                maxWidth >= minKey * n + rowGap * (n - 1)
            val perRow = when {
                singleRowFits -> n
                maxWidth >= minKey * 4 + 6.dp * 3 -> 4
                else -> 3
            }
            val keyHeight = if (singleRowFits) 52.dp else 56.dp
            val gap = if (singleRowFits) rowGap else 6.dp
            val chunks = targetLabels.withIndex().toList().chunked(perRow)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                chunks.forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        chunk.forEach { indexed ->
                            val i = indexed.index
                            val label = indexed.value
                            val active = activeStringIndex == i
                            val locked = active && stringLocked
                            InstrumentKey(
                                onClick = { onStringSelect(i) },
                                selected = locked,
                                highlighted = active && !locked,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = keyHeight)
                                    .testTag("resonance.string.$i")
                                    .semantics {
                                        contentDescription =
                                            "String ${i + 1}, $label" + when {
                                                locked -> ", locked"
                                                active -> ", detected"
                                                else -> ""
                                            }
                                        this.selected = locked
                                    },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 2.dp,
                                    vertical = 4.dp,
                                ),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${i + 1}",
                                            style = InstrumentType.engraved.copy(fontSize = 9.sp),
                                            maxLines = 1,
                                            modifier = Modifier.clearAndSetSemantics {},
                                        )
                                        if (active) {
                                            Spacer(Modifier.size(3.dp))
                                            JewelLamp(
                                                lit = true,
                                                color = if (locked) p.lampAmber else p.lampGreen,
                                                size = 6.dp,
                                            )
                                        }
                                    }
                                    Text(
                                        text = label,
                                        style = InstrumentType.keyLabel.copy(
                                            fontSize = 16.sp,
                                            color = if (active) p.ivory else p.ivory.copy(alpha = 0.88f),
                                        ),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        // Keep a short last grid row aligned with its peers.
                        if (!singleRowFits) {
                            repeat(perRow - chunk.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}