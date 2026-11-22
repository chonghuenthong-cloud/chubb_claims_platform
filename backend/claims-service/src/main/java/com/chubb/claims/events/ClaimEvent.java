package com.chubb.claims.events;

import com.chubb.claims.claim.Claim;
import com.chubb.claims.claim.ClaimEventType;
import com.chubb.claims.claim.ClaimStatus;
import com.chubb.claims.claim.PendingClaimEvent;
import com.chubb.claims.policy.Market;
import com.chubb.claims.policy.ProductType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * A claim event as published to Kafka (topic "claim-events", key = claim id), as JSON.
 * Self-contained, so consumers never need to call back into the database to understand it.
 * Internal: includes the reserve, so it must never be passed to claimants as-is.
 */
public record ClaimEvent(
        UUID eventId,
        ClaimEventType type,
        Instant occurredAt,
        UUID claimId,
        String claimNumber,
        UUID claimantId,
        UUID actorId,
        String actorName,
        ClaimStatus fromStatus,      // null for CLAIM_SUBMITTED
        ClaimStatus toStatus,
        String note,                 // info question or rejection reason, if any
        String currency,
        Market market,
        ProductType productType,
        BigDecimal reserveBefore,
        BigDecimal reserveAfter,
        BigDecimal approvedAmount    // null until approved
) {

    /** Combines what happened (recorded by the Claim) with the claim's identity, known once it is saved. */
    static ClaimEvent of(Claim claim, PendingClaimEvent e) {
        return new ClaimEvent(e.eventId(), e.type(), e.occurredAt(),
                claim.getId(), claim.getClaimNumber(), claim.getClaimant().getId(),
                e.actor().getId(), e.actor().getFullName(),
                e.fromStatus(), e.toStatus(), e.note(),
                claim.getPolicy().getCurrency(), claim.getPolicy().getMarket(), claim.getPolicy().getProductType(),
                money(e.reserveBefore()), money(e.reserveAfter()), money(claim.getApprovedAmount()));
    }

    /**
     * Every amount in an event has 2 decimals ("1500.00"), whether it came from the database or a request,
     * so all consumers see one consistent format.
     */
    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
