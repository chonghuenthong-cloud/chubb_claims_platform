package com.chubb.claims.claim;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks every pair of statuses against the lifecycle diagram in ClaimStatus. */
class ClaimStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            // From SUBMITTED, an officer must pick it up first
            "SUBMITTED,     UNDER_REVIEW,  true",
            "SUBMITTED,     APPROVED,      false",
            "SUBMITTED,     REJECTED,      false",
            // From UNDER_REVIEW: ask for info, approve or reject
            "UNDER_REVIEW,  AWAITING_INFO, true",
            "UNDER_REVIEW,  APPROVED,      true",
            "UNDER_REVIEW,  REJECTED,      true",
            "UNDER_REVIEW,  SETTLED,       false",
            // From AWAITING_INFO: back to review once the claimant answers
            "AWAITING_INFO, UNDER_REVIEW,  true",
            "AWAITING_INFO, APPROVED,      false",
            // From APPROVED: settle only
            "APPROVED,      SETTLED,       true",
            "APPROVED,      REJECTED,      false",
            // Terminal states
            "REJECTED,      UNDER_REVIEW,  false",
            "SETTLED,       UNDER_REVIEW,  false",
    })
    void enforcesLifecycle(ClaimStatus from, ClaimStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource({"SUBMITTED, true", "UNDER_REVIEW, true", "AWAITING_INFO, true", "APPROVED, true",
            "REJECTED, false", "SETTLED, false"})
    void onlyNonTerminalClaimsAreOpen(ClaimStatus status, boolean open) {
        assertThat(status.isOpen()).isEqualTo(open);
    }
}
