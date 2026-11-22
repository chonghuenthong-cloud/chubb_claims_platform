package com.chubb.claims.claim;

import com.chubb.claims.policy.Market;
import com.chubb.claims.policy.Policy;
import com.chubb.claims.policy.ProductType;
import com.chubb.claims.user.AppUser;
import com.chubb.claims.user.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests of the Claim entity's rules; no database needed. */
class ClaimTest {

    private final AppUser claimant = new AppUser("c@example.com", "Claimant", Role.CLAIMANT, "hash");
    private final AppUser officer = new AppUser("o@example.com", "Officer", Role.STAFF, "hash");
    private final Policy policy = new Policy("SG-MOT-TEST", claimant, Market.SG, ProductType.MOTOR,
            new BigDecimal("10000.00"), "SGD", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));

    private Claim submit(String estimatedLoss) {
        return Claim.submit("CLM-TEST", policy, claimant, LocalDate.of(2026, 6, 1),
                "Test incident", new BigDecimal(estimatedLoss));
    }

    @Test
    void newClaimStartsSubmittedWithHistory() {
        Claim claim = submit("4000.00");

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.getHistory()).singleElement().satisfies(h -> {
            assertThat(h.getFromStatus()).isNull();
            assertThat(h.getToStatus()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(h.getChangedBy()).isSameAs(claimant);
        });
    }

    @Test
    void reserveIsEstimateWhenWithinCoverage() {
        assertThat(submit("4000.00").getReserveAmount()).isEqualByComparingTo("4000.00");
    }

    @Test
    void reserveIsCappedAtCoverageLimit() {
        assertThat(submit("25000.00").getReserveAmount()).isEqualByComparingTo("10000.00");
    }

    @Test
    void validTransitionUpdatesStatusAndRecordsHistory() {
        Claim claim = submit("4000.00");

        claim.changeStatus(ClaimStatus.UNDER_REVIEW, officer, "Picked up");

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.getHistory()).hasSize(2).last().satisfies(h -> {
            assertThat(h.getFromStatus()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(h.getToStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
            assertThat(h.getChangedBy()).isSameAs(officer);
            assertThat(h.getNote()).isEqualTo("Picked up");
        });
    }

    @Test
    void invalidTransitionIsRejectedAndLeavesClaimUnchanged() {
        Claim claim = submit("4000.00");

        assertThatThrownBy(() -> claim.changeStatus(ClaimStatus.SETTLED, officer, null))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("SUBMITTED to SETTLED");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.getHistory()).hasSize(1);
    }
}
