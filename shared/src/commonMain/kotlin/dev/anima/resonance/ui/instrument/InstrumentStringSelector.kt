package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
 * The tuning's strings as tactile keys mounted on the faceplate.
 * Works for any string count (built-in six, alternate tunings, customs up
 * to 12): narrow surfaces get a 3-per-row grid, wide surfaces a single
 * low-to-high row. A manual lock raises the key (amber LOCKED); AUTO
 * detection highlights the matching key (green AUTO). Semantics are
 * unchanged: only a manual lock is `selected`.
 *
 * The note name is the prominent label (17sp; the string number stays a
 * muted 9sp secondary). With [fillHeight] the keys grow to absorb spare
 * faceplate height (up to a comfortable cap, centred in their row) so the
 * space improves the controls instead of becoming an empty footer; otherwise
 * they keep a natural minimum height (scroll fallback).
 */
@Composable
fun InstrumentStringSelector(
    targetLabels: List<String>,
    activeStringIndex: Int?,
    stringLocked: Boolean,
    onStringSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
) {
    val p = InstrumentTheme.palette
    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
        ) {
            val perRow = if (maxWidth < 480.dp || targetLabels.size > 6) 3 else 6
            val chunks = targetLabels.withIndex().toList().chunked(perRow)
            Column(
                modifier = Modifier.fillMaxWidth().then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chunks.forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth().then(if (fillHeight) Modifier.weight(1f) else Modifier),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        chunk.forEach { indexed ->
                            val i = indexed.index
                            val label = indexed.value
                            val active = activeStringIndex == i
                            val locked = active && stringLocked
                            val keyHeight = if (fillHeight) {
                                Modifier.heightIn(min = 72.dp, max = 104.dp)
                            } else {
                                Modifier.heightIn(min = 76.dp)
                            }
                            // Centre the key in its (possibly taller) row so spare
                            // height reads as breathing room, not an empty band.
                            Box(
                                modifier = Modifier.weight(1f).then(
                                    if (fillHeight) Modifier.fillMaxHeight() else Modifier,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                InstrumentKey(
                                    onClick = { onStringSelect(i) },
                                    selected = locked,
                                    highlighted = active && !locked,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(keyHeight)
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
                                        verticalArrangement = Arrangement.Center,
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
                                                fontSize = 17.sp,
                                                color = if (active) p.ivory else p.ivory.copy(alpha = 0.88f),
                                            ),
                                            maxLines = 1,
                                        )
                                        if (active) {
                                            Text(
                                                text = if (locked) "LOCKED" else "AUTO",
                                                style = InstrumentType.engraved.copy(
                                                    fontSize = 10.sp,
                                                    color = if (locked) p.lampAmber else p.lampGreen,
                                                ),
                                                maxLines = 1,
                                                modifier = Modifier.clearAndSetSemantics {},
                                            )
                                        }
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
    }
}