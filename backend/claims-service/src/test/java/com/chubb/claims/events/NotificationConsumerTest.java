package com.chubb.claims.events;

import com.chubb.claims.claim.ClaimEventType;
import com.chubb.claims.claim.ClaimStatus;
import com.chubb.claims.policy.Market;
import com.chubb.claims.policy.ProductType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Which events notify the claimant, and what the message says. No Kafka or database needed. */
class NotificationConsumerTest {

    private final UUID claimant = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();

    private ClaimEvent event(ClaimEventType type, UUID actor, ClaimStatus from, ClaimStatus to, String note) {
        return new ClaimEvent(UUID.randomUUID(), type, Instant.now(), UUID.randomUUID(), "CLM-000042", claimant,
                actor, actor.equals(claimant) ? "Alice Tan" : "Priya Nair", from, to, note,
                "SGD", Market.SG, ProductType.MOTOR, new BigDecimal("4000"), new BigDecimal("3800"),
                to == ClaimStatus.APPROVED || to == ClaimStatus.SETTLED ? new BigDecimal("3800") : null);
    }

    private String message(ClaimEventType type, UUID actor, ClaimStatus from, ClaimStatus to, String note) {
        return NotificationConsumer.messageFor(event(type, actor, from, to, note));
    }

    @Test
    void staffStatusChangesNotifyTheClaimant() {
        assertThat(message(ClaimEventType.CLAIM_STATUS_CHANGED, officer, ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, null))
                .isEqualTo("Priya Nair picked up CLM-000042");
        assertThat(message(ClaimEventType.CLAIM_STATUS_CHANGED, officer, ClaimStatus.UNDER_REVIEW, ClaimStatus.AWAITING_INFO, "Send photos"))
                .isEqualTo("Question on CLM-000042: Send photos");
        assertThat(message(ClaimEventType.CLAIM_STATUS_CHANGED, officer, ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, null))
                .isEqualTo("CLM-000042 approved: SGD 3,800.00");
        assertThat(message(ClaimEventType.CLAIM_STATUS_CHANGED, officer, ClaimStatus.UNDER_REVIEW, ClaimStatus.REJECTED, "Not covered"))
                .isEqualTo("CLM-000042 rejected: Not covered");
        assertThat(message(ClaimEventType.CLAIM_STATUS_CHANGED, officer, ClaimStatus.APPROVED, ClaimStatus.SETTLED, null))
                .isEqualTo("CLM-000042 settled: SGD 3,800.00 paid");
    }

    @Test
    void claimantsOwnActionsDoNotNotify() {
        assertThat(message(ClaimEventType.CLAIM_SUBMITTED, claimant, null, ClaimStatus.SUBMITTED, null)).isNull();
        assertThat(message(ClaimEventType.CLAIM_STATUS_CHANGED, claimant, ClaimStatus.AWAITING_INFO, ClaimStatus.UNDER_REVIEW, null)).isNull();
    }

    @Test
    void reserveRevisionsAreInternalAndDoNotNotify() {
        assertThat(message(ClaimEventType.RESERVE_REVISED, officer, ClaimStatus.UNDER_REVIEW, ClaimStatus.UNDER_REVIEW, null)).isNull();
    }
}
