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

    // --- Staff and claimant actions ---

    private final AppUser otherOfficer = new AppUser("x@example.com", "Other", Role.STAFF, "hash");

    /** A claim picked up by `officer`, i.e. UNDER_REVIEW and assigned. */
    private Claim inReview() {
        Claim claim = submit("4000.00");
        claim.pickUp(officer);
        return claim;
    }

    @Test
    void pickUpAssignsTheOfficerAndStartsReview() {
        Claim claim = inReview();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.isAssignedTo(officer)).isTrue();
    }

    @Test
    void pickingUpAnAssignedClaimIsAConflict() {
        Claim claim = inReview();
        assertThatThrownBy(() -> claim.pickUp(otherOfficer))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Claim has already been picked up by Officer");
    }

    @Test
    void onlyTheAssignedOfficerCanAct() {
        Claim claim = inReview();
        assertThatThrownBy(() -> claim.approve(otherOfficer, new BigDecimal("100")))
                .isInstanceOf(NotAssignedOfficerException.class);
        assertThatThrownBy(() -> claim.reviseReserve(otherOfficer, new BigDecimal("100")))
                .isInstanceOf(NotAssignedOfficerException.class);
    }

    @Test
    void ownershipIsCheckedBeforeStatus() {
        Claim claim = submit("4000.00"); // SUBMITTED, nobody assigned
        assertThatThrownBy(() -> claim.approve(officer, new BigDecimal("100")))
                .isInstanceOf(NotAssignedOfficerException.class);
    }

    @Test
    void reserveCanBeRevisedWithinCoverageLimit() {
        Claim claim = inReview();
        claim.reviseReserve(officer, new BigDecimal("10000.00"));
        assertThat(claim.getReserveAmount()).isEqualByComparingTo("10000.00");
        assertThatThrownBy(() -> claim.reviseReserve(officer, new BigDecimal("10000.01")))
                .isInstanceOf(AmountAboveCoverageLimitException.class);
    }

    @Test
    void approveSetsApprovedAmountAndReserve() {
        Claim claim = inReview();
        claim.approve(officer, new BigDecimal("3500.00"));
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo("3500.00");
        assertThat(claim.getReserveAmount()).isEqualByComparingTo("3500.00");
    }

    @Test
    void approveAboveCoverageLimitLeavesClaimUnchanged() {
        Claim claim = inReview();
        assertThatThrownBy(() -> claim.approve(officer, new BigDecimal("10000.01")))
                .isInstanceOf(AmountAboveCoverageLimitException.class);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.getApprovedAmount()).isNull();
    }

    @Test
    void reserveCannotBeRevisedAfterApproval() {
        Claim claim = inReview();
        claim.approve(officer, new BigDecimal("3500.00"));
        assertThatThrownBy(() -> claim.reviseReserve(officer, new BigDecimal("100")))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void settleClearsTheReserve() {
        Claim claim = inReview();
        claim.approve(officer, new BigDecimal("3500.00"));
        claim.settle(officer);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
    }

    @Test
    void rejectRecordsReasonClearsReserveAndNotesHistory() {
        Claim claim = inReview();
        claim.reject(officer, "Not covered by this policy");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.getRejectionReason()).isEqualTo("Not covered by this policy");
        assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
        assertThat(claim.getHistory()).last().extracting(ClaimStatusHistory::getNote).isEqualTo("Not covered by this policy");
    }

    @Test
    void requestInfoThenAnswerReturnsToReview() {
        Claim claim = inReview();
        claim.requestInformation(officer, "Please send photos of the damage");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.AWAITING_INFO);
        assertThat(claim.getHistory()).last().extracting(ClaimStatusHistory::getNote).isEqualTo("Please send photos of the damage");

        claim.answerInformationRequest(claimant, "Photos sent by email this morning");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.getInformationRequests()).singleElement().satisfies(r -> {
            assertThat(r.isAnswered()).isTrue();
            assertThat(r.getResponse()).isEqualTo("Photos sent by email this morning");
        });
    }

    @Test
    void answeringWithoutAnOpenQuestionIsAConflict() {
        Claim claim = inReview();
        assertThatThrownBy(() -> claim.answerInformationRequest(claimant, "Nobody asked me"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }
}
