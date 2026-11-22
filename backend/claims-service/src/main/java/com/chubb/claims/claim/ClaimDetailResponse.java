package com.chubb.claims.claim;

import java.time.Instant;
import java.util.List;

/**
 * One claim in full: the list-row fields, the incident description, and the status history.
 * The base for claim tracking; information requests will be added when that feature is built.
 */
public record ClaimDetailResponse(
        ClaimSummaryResponse summary,
        String incidentDescription,
        List<StatusChange> history   // oldest first
) {

    /** One entry in the audit trail. */
    public record StatusChange(ClaimStatus fromStatus, ClaimStatus toStatus, String changedByName,
                               String note, Instant changedAt) {
    }

    /** Needs policy, claimant, officer and history (with who changed it) loaded; see ClaimRepository. */
    static ClaimDetailResponse from(Claim c) {
        List<StatusChange> history = c.getHistory().stream()
                .map(h -> new StatusChange(h.getFromStatus(), h.getToStatus(), h.getChangedBy().getFullName(),
                        h.getNote(), h.getChangedAt()))
                .toList();
        return new ClaimDetailResponse(ClaimSummaryResponse.from(c), c.getIncidentDescription(), history);
    }
}
