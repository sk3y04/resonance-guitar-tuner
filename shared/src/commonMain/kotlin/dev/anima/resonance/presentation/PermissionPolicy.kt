package dev.anima.resonance.presentation

/**
 * Pure microphone-permission policy.
 *
 * Android reports `shouldShowRequestPermissionRationale == false` both for a
 * first-run app and for a permanent denial. The shell persists whether it has
 * ever requested the permission; [decide] then makes the distinction explicit
 * and testable. The lifecycle rule is simple:
 *
 * - granted → use the microphone;
 * - denied but a rationale is available → asking again is legitimate;
 * - never asked → ask once;
 * - asked before, no rationale → permanent denial: route to app settings,
 *   never re-prompt.
 */
enum class PermissionDecision { GRANTED, REQUEST, PERMANENT_DENIAL }

object PermissionPolicy {

    fun decide(
        hasPermission: Boolean,
        shouldShowRationale: Boolean,
        requestedBefore: Boolean,
    ): PermissionDecision = when {
        hasPermission -> PermissionDecision.GRANTED
        shouldShowRationale -> PermissionDecision.REQUEST
        !requestedBefore -> PermissionDecision.REQUEST
        else -> PermissionDecision.PERMANENT_DENIAL
    }

    /** Maps a decision to the UI-facing permission state. */
    fun toMicState(
        decision: PermissionDecision,
        hasPermission: Boolean,
        requestedBefore: Boolean,
    ): MicPermissionState = when (decision) {
        PermissionDecision.GRANTED ->
            if (hasPermission) MicPermissionState.GRANTED else MicPermissionState.NOT_REQUESTED
        PermissionDecision.REQUEST ->
            if (requestedBefore) MicPermissionState.DENIED else MicPermissionState.NOT_REQUESTED
        PermissionDecision.PERMANENT_DENIAL -> MicPermissionState.DENIED_PERMANENT
    }
}