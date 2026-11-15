package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.anima.resonance.presentation.TunerMode

/**
 * A compact two-position mechanical selector — one recessed slot
 * milled into the enclosure carrying two physical keys. The engaged key
 * stands proud with its amber jewel lit; the idle key sits flat in the
 * slot. Selection is also exposed semantically, so color/shape are never
 * the only carriers. Tags/semantics match the tested control contract.
 */
@Composable
fun MechanicalModeSelector(
    mode: TunerMode,
    onModeSelect: (TunerMode) -> Unit,
    largeText: Boolean,
    modifier: Modifier = Modifier,
) {
    RecessedPanel(
        modifier = modifier.testTag("resonance.mode"),
        shape = RoundedCornerShape(9.dp),
    ) {
        if (largeText) {
            Column(
                modifier = Modifier.padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ModeKey("Chromatic", TunerMode.CHROMATIC, mode, onModeSelect, Modifier.fillMaxWidth())
                ModeKey("Instrument", TunerMode.INSTRUMENT, mode, onModeSelect, Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ModeKey("Chromatic", TunerMode.CHROMATIC, mode, onModeSelect, Modifier.weight(1f))
                ModeKey("Instrument", TunerMode.INSTRUMENT, mode, onModeSelect, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeKey(
    label: String,
    value: TunerMode,
    mode: TunerMode,
    onModeSelect: (TunerMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = InstrumentTheme.palette
    val selected = mode == value
    val tag = if (value == TunerMode.CHROMATIC) "resonance.mode.chromatic" else "resonance.mode.instrument"
    InstrumentKey(
        onClick = { onModeSelect(value) },
        selected = selected,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag)
            .semantics {
                contentDescription = "$label mode"
                this.selected = selected
            },
    ) {
        JewelLamp(lit = selected, color = p.lampAmber, size = 10.dp)
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = InstrumentType.keyLabel.copy(
                // Idle mode label stays legible; the engaged key is
                // unmistakable (proud cap + lit amber jewel + full ivory).
                color = if (selected) p.ivory else p.ivory.copy(alpha = 0.9f),
            ),
            maxLines = 1,
        )
    }
}