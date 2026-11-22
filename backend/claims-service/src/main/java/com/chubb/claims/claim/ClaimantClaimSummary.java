package com.chubb.claims.claim;

import com.chubb.claims.policy.Market;
import com.chubb.claims.policy.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One claim in the claimant's "My claims" list.
 * Claimant-facing: internal figures (reserve, officer id) are deliberately left out, so they never leave the server.
 */
public record ClaimantClaimSummary(
        UUID id,
        String claimNumber,
        String policyNumber,
        ProductType productType,
        Market market,
        ClaimStatus status,
        LocalDate incidentDate,
        BigDecimal estimatedLoss,
        String currency,
        Instant submittedAt
) {

    /** Needs the policy loaded. */
    static ClaimantClaimSummary from(Claim c) {
        return new ClaimantClaimSummary(
                c.getId(),
                c.getClaimNumber(),
                c.getPolicy().getPolicyNumber(),
                c.getPolicy().getProductType(),
                c.getPolicy().getMarket(),
                c.getStatus(),
                c.getIncidentDate(),
                c.getEstimatedLoss(),
                c.getPolicy().getCurrency(),
                c.getSubmittedAt());
    }
}
