package com.chubb.claims.claim;

import com.chubb.claims.claim.ClaimDetailResponse.InfoRequest;
import com.chubb.claims.claim.ClaimDetailResponse.StatusChange;

import java.math.BigDecimal;
import java.util.List;

/**
 * One claim as the claimant sees it: status, decision, timeline and questions.
 * Leaves out the reserve and the officer's internal id. Shows the officer's name, the policy's coverage limit,
 * and the timeline with staff names and notes (the notes are the info questions and rejection reasons,
 * which are written for the claimant).
 */
public record ClaimantClaimDetail(
        ClaimantClaimSummary summary,
        String incidentDescription,
        BigDecimal coverageLimit,
        String assignedOfficerName,    // null while unassigned
        BigDecimal approvedAmount,     // null until approved
        String rejectionReason,        // null unless rejected
        List<StatusChange> history,    // oldest first
        List<InfoRequest> informationRequests  // oldest first
) {

    /** Needs policy, officer and history loaded; information requests load lazily in the same transaction. */
    static ClaimantClaimDetail from(Claim c) {
        return new ClaimantClaimDetail(
                ClaimantClaimSummary.from(c),
                c.getIncidentDescription(),
                c.getPolicy().getCoverageLimit(),
                c.getAssignedOfficer() == null ? null : c.getAssignedOfficer().getFullName(),
                c.getApprovedAmount(),
                c.getRejectionReason(),
                ClaimDetailResponse.historyOf(c),
                ClaimDetailResponse.requestsOf(c));
    }
}
