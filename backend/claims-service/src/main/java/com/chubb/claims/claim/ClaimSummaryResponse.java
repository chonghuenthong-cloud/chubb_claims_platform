package com.chubb.claims.claim;

import com.chubb.claims.policy.Market;
import com.chubb.claims.policy.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One claim in a list, for both the claimant's "My claims" and the staff work queue. */
public record ClaimSummaryResponse(
        UUID id,
        String claimNumber,
        String policyNumber,
        String claimantName,
        ProductType productType,
        Market market,
        ClaimStatus status,
        String assignedOfficerName,   // null while unassigned
        LocalDate incidentDate,
        BigDecimal estimatedLoss,
        BigDecimal reserveAmount,
        String currency,
        Instant submittedAt
) {

    /** Needs policy, claimant and officer loaded; callers fetch them with an entity graph. */
    static ClaimSummaryResponse from(Claim c) {
        return new ClaimSummaryResponse(
                c.getId(),
                c.getClaimNumber(),
                c.getPolicy().getPolicyNumber(),
                c.getClaimant().getFullName(),
                c.getPolicy().getProductType(),
                c.getPolicy().getMarket(),
                c.getStatus(),
                c.getAssignedOfficer() == null ? null : c.getAssignedOfficer().getFullName(),
                c.getIncidentDate(),
                c.getEstimatedLoss(),
                c.getReserveAmount(),
                c.getPolicy().getCurrency(),
                c.getSubmittedAt());
    }
}
