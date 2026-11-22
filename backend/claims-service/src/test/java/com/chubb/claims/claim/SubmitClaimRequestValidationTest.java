package com.chubb.claims.claim;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Field-level rules on the "Report an incident" request, checked without Spring or a database. */
class SubmitClaimRequestValidationTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** A request that passes every rule; each test changes one field. */
    private static SubmitClaimRequest valid() {
        return new SubmitClaimRequest(UUID.randomUUID(), LocalDate.now(), "Rear-ended at a junction", new BigDecimal("1500.00"));
    }

    /** Names of the fields that failed validation. */
    private Set<String> invalidFields(SubmitClaimRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void validRequestPasses() {
        assertThat(invalidFields(valid())).isEmpty();
    }

    @Test
    void allFieldsAreRequired() {
        assertThat(invalidFields(new SubmitClaimRequest(null, null, null, null)))
                .containsExactlyInAnyOrder("policyId", "incidentDate", "incidentDescription", "estimatedLoss");
    }

    @Test
    void incidentDateCannotBeInTheFuture() {
        var r = valid();
        assertThat(invalidFields(new SubmitClaimRequest(r.policyId(), LocalDate.now().plusDays(1),
                r.incidentDescription(), r.estimatedLoss()))).containsExactly("incidentDate");
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 2001})
    void descriptionOutside10To2000CharsFails(int length) {
        var r = valid();
        assertThat(invalidFields(new SubmitClaimRequest(r.policyId(), r.incidentDate(),
                "x".repeat(length), r.estimatedLoss()))).containsExactly("incidentDescription");
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 2000})
    void descriptionAtBoundariesPasses(int length) {
        var r = valid();
        assertThat(invalidFields(new SubmitClaimRequest(r.policyId(), r.incidentDate(),
                "x".repeat(length), r.estimatedLoss()))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "10.005"})
    void estimateMustBePositiveWithAtMostTwoDecimals(String amount) {
        var r = valid();
        assertThat(invalidFields(new SubmitClaimRequest(r.policyId(), r.incidentDate(),
                r.incidentDescription(), new BigDecimal(amount)))).containsExactly("estimatedLoss");
    }
}
