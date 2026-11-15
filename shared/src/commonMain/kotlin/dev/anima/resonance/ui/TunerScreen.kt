package dev.anima.resonance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anima.resonance.music.Spelling
import dev.anima.resonance.presentation.CustomEditorUiState
import dev.anima.resonance.presentation.MainScreenUiState
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.PresetRowUi
import dev.anima.resonance.presentation.TunerMode
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Main instrument screen. Renders the tested [MainScreenUiState] only:
 * no measurement logic lives here.
 *
 * Layout is one vertically scrollable instrument surface. A scrolling root is
 * deliberate: at 320 dp / large font sizes the note, dial, strings, mode and
 * the full tuning sheet cannot all fit at once; hiding the lower controls
 * behind a clipped, non-scrollable column was a real usability defect.
 *
 * Semantic/test tags: resonance.scroll, resonance.note, resonance.cents,
 * resonance.dial, resonance.direction, resonance.hint, resonance.micStatus,
 * resonance.mode(+.chromatic/.instrument), resonance.string.<i>,
 * resonance.settings, resonance.concert, resonance.permission, resonance.preset.
 */
@Composable
fun TunerScreen(
    state: MainScreenUiState,
    onOpenSettings: () -> Unit,
    onOpenPresets: () -> Unit,
    onModeSelect: (TunerMode) -> Unit,
    onStringSelect: (Int) -> Unit,
    onPermissionCta: () -> Unit,
    onRetryCapture: () -> Unit,
    onRequestPermission: () -> Unit = {},
    onConcertStep: (Boolean) -> Unit = {},
    onConcertStepCoarse: (Boolean) -> Unit = {},
    onPresetSelect: (String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onAddTuning: () -> Unit = {},
    onEditTuning: (String) -> Unit = {},
    onDeleteTuning: (String) -> Unit = {},
    onSpellingSelect: (Spelling) -> Unit = {},
    onImportRequested: () -> Unit = {},
    onExportRequested: () -> Unit = {},
    editor: CustomEditorUiState? = null,
    onEditorNameChange: (String) -> Unit = {},
    onEditorNoteCycle: (Int, Boolean) -> Unit = { _, _ -> },
    onEditorOctaveCycle: (Int, Boolean) -> Unit = { _, _ -> },
    onEditorRemoveString: (Int) -> Unit = {},
    onEditorAddString: () -> Unit = {},
    onEditorSave: () -> Unit = {},
    onEditorCancel: () -> Unit = {},
    showSettingsButton: Boolean = true,
    showPresetsButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val granted = state.permission == MicPermissionState.GRANTED
    val fontScale = LocalDensity.current.fontScale
    // Adaptive glyphs: system font scaling still applies, but an uncapped
    // 96sp note would overflow narrow screens at 2x accessibility text.
    val largeText = fontScale > 1.3f
    // Cap the *rendered* size, not the sp value: scaling from 96sp to 64sp
    // at 1.4x would make the note smaller as the user asks for larger text.
    val noteSize = (128f / fontScale).coerceAtMost(96f).sp
    val centsSize = (72f / fontScale).coerceAtMost(44f).sp
    val scrollState = rememberScrollState()
    val tuningsRequester = remember { BringIntoViewRequester() }
    val editorRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val editorOpen = editor != null
    val editorId = editor?.editingId
    LaunchedEffect(editorOpen, editorId) {
        if (editorOpen) {
            // Wait for the editor to be measured, then make it the focus of
            // the scroll surface (it is inserted above the long sheet).
            androidx.compose.runtime.withFrameNanos { }
            editorRequester.bringIntoView()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("resonance.scroll"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Header: tuning name + optional settings ───────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.presetName ?: "Chromatic",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("resonance.preset")
                    .semantics { heading() },
            )
            if (state.presetRows.isNotEmpty()) {
                TextButton(
                    onClick = { scope.launch { tuningsRequester.bringIntoView() } },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("resonance.openTunings"),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text("Tunings") }
            }
            if (showSettingsButton) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("resonance.settings"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) { Text("Settings") }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Live capture state + reference pitch ──────────────────────────
        if (largeText) {
            Column(Modifier.fillMaxWidth()) {
                MicStatusLine(
                    text = state.micStatusText,
                    isError = state.captureError,
                    modifier = Modifier.fillMaxWidth(),
                )
                ConcertPitchText(
                    hz = state.concertPitchHz,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicStatusLine(
                    text = state.micStatusText,
                    isError = state.captureError,
                    modifier = Modifier.weight(1f),
                )
                ConcertPitchText(hz = state.concertPitchHz)
            }
        }

        // ── Permission recovery (first run / soft denial / permanent) ─────
        if (state.permissionCtaVisible) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resonance.permission.banner"),
                shape = RoundedCornerShape(14.dp),
                color = scheme.surfaceVariant,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "Microphone access",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.permissionHint.orEmpty(),
                        style = ResonanceType.hint,
                        color = scheme.onSurface,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (state.permissionCtaOpensSettings) {
                                onPermissionCta()
                            } else {
                                onRequestPermission()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("resonance.permission"),
                    ) {
                        Text(state.permissionCtaText)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Signal / capture guidance, directly under the mic state ───────
        // First-time clarity: the instruction belongs near "Listening", not
        // below a large dial the user has not learned to read yet.
        state.statusHint?.let { hint ->
            Text(
                text = hint,
                style = ResonanceType.hint,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resonance.hint"),
            )
        }
        if (state.captureError) {
            Button(
                onClick = onRetryCapture,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 48.dp)
                    .testTag("resonance.retry"),
            ) { Text("Retry microphone") }
        } else if (granted && !state.captureActive) {
            Button(
                onClick = onRetryCapture,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 48.dp)
                    .testTag("resonance.start"),
            ) { Text("Start listening") }
        }

        // Without permission (or with a failed device) there is no
        // measurement to show. Keeping a dead dial on screen implied a live
        // instrument.
        if (granted && !state.captureError) {
            Spacer(Modifier.height(12.dp))

            // ── Detected note + frequency (stable reserved height) ────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 126.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.noteLabel != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = state.noteLabel,
                                style = ResonanceType.noteGlyph.copy(fontSize = noteSize),
                                maxLines = 1,
                                modifier = Modifier
                                    .testTag("resonance.note")
                                    .semantics {
                                        contentDescription =
                                            (if (state.mode == TunerMode.INSTRUMENT) {
                                                "Target note"
                                            } else {
                                                "Detected note"
                                            }) + " ${state.noteLabel}${state.octave ?: ""}"
                                        liveRegion = LiveRegionMode.Polite
                                    },
                            )
                            state.octave?.let {
                                Text(
                                    text = it,
                                    style = ResonanceType.octave,
                                    modifier = Modifier
                                        .padding(start = 4.dp, bottom = 22.dp)
                                        .clearAndSetSemantics {},
                                )
                            }
                        }
                        state.frequencyText?.let {
                            Text(
                                text = it,
                                style = ResonanceType.frequency,
                                modifier = Modifier.testTag("resonance.frequency"),
                            )
                        }
                    }
                }
            }

            // ── Cents dial ────────────────────────────────────────────────
            CentsDial(
                position = state.meterPosition,
                inTune = state.inTune,
                clamped = state.meterClamped,
                active = state.meterActive,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "FLAT",
                    style = ResonanceType.labelSmall,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Text(
                    "SHARP",
                    style = ResonanceType.labelSmall,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }

            Spacer(Modifier.height(2.dp))

            val centsDescription = when {
                !state.meterActive || state.centsText == null -> "No current reading"
                state.inTune -> "In tune"
                state.directionText == "Tune up" ->
                    "${abs(state.centsText.toIntOrNull() ?: 0)} cents flat, tune up"
                else ->
                    "${abs(state.centsText.toIntOrNull() ?: 0)} cents sharp, tune down"
            }
            if (state.meterActive && state.centsText != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.centsText,
                        style = ResonanceType.cents.copy(fontSize = centsSize),
                        modifier = Modifier
                            .testTag("resonance.cents")
                            .semantics { contentDescription = centsDescription },
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "cents",
                        style = ResonanceType.labelSmall,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            } else {
                // A small muted dash, not a giant white line that can read
                // as "0 cents" at a glance.
                Text(
                    text = "—",
                    style = ResonanceType.frequency.copy(color = scheme.onSurfaceVariant),
                    modifier = Modifier
                        .testTag("resonance.cents")
                        .semantics { contentDescription = centsDescription },
                )
            }
            state.directionText?.let { direction ->
                Text(
                    text = direction,
                    style = ResonanceType.direction.copy(
                        color = if (state.inTune) scheme.secondary else scheme.onSurface,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .testTag("resonance.direction")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Target strings (Instrument mode) ──────────────────────────────
        if (state.targetLabels.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Six 48 dp targets cannot fit one narrow row. Use a 3×2
                // grid on phones (each target ~90 dp wide) and keep a single
                // low-to-high row on wider surfaces.
                val perRow = if (maxWidth < 480.dp || state.targetLabels.size > 6) 3 else 6
                val chunks = state.targetLabels.withIndex().toList().chunked(perRow)
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
                                val active = state.activeStringIndex == i
                                val locked = active && state.stringLocked
                                Surface(
                                    onClick = { onStringSelect(i) },
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
                                    shape = RoundedCornerShape(10.dp),
                                    color = when {
                                        locked -> scheme.primary.copy(alpha = 0.16f)
                                        active -> scheme.primary.copy(alpha = 0.08f)
                                        else -> scheme.surface
                                    },
                                    border = when {
                                        locked -> BorderStroke(2.dp, scheme.primary)
                                        active -> BorderStroke(1.dp, scheme.primary)
                                        else -> null
                                    },
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = label,
                                            style = ResonanceType.labelSmall.copy(
                                                color = if (active) scheme.primary
                                                else scheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            maxLines = 1,
                                        )
                                        if (active) {
                                            Text(
                                                text = if (locked) "LOCKED" else "AUTO",
                                                style = ResonanceType.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    color = scheme.primary,
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
                text = if (state.selectedStringIndex != null) {
                    "Locked to one string. Tap it again for automatic selection."
                } else {
                    "Tap a string to lock its target; automatic selection is on."
                },
                style = ResonanceType.hint,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .testTag("resonance.stringHint"),
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Mode: explicit two-option segmented control ───────────────────
        if (largeText) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resonance.mode"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeOption(
                    label = "Chromatic",
                    selected = state.mode == TunerMode.CHROMATIC,
                    onClick = { onModeSelect(TunerMode.CHROMATIC) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resonance.mode.chromatic"),
                )
                ModeOption(
                    label = "Instrument",
                    selected = state.mode == TunerMode.INSTRUMENT,
                    onClick = { onModeSelect(TunerMode.INSTRUMENT) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resonance.mode.instrument"),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resonance.mode"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeOption(
                    label = "Chromatic",
                    selected = state.mode == TunerMode.CHROMATIC,
                    onClick = { onModeSelect(TunerMode.CHROMATIC) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("resonance.mode.chromatic"),
                )
                ModeOption(
                    label = "Instrument",
                    selected = state.mode == TunerMode.INSTRUMENT,
                    onClick = { onModeSelect(TunerMode.INSTRUMENT) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("resonance.mode.instrument"),
                )
            }
        }

        if (state.mode == TunerMode.CHROMATIC) {
            Text(
                text = "Chromatic listens to every note. Instrument mode targets the selected tuning.",
                style = ResonanceType.hint,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Optional external "Tunings" entry point (tests/hosts) ─────────
        if (showPresetsButton) {
            OutlinedButton(
                onClick = onOpenPresets,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("resonance.presets"),
            ) { Text("Tunings") }
            Spacer(Modifier.height(10.dp))
        }

        // ── Custom editor (above the sheet) ───────────────────────────────
        editor?.let { form ->
            CustomEditorSheet(
                state = form,
                onNameChange = onEditorNameChange,
                onNoteCycle = onEditorNoteCycle,
                onOctaveCycle = onEditorOctaveCycle,
                onRemoveString = onEditorRemoveString,
                onAddString = onEditorAddString,
                onSave = onEditorSave,
                onCancel = onEditorCancel,
                modifier = Modifier
                    .bringIntoViewRequester(editorRequester)
                    .padding(bottom = 12.dp),
            )
        }

        // ── Tunings + reference settings sheet ────────────────────────────
        if (state.presetRows.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resonance.sheet"),
                shape = RoundedCornerShape(14.dp),
                color = scheme.surface,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "Tunings & settings",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .bringIntoViewRequester(tuningsRequester)
                            .semantics { heading() }
                            .testTag("resonance.sheet.title"),
                    )

                    // Coarse ±1 Hz + fine ±0.1 Hz. The original UI offered
                    // only 0.1 Hz steps, so 440 → 432 Hz took 80 taps.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resonance.sheet.concert"),
                    ) {
                        if (largeText) {
                            Text(state.concertLabel, style = ResonanceType.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) { CoarseConcertButtons(onConcertStepCoarse) }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = state.concertLabel,
                                    style = ResonanceType.labelSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                CoarseConcertButtons(onConcertStepCoarse)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            FineConcertButtons(onConcertStep)
                        }
                    }
                    state.concertError?.let { error ->
                        Text(
                            text = error,
                            color = scheme.error,
                            style = ResonanceType.hint,
                            modifier = Modifier.testTag("resonance.sheet.concertError"),
                        )
                    }

                    if (largeText) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .testTag("resonance.sheet.spelling"),
                        ) {
                            Text("Accidentals", style = ResonanceType.labelSmall)
                            Spacer(Modifier.size(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                SpellingButtons(state.accidentalSpelling, onSpellingSelect)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .testTag("resonance.sheet.spelling"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Accidentals",
                                style = ResonanceType.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            SpellingButtons(state.accidentalSpelling, onSpellingSelect)
                        }
                    }

                    state.presetRows.forEach { row ->
                        Surface(
                            onClick = { onPresetSelect(row.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .testTag("resonance.sheet.row.${row.id}")
                                .semantics {
                                    contentDescription = "Tuning ${row.name}" +
                                        (if (row.selected) ", selected" else "") +
                                        (if (row.favorite) ", favorite" else "")
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (row.selected) scheme.primary.copy(alpha = 0.12f)
                            else scheme.surface,
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = row.name,
                                        style = ResonanceType.labelSmall.copy(
                                            color = scheme.onSurface,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (row.selected) {
                                        Text(
                                            text = "●",
                                            color = scheme.primary,
                                            style = ResonanceType.labelSmall,
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .clearAndSetSemantics {},
                                        )
                                    }
                                    TextButton(
                                        onClick = { onToggleFavorite(row.id) },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("resonance.sheet.row.${row.id}.favorite")
                                            .semantics {
                                                contentDescription = if (row.favorite) {
                                                    "Remove ${row.name} from favorites"
                                                } else {
                                                    "Add ${row.name} to favorites"
                                                }
                                            },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Text(
                                            text = if (row.favorite) "★" else "☆",
                                            color = if (row.favorite) scheme.primary else scheme.onSurfaceVariant,
                                            fontSize = 18.sp,
                                        )
                                    }
                                }
                                if (largeText) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(row.stringsPreview, style = ResonanceType.hint)
                                        if (!row.builtIn) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                            ) {
                                                RowEditDelete(row, onEditTuning, onDeleteTuning)
                                            }
                                        }
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = row.stringsPreview,
                                            style = ResonanceType.hint,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (!row.builtIn) {
                                            RowEditDelete(row, onEditTuning, onDeleteTuning)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (largeText) {
                        // Three equal-width actions cannot show full labels at
                        // 2x text; use one full-width action + a two-up row.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SheetAction("+ New", "resonance.sheet.add", onAddTuning, Modifier.fillMaxWidth())
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SheetAction("Import", "resonance.sheet.import", onImportRequested, Modifier.weight(1f))
                                SheetAction("Export", "resonance.sheet.export", onExportRequested, Modifier.weight(1f))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SheetAction("+ New", "resonance.sheet.add", onAddTuning, Modifier.weight(1f))
                            SheetAction("Import", "resonance.sheet.import", onImportRequested, Modifier.weight(1f))
                            SheetAction("Export", "resonance.sheet.export", onExportRequested, Modifier.weight(1f))
                        }
                    }
                    state.importResultText?.let { message ->
                        Text(
                            text = message,
                            style = ResonanceType.hint,
                            color = scheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .testTag("resonance.sheet.importResult")
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RowEditDelete(
    row: PresetRowUi,
    onEditTuning: (String) -> Unit,
    onDeleteTuning: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    TextButton(
        onClick = { onEditTuning(row.id) },
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("resonance.sheet.row.${row.id}.edit")
            .semantics { contentDescription = "Edit ${row.name}" },
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) { Text("Edit") }
    TextButton(
        onClick = { onDeleteTuning(row.id) },
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("resonance.sheet.row.${row.id}.delete")
            .semantics { contentDescription = "Delete ${row.name}" },
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) { Text("Delete", color = scheme.error) }
}

@Composable
private fun MicStatusLine(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val dotColor = when {
        isError -> MaterialTheme.colorScheme.error
        text == "Listening" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = ResonanceType.hint,
            modifier = Modifier
                .weight(1f)
                .testTag("resonance.micStatus"),
        )
    }
}

@Composable
private fun ConcertPitchText(
    hz: Double,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "A4 ${hz.toIntIfWholeOrOneDecimal()} Hz",
        style = ResonanceType.labelSmall,
        modifier = modifier
            .testTag("resonance.concert")
            .semantics {
                contentDescription =
                    "Reference pitch A4 ${hz.toIntIfWholeOrOneDecimal()} hertz"
            },
    )
}

@Composable
private fun CoarseConcertButtons(onConcertStep: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = { onConcertStep(false) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("resonance.sheet.concert.coarseMinus")
                .semantics { contentDescription = "Decrease A4 reference by 1 hertz" },
        ) { Text("−1") }
        OutlinedButton(
            onClick = { onConcertStep(true) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("resonance.sheet.concert.coarsePlus")
                .semantics { contentDescription = "Increase A4 reference by 1 hertz" },
        ) { Text("+1") }
    }
}

@Composable
private fun FineConcertButtons(onConcertStep: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = { onConcertStep(false) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("resonance.sheet.concert.minus")
                .semantics { contentDescription = "Decrease A4 reference by 0.1 hertz" },
        ) { Text("−0.1") }
        OutlinedButton(
            onClick = { onConcertStep(true) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("resonance.sheet.concert.plus")
                .semantics { contentDescription = "Increase A4 reference by 0.1 hertz" },
        ) { Text("+0.1") }
    }
}

@Composable
private fun SpellingButtons(
    selected: Spelling,
    onSpellingSelect: (Spelling) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SpellingOption(
            label = "♯",
            description = "Sharp note names",
            selected = selected == Spelling.SHARP,
            onClick = { onSpellingSelect(Spelling.SHARP) },
            modifier = Modifier.testTag("resonance.sheet.spelling.sharp"),
        )
        SpellingOption(
            label = "♭",
            description = "Flat note names",
            selected = selected == Spelling.FLAT,
            onClick = { onSpellingSelect(Spelling.FLAT) },
            modifier = Modifier.testTag("resonance.sheet.spelling.flat"),
        )
    }
}

@Composable
private fun SheetAction(
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(text, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = "$label mode"
                this.selected = selected
            },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) scheme.primary else scheme.outlineVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (selected) "✓ $label" else label,
                style = ResonanceType.labelSmall.copy(
                    color = if (selected) scheme.primary else scheme.onSurface,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SpellingOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = description
                stateDescription = if (selected) "Selected" else "Not selected"
                this.selected = selected
            },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) scheme.primary else scheme.outlineVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (selected) "✓ $label" else label,
                style = ResonanceType.labelSmall.copy(
                    color = if (selected) scheme.primary else scheme.onSurface,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }
    }
}

private fun Double.toIntIfWholeOrOneDecimal(): String {
    val tenths = (this * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}