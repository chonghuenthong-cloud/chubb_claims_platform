package com.chubb.claims.claim;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request bodies for the staff and claimant actions on a claim.
 * Field checks are here; the coverage-limit ceiling needs the policy, so ClaimService checks it.
 * All free texts share one rule: 10 to 2000 characters.
 */
public final class ClaimActionRequests {

    private ClaimActionRequests() {
    }

    /** New reserve: 0 or more (the ceiling is the coverage limit). */
    public record ReviseReserveRequest(
            @NotNull(message = "Enter the reserve amount")
            @PositiveOrZero(message = "Reserve cannot be negative")
            @Digits(integer = 13, fraction = 2, message = "Reserve can have at most 2 decimal places")
            BigDecimal amount) {
    }

    /** Approved amount: more than 0 (the ceiling is the coverage limit). */
    public record ApproveRequest(
            @NotNull(message = "Enter the approved amount")
            @Positive(message = "Approved amount must be greater than 0")
            @Digits(integer = 13, fraction = 2, message = "Approved amount can have at most 2 decimal places")
            BigDecimal amount) {
    }

    public record RejectRequest(
            @NotBlank(message = "Give a reason")
            @Size(min = 10, max = 2000, message = "Reason must be 10 to 2000 characters")
            String reason) {
    }

    public record RequestInfoRequest(
            @NotBlank(message = "Enter the question")
            @Size(min = 10, max = 2000, message = "Question must be 10 to 2000 characters")
            String question) {
    }

    public record AnswerRequest(
            @NotBlank(message = "Enter your answer")
            @Size(min = 10, max = 2000, message = "Answer must be 10 to 2000 characters")
            String response) {
    }
}
