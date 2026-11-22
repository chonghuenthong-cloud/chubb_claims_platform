package com.chubb.claims.claim;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One claim in full: the list-row fields, the incident and decision details, the status history
 * and the information requests. Used by the staff detail page and the claimant's answer page.
 */
public record ClaimDetailResponse(
        ClaimSummaryResponse summary,
        String incidentDescription,
        BigDecimal coverageLimit,      // ceiling for reserve and approved amount
        BigDecimal approvedAmount,     // null until approved
        String rejectionReason,        // null unless rejected
        List<StatusChange> history,    // oldest first
        List<InfoRequest> informationRequests  // oldest first
) {

    /** One entry in the audit trail. */
    public record StatusChange(ClaimStatus fromStatus, ClaimStatus toStatus, String changedByName,
                               String note, Instant changedAt) {
    }

    /** A question from the officer and, once given, the claimant's answer. */
    public record InfoRequest(UUID id, String question, String requestedByName, Instant requestedAt,
                              String response, Instant respondedAt) {
    }

    /** Needs policy, claimant, officer and history loaded; information requests load lazily in the same transaction. */
    static ClaimDetailResponse from(Claim c) {
        List<StatusChange> history = c.getHistory().stream()
                .map(h -> new StatusChange(h.getFromStatus(), h.getToStatus(), h.getChangedBy().getFullName(),
                        h.getNote(), h.getChangedAt()))
                .toList();
        List<InfoRequest> requests = c.getInformationRequests().stream()
                .map(r -> new InfoRequest(r.getId(), r.getQuestion(), r.getRequestedBy().getFullName(),
                        r.getRequestedAt(), r.getResponse(), r.getRespondedAt()))
                .toList();
        return new ClaimDetailResponse(ClaimSummaryResponse.from(c), c.getIncidentDescription(),
                c.getPolicy().getCoverageLimit(), c.getApprovedAmount(), c.getRejectionReason(), history, requests);
    }
}
