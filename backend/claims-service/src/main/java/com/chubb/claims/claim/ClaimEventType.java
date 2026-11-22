package com.chubb.claims.claim;

/** Kinds of claim events published to Kafka (topic "claim-events"). */
public enum ClaimEventType {
    /** A claimant reported an incident. */
    CLAIM_SUBMITTED,
    /** Any status change: pick-up, info requested, answered, approved, rejected, settled. */
    CLAIM_STATUS_CHANGED,
    /** The assigned officer changed the reserve without a status change. Internal: never shown to claimants. */
    RESERVE_REVISED
}
