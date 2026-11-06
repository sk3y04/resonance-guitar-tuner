package dev.anima.resonance.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import dev.anima.resonance.engine.ResonanceViewModel
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.ui.MainScreen

/**
 * Android shell entry point. Renders the tested common [MainScreen]
 * against the [ResonanceViewModel] and enforces the microphone permission
 * policy:
 *
 *  - granted → start capture;
 *  - not requested yet → request it once;
 *  - denied with rationale → request (the user may change their mind);
 *  - permanently denied (no rationale offered) → DENIED_PERMANENT: the UI
 *    shows the "open app settings" CTA and the app never re-prompts.
 *
 * Status: WRITTEN — COMPILE VERIFICATION BLOCKED (SDK platform /
 * licenses not accepted → android target auto-disabled). No device testing
 * yet.
 */
class ResonanceApp : ComponentActivity() {

    private val viewModel: ResonanceViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val controller = viewModel.controller
            if (granted) {
                controller.onPermissionResult(MicPermissionState.GRANTED)
                viewModel.start()
            } else {
                controller.onPermissionResult(
                    if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
                        MicPermissionState.DENIED
                    else MicPermissionState.DENIED_PERMANENT
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    controller = viewModel.controller,
                    onPermissionCta = { openAppSettings() },
                )
            }
        }
        applyPermissionState(hasRecordAudio())
    }

    private fun hasRecordAudio(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Maps the platform permission state onto the controller and starts
     * capture only when granted.
     */
    private fun applyPermissionState(granted: Boolean) {
        val controller = viewModel.controller
        if (granted) {
            controller.onPermissionResult(MicPermissionState.GRANTED)
            viewModel.start()
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            controller.onPermissionResult(MicPermissionState.DENIED)
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            // First run without rationale: ask once; afterwards, permanent.
            controller.onPermissionResult(MicPermissionState.NOT_REQUESTED)
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            },
        )
    }
}
