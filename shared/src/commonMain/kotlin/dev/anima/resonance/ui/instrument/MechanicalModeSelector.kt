package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
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
 * A two-position mechanical selector milled into the enclosure.
 * The engaged key sits pressed in its slot with its amber jewel lit;
 * selection is also exposed semantically and by the pressed shape, so
 * color alone never carries the state. Tags/semantics match the tested
 * segmented control contract.
 */
@Composable
fun MechanicalModeSelector(
    mode: TunerMode,
    onModeSelect: (TunerMode) -> Unit,
    largeText: Boolean,
    modifier: Modifier = Modifier,
) {
    if (largeText) {
        Column(
            modifier = modifier.testTag("resonance.mode"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeKey("Chromatic", TunerMode.CHROMATIC, mode, onModeSelect, Modifier.fillMaxWidth())
            ModeKey("Instrument", TunerMode.INSTRUMENT, mode, onModeSelect, Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier.testTag("resonance.mode"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeKey("Chromatic", TunerMode.CHROMATIC, mode, onModeSelect, Modifier.weight(1f))
            ModeKey("Instrument", TunerMode.INSTRUMENT, mode, onModeSelect, Modifier.weight(1f))
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
                color = if (selected) p.ivory else p.textMuted,
            ),
            maxLines = 1,
        )
    }
}