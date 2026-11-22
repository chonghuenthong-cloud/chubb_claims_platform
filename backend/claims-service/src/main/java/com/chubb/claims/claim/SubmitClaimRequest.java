package com.chubb.claims.claim;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What a claimant sends to report an incident.
 * Field-level checks are here; rules that need the database (own policy, policy period) are in ClaimService.
 */
public record SubmitClaimRequest(

        @NotNull(message = "Select a policy")
        UUID policyId,

        /* "Not in the future" is judged against the server's date. */
        @NotNull(message = "Enter the incident date")
        @PastOrPresent(message = "Incident date cannot be in the future")
        LocalDate incidentDate,

        @NotBlank(message = "Describe what happened")
        @Size(min = 10, max = 2000, message = "Description must be 10 to 2000 characters")
        String incidentDescription,

        /* Matches the NUMERIC(15, 2) column. Above the coverage limit is allowed; the reserve is capped instead. */
        @NotNull(message = "Enter the estimated loss")
        @Positive(message = "Estimated loss must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "Estimated loss can have at most 2 decimal places")
        BigDecimal estimatedLoss
) {
}
