package com.chubb.claims.claim;

/**
 * Claim lifecycle. The allowed transitions live here, in one place:
 *
 * <pre>
 * SUBMITTED    -> UNDER_REVIEW
 * UNDER_REVIEW -> AWAITING_INFO | APPROVED | REJECTED
 * AWAITING_INFO -> UNDER_REVIEW
 * APPROVED     -> SETTLED
 * REJECTED, SETTLED: terminal
 * </pre>
 */
public enum ClaimStatus {
    SUBMITTED,
    UNDER_REVIEW,
    AWAITING_INFO,
    APPROVED,
    REJECTED,
    SETTLED;

    /** True if a claim in this status may move directly to {@code target}. */
    public boolean canTransitionTo(ClaimStatus target) {
        return switch (this) {
            case SUBMITTED -> target == UNDER_REVIEW;
            case UNDER_REVIEW -> target == AWAITING_INFO || target == APPROVED || target == REJECTED;
            case AWAITING_INFO -> target == UNDER_REVIEW;
            case APPROVED -> target == SETTLED;
            case REJECTED, SETTLED -> false;
        };
    }

    /** Open claims still carry liability exposure. */
    public boolean isOpen() {
        return this != REJECTED && this != SETTLED;
    }
}
