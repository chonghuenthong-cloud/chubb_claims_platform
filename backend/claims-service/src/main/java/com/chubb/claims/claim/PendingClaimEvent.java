package com.chubb.claims.claim;

import com.chubb.claims.user.AppUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened to a claim, recorded by the Claim itself and not yet saved to the outbox.
 * The full event (with the claim id, which a new claim only gets when saved) is built by OutboxWriter.
 *
 * @param fromStatus    null for CLAIM_SUBMITTED; equal to toStatus for RESERVE_REVISED
 * @param note          the info question or rejection reason, if any
 * @param reserveBefore reserve before the change (0 for a new claim); with reserveAfter, lets consumers
 *                      keep exposure totals from events alone
 */
public record PendingClaimEvent(
        UUID eventId,
        ClaimEventType type,
        Instant occurredAt,
        AppUser actor,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        String note,
        BigDecimal reserveBefore,
        BigDecimal reserveAfter
) {
}
