package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The tuning's strings as tactile keys on the enclosure. Works for
 * any string count (built-in six, alternate tunings, customs up to 12):
 * narrow surfaces get a 3-per-row grid, wide surfaces a single low-to-high
 * row. A manual lock presses the key in (amber LOCKED); AUTO detection
 * merely lights the matching key (green AUTO). Semantics are unchanged:
 * only a manual lock is `selected`.
 */
@Composable
fun InstrumentStringSelector(
    targetLabels: List<String>,
    activeStringIndex: Int?,
    stringLocked: Boolean,
    selectedStringIndex: Int?,
    onStringSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = if (maxWidth < 480.dp || targetLabels.size > 6) 3 else 6
            val chunks = targetLabels.withIndex().toList().chunked(perRow)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chunks.forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                    .heightIn(min = 56.dp)
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
                                    horizontal = 4.dp,
                                    vertical = 6.dp,
                                ),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "${i + 1}",
                                        style = InstrumentType.engraved.copy(fontSize = 9.sp),
                                        maxLines = 1,
                                        modifier = Modifier.clearAndSetSemantics {},
                                    )
                                    Text(
                                        text = label,
                                        style = InstrumentType.keyLabel.copy(
                                            color = if (active) p.ivory else p.textMuted,
                                        ),
                                        maxLines = 1,
                                    )
                                    if (active) {
                                        Text(
                                            text = if (locked) "LOCKED" else "AUTO",
                                            style = InstrumentType.engraved.copy(
                                                fontSize = 9.sp,
                                                color = if (locked) p.lampAmber else p.lampGreen,
                                            ),
                                            maxLines = 1,
                                            modifier = Modifier.clearAndSetSemantics {},
                                        )
                                    }
                                }
                            }
                        }
                        // Keep the last grid row aligned with its peers.
                        repeat(perRow - chunk.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Text(
            text = if (selectedStringIndex != null) {
                "Locked to one string. Tap it again for automatic selection."
            } else {
                "Tap a string to lock its target; automatic selection is on."
            },
            style = InstrumentType.readoutSmall.copy(fontSize = 13.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .testTag("resonance.stringHint"),
        )
    }
}