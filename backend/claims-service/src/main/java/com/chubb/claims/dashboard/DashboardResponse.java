package com.chubb.claims.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Everything the team dashboard shows, in one response.
 * All figures are all-time and calculated live from the claims tables on each request.
 */
public record DashboardResponse(
        UnassignedQueue unassigned,
        List<OfficerWorkload> workload,       // every staff member, idle ones included, by name
        List<CurrencyExposure> exposure,      // one entry per currency, by currency code
        List<OfficerPerformance> performance  // every staff member, by name
) {

    /** Claims waiting to be picked up. */
    public record UnassignedQueue(long count, Instant oldestSubmittedAt /* null when the queue is empty */) {
    }

    /** One officer's open claims. Approved claims count until they are settled (payment still to confirm). */
    public record OfficerWorkload(String officerName, long underReview, long awaitingInfo,
                                  long approvedNotSettled, long totalOpen) {
    }

    /**
     * Outstanding liability in one currency: the sum of reserves on open claims (no FX conversion, per B6).
     * Rejected and settled claims have a reserve of 0 (S10), so they add nothing.
     */
    public record CurrencyExposure(String currency, BigDecimal total,
                                   List<Breakdown> byMarket, List<Breakdown> byProduct) {
    }

    /** One slice of an exposure total, e.g. market "SG" or product "MOTOR". */
    public record Breakdown(String key, BigDecimal amount) {
    }

    /**
     * One officer's all-time results. Counts come from the status history (who made each change).
     * Average durations are in hours; null when there is nothing to average yet.
     */
    public record OfficerPerformance(String officerName, long approved, long rejected, long settled,
                                     Double avgSubmissionToPickUpHours,
                                     Double avgPickUpToDecisionHours,
                                     Double avgApprovalToSettlementHours) {
    }
}
