package dev.anima.resonance.ui.instrument

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.anima.resonance.presentation.TunerMode

/**
 * The tuning mode as TWO keys in one recessed track (not a dark tray, not
 * two floating buttons). The active mode is a raised walnut cap with a brass
 * indicator bar and a brighter bold label — unmistakable, never carried by the
 * underline or colour alone (`selected` semantics + cap shape + text weight).
 *
 * The control sits in the same place in both modes: first row of the deck.
 */
@Composable
fun MechanicalModeSelector(
    mode: TunerMode,
    onModeSelect: (TunerMode) -> Unit,
    @Suppress("UNUSED_PARAMETER") largeText: Boolean,
    modifier: Modifier = Modifier,
) {
    val chromatic = mode == TunerMode.CHROMATIC
    SegmentTrack(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resonance.mode"),
        height = ControlDeckLayout.MODE_ROW_DP.dp,
        // The mode selector sits in a shallow milled track — the one
        // recessed control on the faceplate. Keys keep their 48dp height.
        recessed = true,
    ) {
        InstrumentSegment(
            label = "Chromatic",
            selected = chromatic,
            onClick = { onModeSelect(TunerMode.CHROMATIC) },
            tag = "resonance.mode.chromatic",
            description = "Chromatic mode",
        )
        InstrumentSegment(
            label = "Instrument",
            selected = !chromatic,
            onClick = { onModeSelect(TunerMode.INSTRUMENT) },
            tag = "resonance.mode.instrument",
            description = "Instrument mode",
        )
    }
}