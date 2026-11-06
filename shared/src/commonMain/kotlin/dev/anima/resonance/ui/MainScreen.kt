package dev.anima.resonance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.anima.resonance.engine.TunerController
import dev.anima.resonance.presentation.TunerMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MainScreen: the host composable — a [TunerController] in, a
 * rendered instrument screen out.
 *
 * The ticker re-reads the immutable UI state (the engine is pull-based and
 * the read is cheap); mutations go through the controller and refresh
 * immediately so taps never wait for the next tick. The platform shell
 * (Android Activity) starts/stops capture via the controller lifecycle;
 * [onPermissionCta] is the hook that opens system app settings.
 *
 * The inline settings/preset sheet replaces the old Settings and
 * Tunings buttons — they are hidden here (TunerScreen keeps them, tested,
 * for state-driven rendering).
 */
@Composable
fun MainScreen(
    controller: TunerController,
    modifier: Modifier = Modifier,
    refreshMs: Long = 50,
    onPermissionCta: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var ui by remember(controller) { mutableStateOf(controller.state()) }

    LaunchedEffect(controller) {
        while (isActive) {
            ui = controller.state()
            delay(refreshMs)
        }
    }

    fun refresh() {
        ui = controller.state()
    }

    TunerScreen(
        state = ui,
        onOpenSettings = {},
        onOpenPresets = {},
        onModeToggle = {
            val next = if (ui.mode == TunerMode.CHROMATIC) TunerMode.INSTRUMENT else TunerMode.CHROMATIC
            scope.launch {
                controller.setMode(next)
                refresh()
            }
        },
        onStringSelect = { index ->
            controller.selectString(index)
            refresh()
        },
        onPermissionCta = onPermissionCta,
        onRetryCapture = {
            scope.launch {
                controller.retryCapture()
                refresh()
            }
        },
        onPresetSelect = { id ->
            scope.launch {
                controller.setPreset(id)
                refresh()
            }
        },
        onConcertStep = { up ->
            scope.launch {
                controller.stepConcertPitch(up)
                refresh()
            }
        },
        showSettingsButton = false,
        showPresetsButton = false,
        modifier = modifier,
    )
}
