package dev.anima.resonance.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import dev.anima.resonance.engine.ResonanceViewModel
import dev.anima.resonance.presentation.MicPermissionState
import dev.anima.resonance.presentation.PermissionDecision
import dev.anima.resonance.presentation.PermissionPolicy
import dev.anima.resonance.ui.MainScreen
import dev.anima.resonance.ui.ResonanceTheme

/**
 * Android shell entry point. Renders the tested common [MainScreen] against
 * the [ResonanceViewModel] and enforces the microphone permission/lifecycle
 * policy:
 *
 *  - granted → start capture while the screen is started;
 *  - first run → explain, then request once from a real button (the flag is
 *    persisted; Android cannot distinguish "never asked" from "permanently
 *    denied" without it);
 *  - soft denial → explain and offer a real "Allow microphone" retry;
 *  - permanent denial → "Open app settings" and never re-prompt;
 *  - leaving the foreground stops and releases capture; returning re-checks
 *    the permission and restarts only when granted.
 *
 * Status: host compile/Robolectric verified; on-device behavior remains
 * unverified on real hardware (no emulator/phone in this environment).
 */
class ResonanceApp : ComponentActivity() {

    private val viewModel: ResonanceViewModel by viewModels()

    private val permissionRequestedPrefs by lazy {
        getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            markPermissionRequested()
            val controller = viewModel.controller
            if (granted) {
                controller.onPermissionResult(MicPermissionState.GRANTED)
                viewModel.start()
            } else {
                controller.onPermissionResult(
                    if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                        MicPermissionState.DENIED
                    } else {
                        MicPermissionState.DENIED_PERMANENT
                    },
                )
            }
        }

    // Tuning-file import/export via the document picker (SAF).
    // Read-only access by contract — no storage permission is ever
    // requested; the picked URI is handed to the ViewModel, which stages
    // the payload for the tested common import path.
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.onImportUri(it) }
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { viewModel.onExportUri(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResonanceTheme {
                MainScreen(
                    controller = viewModel.controller,
                    onPermissionCta = { openAppSettings() },
                    onRequestPermission = { requestMicrophonePermission() },
                    onImportPicker = {
                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    onExportPicker = {
                        exportLauncher.launch("resonance-tunings.json")
                    },
                    importPayload = viewModel.importPayload.collectAsState(initial = null).value,
                    onImportConsumed = { viewModel.consumeImport() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshPermissionAndCapture()
    }

    override fun onStop() {
        // Spec lifecycle rule: capture runs only while the foreground tuner
        // surface is active. Also releases the AudioRecord before the system
        // can keep the mic hot in the background.
        viewModel.stop()
        super.onStop()
    }

    private fun hasRecordAudio(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun permissionRequestedBefore(): Boolean =
        permissionRequestedPrefs.getBoolean(KEY_PERMISSION_REQUESTED, false)

    private fun markPermissionRequested() {
        permissionRequestedPrefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
    }

    private fun requestMicrophonePermission() {
        markPermissionRequested()
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    /**
     * Re-checks the platform permission on every foreground start. The
     * persisted request flag is what prevents an endless prompt loop after a
     * permanent denial.
     *
     * The first request is deliberately user-initiated: the screen shows an
     * explanatory banner with a real "Allow microphone" button instead of
     * throwing a system dialog at a first-time user. Soft denials use the
     * same retry path; permanent denial routes to app settings.
     */
    private fun refreshPermissionAndCapture() {
        val controller = viewModel.controller
        val granted = hasRecordAudio()
        val requestedBefore = permissionRequestedBefore()
        val rationale = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        val decision = PermissionPolicy.decide(
            hasPermission = granted,
            shouldShowRationale = rationale,
            requestedBefore = requestedBefore,
        )
        controller.onPermissionResult(
            PermissionPolicy.toMicState(decision, granted, requestedBefore),
        )
        when (decision) {
            PermissionDecision.GRANTED -> viewModel.start()
            PermissionDecision.REQUEST,
            PermissionDecision.PERMANENT_DENIAL,
            -> Unit // the banner CTA requests or opens app settings
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
            },
        )
    }

    private companion object {
        const val PERMISSION_PREFS = "resonance_permissions"
        const val KEY_PERMISSION_REQUESTED = "mic_requested"
    }
}