package com.chubb.claims.events;

import com.chubb.claims.claim.Claim;
import com.chubb.claims.claim.ClaimEventType;
import com.chubb.claims.policy.Market;
import com.chubb.claims.policy.Policy;
import com.chubb.claims.policy.ProductType;
import com.chubb.claims.user.AppUser;
import com.chubb.claims.user.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Building the published event from a claim. No Kafka or database needed. */
class ClaimEventTest {

    private final AppUser claimant = new AppUser("c@example.com", "Claimant", Role.CLAIMANT, "hash");
    private final Policy policy = new Policy("SG-MOT-TEST", claimant, Market.SG, ProductType.MOTOR,
            new BigDecimal("10000.00"), "SGD", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));

    @Test
    void amountsAlwaysHaveTwoDecimals() {
        // "1500" as typed in a request, no decimals
        Claim claim = Claim.submit("CLM-TEST", policy, claimant, LocalDate.of(2026, 6, 1), "Test incident",
                new BigDecimal("1500"));

        ClaimEvent event = ClaimEvent.of(claim, claim.pullPendingEvents().getFirst());

        assertThat(event.type()).isEqualTo(ClaimEventType.CLAIM_SUBMITTED);
        assertThat(event.reserveBefore()).hasToString("0.00");
        assertThat(event.reserveAfter()).hasToString("1500.00");
        assertThat(event.approvedAmount()).isNull();
        assertThat(event.currency()).isEqualTo("SGD");
        assertThat(event.claimNumber()).isEqualTo("CLM-TEST");
    }
}
