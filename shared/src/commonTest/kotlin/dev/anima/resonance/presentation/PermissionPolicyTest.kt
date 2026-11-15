package dev.anima.resonance.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Android's permission API cannot distinguish "never asked" from "asked and
 * permanently denied": both return `shouldShowRequestPermissionRationale ==
 * false`. Persisting a "requested before" flag makes the policy explicit and
 * testable — the app must never re-prompt after a permanent denial.
 */
class PermissionPolicyTest {

    @Test
    fun grantedWinsRegardlessOfHistory() {
        assertEquals(
            PermissionDecision.GRANTED,
            PermissionPolicy.decide(hasPermission = true, shouldShowRationale = false, requestedBefore = true),
        )
        assertEquals(
            PermissionDecision.GRANTED,
            PermissionPolicy.decide(hasPermission = true, shouldShowRationale = true, requestedBefore = false),
        )
    }

    @Test
    fun firstRunRequestsOnce() {
        assertEquals(
            PermissionDecision.REQUEST,
            PermissionPolicy.decide(hasPermission = false, shouldShowRationale = false, requestedBefore = false),
        )
    }

    @Test
    fun softDenialCanBeRequestedAgain() {
        // The user denied but Android still offers the rationale: a retry is
        // allowed, and the UI must surface it instead of dead-ending.
        assertEquals(
            PermissionDecision.REQUEST,
            PermissionPolicy.decide(hasPermission = false, shouldShowRationale = true, requestedBefore = true),
        )
        assertEquals(
            MicPermissionState.DENIED,
            PermissionPolicy.toMicState(
                PermissionDecision.REQUEST,
                hasPermission = false,
                requestedBefore = true,
            ),
        )
    }

    @Test
    fun deniedWithoutRationaleAfterARequestIsPermanent() {
        assertEquals(
            PermissionDecision.PERMANENT_DENIAL,
            PermissionPolicy.decide(hasPermission = false, shouldShowRationale = false, requestedBefore = true),
        )
        assertEquals(
            MicPermissionState.DENIED_PERMANENT,
            PermissionPolicy.toMicState(
                PermissionDecision.PERMANENT_DENIAL,
                hasPermission = false,
                requestedBefore = true,
            ),
        )
    }

    @Test
    fun firstRunStateIsNotReportedAsPermanent() {
        assertEquals(
            MicPermissionState.NOT_REQUESTED,
            PermissionPolicy.toMicState(
                PermissionDecision.REQUEST,
                hasPermission = false,
                requestedBefore = false,
            ),
        )
    }
}