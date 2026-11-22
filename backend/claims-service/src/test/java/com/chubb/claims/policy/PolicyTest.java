package com.chubb.claims.policy;

import com.chubb.claims.user.AppUser;
import com.chubb.claims.user.Role;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The policy period check used when a claimant reports an incident. */
class PolicyTest {

    private final Policy policy = new Policy("SG-MOT-TEST",
            new AppUser("c@example.com", "Claimant", Role.CLAIMANT, "hash"),
            Market.SG, ProductType.MOTOR, new BigDecimal("10000.00"), "SGD",
            LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));

    @ParameterizedTest(name = "{0} covered={1}")
    @CsvSource({
            "2025-12-31, false",  // day before start
            "2026-01-01, true",   // start date is inclusive
            "2026-12-31, true",   // last covered day
            "2027-01-01, false",  // end date is exclusive
    })
    void coversDatesWithinThePolicyPeriod(LocalDate date, boolean covered) {
        assertThat(policy.covers(date)).isEqualTo(covered);
    }
}
